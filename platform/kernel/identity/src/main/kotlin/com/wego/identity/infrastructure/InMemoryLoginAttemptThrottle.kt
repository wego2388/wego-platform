package com.wego.identity.infrastructure

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.wego.identity.application.LoginAttemptThrottle
import com.wego.identity.application.ThrottleDecision
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * In-memory and per-process. This is deliberately single-instance-only:
 * nothing here is shared across replicas. If the application is ever
 * horizontally scaled, this stops meaningfully protecting anything beyond
 * whichever instance happens to receive a given request, and needs a shared
 * store instead — SECURITY_MODEL.md already names Redis as the intended
 * coordination point for exactly this. Documented as a known limitation of
 * this foundation packet's single-VPS deployment topology, not silently
 * assumed away.
 *
 * Pacing alone (a flat "one attempt every N seconds") doesn't stop an
 * account being locked, only slows it down predictably — an attacker who
 * simply waits N seconds between attempts still reaches the account's
 * failure-count lockout eventually, just on a fixed schedule. This
 * escalates the required wait exponentially with each recorded failure
 * (base, base*2, base*4, ...), capped at [maxInterval], and resets to the
 * base interval on [recordSuccess]. A rejected [tryAcquire] call leaves the
 * key's state untouched — it must not, or a sustained flood of rejected
 * requests could keep refreshing the window and lock a legitimate caller
 * out of the same account indefinitely, which is exactly the failure mode
 * a pure "last attempt timestamp" design has.
 *
 * [baseInterval]'s default (2 minutes) is not an arbitrary "make it
 * slower" number — it's chosen so that reaching [LoginService]'s default
 * 5-failure lockout, even for a caller pacing exactly at the throttle's own
 * advertised `Retry-After` (the fastest a well-behaved-but-malicious client
 * can go), takes longer than the account's own 15-minute lockout duration:
 * with attempts at t=0, +2m, +6m, +14m, the fifth (locking) attempt lands
 * at t=+29m — roughly double the lockout window, not comparable to or
 * shorter than it. This is a deliberate, bounded design choice, not a
 * claim that forced lockout is impossible: a sufficiently patient attacker
 * can still eventually force it (see [LoginService]'s own docs and
 * `docs/architecture/SECURITY_MODEL.md` for the residual risk this
 * accepts) — the goal is removing the *cheap* version of that attack
 * (forcing a 15-minute lockout in under a minute), not chasing a
 * mathematically impossible one, since no finite backoff can guarantee
 * that without eventually blocking legitimate retries just as hard.
 *
 * The key is the raw *submitted* email, not a verified account identifier
 * (there is no account to verify against until [LoginService] looks one
 * up) — so an attacker can trivially generate unlimited distinct keys by
 * spraying unique addresses. A plain `ConcurrentHashMap` with a periodic
 * "sweep entries older than X" pass, which is what this class used to do,
 * is not an actual bound against that: a spray of fresh, never-repeated
 * keys are never "stale" by the sweep's own definition, so the map keeps
 * growing past any nominal limit, and every request after that limit paid
 * for a full O(n) scan of the whole map — a self-inflicted CPU-exhaustion
 * vector layered on top of the very attack the limit was meant to stop.
 * [Caffeine](https://github.com/ben-manes/caffeine) replaces that with a
 * genuinely bounded cache: `maximumSize` is a hard cap enforced with
 * amortized O(1) eviction (no foreground full-table scan), which is what
 * actually matters here.
 *
 * Caffeine markets its default policy as scan-resistant (frequency-aware,
 * not plain LRU) — but that was verified empirically for this class's own
 * access pattern before relying on it, not assumed from the marketing, and
 * the result doesn't support leaning on it: every access here also writes
 * (`nextAllowedAt`/`consecutiveFailures` genuinely change), and once a
 * spray's total distinct-key volume reaches [MAX_TRACKED_KEYS], a real
 * key with thousands of prior real accesses gets evicted right alongside
 * the one-off spray keys — frequency did not protect it in testing. So
 * [MAX_TRACKED_KEYS] is instead sized generously against what's actually
 * *achievable*: nginx's own edge-level, per-IP limiter
 * (`infrastructure/nginx/nginx.conf`) sits in front of every one of these
 * requests too, capping any single source to roughly 5 requests/minute —
 * across the ~29-minute window `baseInterval`'s own doc comment above
 * proves matters, that's on the order of 150 requests from one IP, several
 * hundred times below this cap. A single source cannot realistically
 * generate spray volume anywhere near [MAX_TRACKED_KEYS] within a target's
 * active window. A *distributed*, many-source-IP spray at volume
 * comparable to the cap remains a real, accepted residual risk — see
 * `docs/architecture/SECURITY_MODEL.md` — but that is a materially
 * different, higher-cost attack class (coordinated infrastructure, not a
 * single attacker), consistent with this class's own single-instance
 * scope: the same category of threat Redis-backed horizontal coordination
 * is already named to eventually address, not something an in-process
 * cache's eviction policy alone was ever going to solve.
 */
@Component
class InMemoryLoginAttemptThrottle(
    private val clock: Clock,
    private val baseInterval: Duration = Duration.ofMinutes(2),
    private val maxInterval: Duration = Duration.ofMinutes(15),
) : LoginAttemptThrottle {
    // TTL is deliberately larger than maxInterval, not equal to it: the
    // longest legitimate gap between two touches of the *same* still-escalating
    // key is exactly maxInterval (the backoff cap itself), so an equal TTL
    // risks expiring a key's escalation state right as its next attempt is
    // due, silently resetting the schedule and undermining the time-to-lock
    // guarantee this class exists to provide. Doubling leaves a clear
    // margin with no realistic downside — an idle key sitting unused for
    // half an hour is exactly the case eviction should reclaim.
    //
    // This is wall-clock time (Caffeine's own internal ticker), independent
    // of the injected [clock] — the two agree in production (both track
    // real elapsed time) and tests never run long enough in real wall-clock
    // time for this to matter, since they drive [nextAllowedAt] scheduling
    // through [clock] directly rather than relying on cache expiry.
    private val states: Cache<String, ThrottleState> =
        Caffeine
            .newBuilder()
            .maximumSize(MAX_TRACKED_KEYS)
            .expireAfterWrite(maxInterval.multipliedBy(2))
            .build()

    override fun tryAcquire(key: String): ThrottleDecision {
        val now = Instant.now(clock)
        var decision: ThrottleDecision = ThrottleDecision.Allowed

        states.asMap().compute(key) { _, existing ->
            val current = existing ?: ThrottleState(consecutiveFailures = 0, nextAllowedAt = Instant.MIN)
            if (now.isBefore(current.nextAllowedAt)) {
                // Ceiling, not floor: .toMillis() would truncate any
                // sub-second remainder away, which would under-report a
                // wait like 2.9s as "2" and let a client's own
                // Retry-After-driven retry land before the window actually
                // opens, only to be rejected again.
                val remainingMillis = Duration.between(now, current.nextAllowedAt).toMillis()
                val waitSeconds = ((remainingMillis + 999) / 1000).coerceAtLeast(1)
                decision = ThrottleDecision.Rejected(waitSeconds)
                current
            } else {
                decision = ThrottleDecision.Allowed
                current.copy(nextAllowedAt = now.plus(backoffFor(current.consecutiveFailures)))
            }
        }
        return decision
    }

    override fun recordFailure(key: String) {
        states.asMap().compute(key) { _, existing ->
            val current = existing ?: ThrottleState(consecutiveFailures = 0, nextAllowedAt = Instant.MIN)
            current.copy(consecutiveFailures = current.consecutiveFailures + 1)
        }
    }

    override fun recordSuccess(key: String) {
        states.asMap().remove(key)
    }

    /**
     * Test-only observability into the cache's real, enforced size —
     * [Cache.estimatedSize] reflects buffered-but-not-yet-applied writes
     * without first running pending maintenance, so this forces it via
     * [Cache.cleanUp] rather than reporting a number that could look
     * unbounded simply because eviction hadn't been applied yet.
     */
    internal fun estimatedTrackedKeyCount(): Long {
        states.cleanUp()
        return states.estimatedSize()
    }

    private fun backoffFor(consecutiveFailures: Int): Duration {
        val multiplier = 1L shl consecutiveFailures.coerceIn(0, MAX_BACKOFF_SHIFT)
        val scaled = baseInterval.multipliedBy(multiplier)
        return if (scaled > maxInterval) maxInterval else scaled
    }

    private data class ThrottleState(
        val consecutiveFailures: Int,
        val nextAllowedAt: Instant,
    )

    companion object {
        // ~150 requests is roughly what a single IP can push through nginx's
        // own edge limiter (infrastructure/nginx/nginx.conf) within the
        // ~29-minute window a target's throttle schedule needs to survive —
        // this cap sits several hundred times above that, so no single
        // source can realistically approach it. See the class doc comment
        // above for the full reasoning and the distributed-spray risk this
        // still doesn't (and can't, from a single in-process cache) close.
        private const val MAX_TRACKED_KEYS = 50_000L

        // 2^20 * a 2-minute base interval is comfortably beyond maxInterval's
        // 15-minute cap — this only exists to keep the shift itself (and the
        // Duration multiplication) from ever overflowing at an implausibly
        // large failure count, not to change real behavior.
        private const val MAX_BACKOFF_SHIFT = 20
    }
}
