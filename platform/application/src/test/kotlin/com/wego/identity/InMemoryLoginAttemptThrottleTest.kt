package com.wego.identity

import com.wego.identity.application.ThrottleDecision
import com.wego.identity.infrastructure.InMemoryLoginAttemptThrottle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class InMemoryLoginAttemptThrottleTest {
    private val clock = MutableClock(Instant.parse("2026-08-09T00:00:00Z"))
    private val throttle =
        InMemoryLoginAttemptThrottle(
            clock = clock,
            baseInterval = Duration.ofSeconds(3),
            maxInterval = Duration.ofMinutes(15),
        )

    @Test
    fun `a fresh key is allowed immediately`() {
        assertThat(throttle.tryAcquire("a@example.com")).isEqualTo(ThrottleDecision.Allowed)
    }

    @Test
    fun `an immediate second attempt against the same key is rejected`() {
        throttle.tryAcquire("a@example.com")

        assertThat(throttle.tryAcquire("a@example.com")).isEqualTo(ThrottleDecision.Rejected(3))
    }

    @Test
    fun `different keys do not interfere with each other`() {
        assertThat(throttle.tryAcquire("a@example.com")).isEqualTo(ThrottleDecision.Allowed)
        assertThat(throttle.tryAcquire("b@example.com")).isEqualTo(ThrottleDecision.Allowed)
    }

    @Test
    fun `a rejected attempt does not extend the window, so a sustained flood cannot lock a legitimate caller out forever`() {
        throttle.tryAcquire("a@example.com") // reserves the window through +3s

        // A flood of rejected attempts, each a little later than the last. If a
        // rejection refreshed the window (the bug this replaces), the key would
        // never become available again under sustained pressure.
        repeat(20) {
            clock.advance(Duration.ofMillis(100))
            assertThat(throttle.tryAcquire("a@example.com")).isInstanceOf(ThrottleDecision.Rejected::class.java)
        }

        // 20 * 100ms = 2s elapsed since the reservation; advance to exactly the
        // original 3s window and confirm it opens on schedule, not later.
        clock.advance(Duration.ofSeconds(1))
        assertThat(throttle.tryAcquire("a@example.com")).isEqualTo(ThrottleDecision.Allowed)
    }

    @Test
    fun `each recorded failure doubles the wait required before the next attempt`() {
        val key = "a@example.com"

        assertThat(throttle.tryAcquire(key)).isEqualTo(ThrottleDecision.Allowed) // 0 prior failures -> reserves 3s
        throttle.recordFailure(key) // 1 failure recorded

        clock.advance(Duration.ofSeconds(3))
        assertThat(throttle.tryAcquire(key)).isEqualTo(ThrottleDecision.Allowed) // 1 prior failure -> reserves 6s
        throttle.recordFailure(key) // 2 failures recorded

        // Immediately after: the window now requires ~6s, not the original 3s —
        // an attacker pacing at exactly the old interval no longer gets through.
        assertThat(throttle.tryAcquire(key)).isEqualTo(ThrottleDecision.Rejected(6))

        clock.advance(Duration.ofSeconds(6))
        assertThat(throttle.tryAcquire(key)).isEqualTo(ThrottleDecision.Allowed) // 2 prior failures -> reserves 12s
        throttle.recordFailure(key) // 3 failures recorded

        assertThat(throttle.tryAcquire(key)).isEqualTo(ThrottleDecision.Rejected(12))
    }

    @Test
    fun `backoff is capped at maxInterval no matter how many failures accumulate`() {
        val key = "a@example.com"
        assertThat(throttle.tryAcquire(key)).isEqualTo(ThrottleDecision.Allowed)
        repeat(20) { throttle.recordFailure(key) } // far past what the cap allows

        clock.advance(Duration.ofSeconds(3))
        assertThat(throttle.tryAcquire(key)).isEqualTo(ThrottleDecision.Allowed) // reserves min(2^20 * 3s, 15m) = 15m

        assertThat(throttle.tryAcquire(key)).isEqualTo(ThrottleDecision.Rejected(15 * 60L))
    }

    @Test
    fun `a recorded success resets the backoff back to the base interval`() {
        val key = "a@example.com"
        assertThat(throttle.tryAcquire(key)).isEqualTo(ThrottleDecision.Allowed)
        throttle.recordFailure(key)

        clock.advance(Duration.ofSeconds(3))
        assertThat(throttle.tryAcquire(key)).isEqualTo(ThrottleDecision.Allowed) // 1 prior failure -> reserves 6s
        throttle.recordSuccess(key)

        clock.advance(Duration.ofSeconds(6))
        assertThat(throttle.tryAcquire(key)).isEqualTo(ThrottleDecision.Allowed) // reset -> reserves base 3s again, not still escalated
        assertThat(throttle.tryAcquire(key)).isEqualTo(ThrottleDecision.Rejected(3))
    }

    @Test
    fun `spraying far more distinct keys than the size cap does not grow the map without bound`() {
        // Four times the 50,000 cap — a plain sweep-old-entries map (what
        // this replaced) would keep every one of these, since none of them
        // are ever "stale" by the time the next one arrives.
        repeat(200_000) { i -> throttle.tryAcquire("spray-$i@example.com") }

        // MAX_TRACKED_KEYS is a hard cap, not a rough target — after
        // cleanUp() (called inside estimatedTrackedKeyCount()) forces
        // pending eviction maintenance to run, the real enforced size must
        // respect it exactly, not just "roughly bounded."
        assertThat(throttle.estimatedTrackedKeyCount()).isLessThanOrEqualTo(50_000L)
    }

    @Test
    fun `a target's throttle state survives a spray volume realistic for one IP throttled by nginx's own edge limiter`() {
        val hotKey = "hot-target@example.com"

        repeat(10) {
            throttle.tryAcquire(hotKey)
            clock.advance(Duration.ofSeconds(5))
        }
        val decisionBeforeSpray = throttle.tryAcquire(hotKey) // Allowed; reserves a fresh window from "now"
        assertThat(decisionBeforeSpray).isEqualTo(ThrottleDecision.Allowed)

        // nginx's edge limiter (5r/m, burst 3) caps a single source IP to
        // roughly this many requests across the throttle's own ~29-minute
        // time-to-lock window (see InMemoryLoginAttemptThrottle's class doc
        // comment) — this is what a single-source spray can *actually*
        // achieve against a live target, not an unbounded number.
        repeat(150) { i -> throttle.tryAcquire("spray-$i@example.com") }

        // No time has advanced since the reservation above: if the hot
        // key's state survived, it's still inside that window and must be
        // rejected.
        assertThat(throttle.tryAcquire(hotKey)).isInstanceOf(ThrottleDecision.Rejected::class.java)
    }

    @Test
    fun `a spray reaching the size cap itself can evict a target's throttle state - accepted, documented residual risk`() {
        // This is not a property to defend — it's the honest boundary of
        // what a single bounded in-process cache can guarantee, made
        // visible as a test rather than left as an untested assumption. A
        // spray at this volume needs roughly 50,000 requests within the
        // target's active window; a single source is nowhere near capable
        // of that against nginx's own edge limiter (previous test) — this
        // scale requires a distributed, many-source-IP spray, a materially
        // different and higher-cost attack class documented in
        // docs/architecture/SECURITY_MODEL.md and InMemoryLoginAttemptThrottle's
        // own class doc comment.
        val hotKey = "hot-target@example.com"

        repeat(10) {
            throttle.tryAcquire(hotKey)
            clock.advance(Duration.ofSeconds(5))
        }
        throttle.tryAcquire(hotKey) // reserves a fresh window from "now"

        repeat(60_000) { i -> throttle.tryAcquire("spray-$i@example.com") }

        // A key's absence from an eviction-based cache can't be observed
        // directly; what's observable is the *effect* of eviction — the
        // reservation above no longer holds, so a fresh attempt is Allowed
        // again instead of Rejected.
        assertThat(throttle.tryAcquire(hotKey)).isEqualTo(ThrottleDecision.Allowed)
    }
}
