# Security Model

## Trust boundaries

- Public clients and Nginx edge are untrusted input boundaries.
- The client API authenticates and authorizes every protected request.
- PostgreSQL, Redis, storage, and provider credentials are instance-scoped secrets.
- The Wego Control Plane is a separate administrative trust domain.
- AI providers and external communication/payment providers are untrusted processors with minimized inputs.

## Foundation posture

WEGO-000 permitted only health probes anonymously and denied other routes by default; it established wiring, not a complete authentication product, and created no bootstrap password or global operator credential.

WEGO-001 delivered the first real authenticated actor: email/password login issuing an opaque bearer session token (its SHA-256 hash, not the raw value, is what's persisted), server-side session/permission resolution enforced via `hasAuthority`, a basic failed-attempt lockout, and an append-only audit trail for login success/failure/logout/permission-denial. The very first user is created by an operator-run `bootstrap-admin` CLI profile that reads credentials from an interactive console only and refuses to run once any user exists — never a network endpoint, never a default credential. See `docs/operations/BACKEND_DEVELOPMENT.md` for the procedure.

## Target authentication architecture

Authentication supports secure password/key evolution, session and device records, revocation, credential rotation, rate limits, and optional TOTP/step-up. Browser sessions prefer secure, HttpOnly, SameSite cookies with CSRF protection. Mobile tokens are device-bound where practical and stored in platform-native secure storage.

WEGO-001 delivers a deliberately narrow slice of this: password evolution via Spring Security's delegating encoder, a basic count-based lockout, and audit records for the relevant events. **Still deferred to a later authorized packet:** OAuth/social login, MFA/TOTP/step-up, credential rotation, HttpOnly-cookie transport (sessions currently travel as a plain `Authorization: Bearer` header — correct for a stateless API with no cookie, but not the cookie/CSRF target shape above), mobile device-bound tokens, and a full RBAC administration surface (role/permission assignment is schema-only today, seeded by migration, with no admin UI or API).

## Authorization

Authorization evaluates subject, permission, resource scope, deployment context, and step-up/approval requirements. It is enforced at API and application use-case boundaries. RBAC assigns permission sets; scoped checks constrain instances such as branch, location, or owned record. Deny is the default.

## Sensitive actions

Cancellation, refund, price change, payroll, destructive action, and bulk communication require explicit permissions and may require step-up, approval, or human confirmation. Each decision and completed mutation produces an immutable audit record with actor, reason, target, correlation, time, and outcome.

## Support access

A Wego operator has no implicit client business access. Future support requires a client-instance grant with named operator, purpose, requested scope, start/expiry, approver, revocation, and audit trail. Emergency access is exceptional, short-lived, alerted, and reviewed.

## Data protection

PII is classified as public, internal, confidential, or restricted. Collection and logging are minimized. TLS protects transit. Volume/database encryption and backup protection are deployment responsibilities. Field encryption is introduced only for a classified threat with searchable/operational tradeoffs and documented key rotation.

## Secrets and abuse controls

Secrets arrive through deployment configuration and never through committed files, images, generated clients, logs, or client manifests. Edge and application rate limits protect authentication and expensive/sensitive commands. Redis may coordinate rate limits, but durable security state and audit remain in PostgreSQL.

### Login throttling and account lockout — the deliberate tradeoff

WEGO-001's login path layers two independent rate limiters — nginx, per source IP, and an application-level `LoginAttemptThrottle`, per target account — on top of a hard, count-based account lockout (5 consecutive failures, 15-minute lock). Combining a per-account throttle with a hard lockout creates an obvious tension: an attacker who cannot guess a password can instead try to *force* the lockout itself, deliberately denying the real account owner access. A throttle that only paces requests at a flat interval doesn't close that gap, it just adds a fixed delay before the same forced lockout — closing it completely would require either removing the hard lockout, or making the delay to force it long enough that doing so is no longer a cheap attack.

This project chose to keep the hard lockout — it remains real protection against sustained password guessing — and instead tune the throttle so that reaching the fifth (locking) failure, even for a caller pacing exactly at the throttle's own advertised `Retry-After` (the fastest a well-behaved-but-malicious client can go), takes measurably longer than the lockout itself lasts: `InMemoryLoginAttemptThrottle`'s exponential backoff (2 minutes, doubling per failure, capped at 15 minutes) puts the fifth attempt at roughly 29 minutes of elapsed real time, against the account's own 15-minute lock — forcing a lockout now costs an attacker more real time than the lockout itself imposes, not less. This is proven directly, not just asserted: `LoginServiceTest`'s `pacing exactly at the real throttle's own advertised retry-after ...` test wires the real throttle into a real `LoginService` against a real account, paces every attempt by the exact `retryAfterSeconds` the throttle returns, and asserts the elapsed time to a confirmed lock exceeds the lockout duration.

**This does not make forced lockout impossible — only expensive.** No finite backoff can guarantee that without eventually throttling legitimate retries just as hard; a sufficiently patient, automated attacker can still eventually force a lock. The residual risk accepted here is that determined, low-and-slow targeting of a *known* account remains possible over a timescale of tens of minutes per forced lock, rather than seconds. Detecting and alerting on that pattern (repeated `ACCOUNT_LOCKED_OR_DISABLED`/`WRONG_PASSWORD` audit events against one account) is not yet built and is deferred, same as the rest of the "optional TOTP/step-up" surface named above — a step-up challenge or account-owner notification on repeated lockouts is the natural next mitigation, not a longer backoff number.

### Throttle memory bounding — sized against what's achievable, not against frequency

The throttle keys on the raw *submitted* email, not a verified account — so an attacker can spray unlimited distinct keys to grow `InMemoryLoginAttemptThrottle`'s internal state without bound, or worse, force whatever bounding logic exists to run against every request. An earlier version's "sweep entries older than X" cleanup wasn't an actual bound: freshly-sprayed keys are never "stale" by that definition, so the map kept growing past its nominal limit, and every request past that limit paid for a full O(n) scan of the whole map — a self-inflicted CPU-exhaustion vector layered on top of the spray itself.

This was replaced with a [Caffeine](https://github.com/ben-manes/caffeine)-backed cache with a hard `maximumSize` and amortized O(1) eviction — no more unbounded growth and no more full-table scans, verified with a test spraying four times the cap and confirming the tracked-key count stays bounded. Caffeine is often described as scan-resistant (frequency-aware eviction, not plain LRU), which would suggest a real, repeatedly-hit target account is automatically protected from being evicted by a burst of cold spray keys — **that claim was checked empirically for this class's specific access pattern before relying on it, and did not hold**: because every access here also writes (`nextAllowedAt`/`consecutiveFailures` genuinely change on nearly every call), a key with thousands of prior real accesses was evicted right alongside one-off spray keys once total spray volume reached the cap, in testing. The cap is sized instead against what a single attacker can actually *achieve*: nginx's own edge-level, per-IP limiter sits in front of every one of these requests too, capping a single source to roughly 5 requests/minute — across the ~29-minute window that matters (above), that's on the order of 150 requests, several hundred times below the 50,000 cap. A single source cannot realistically generate spray volume anywhere near the cap within a target's active window — proven directly by a test at that realistic volume alongside a second test that deliberately sprays enough to reach the cap and confirms eviction *does* happen there, making the boundary visible rather than an untested assumption in either direction. A *distributed*, many-source-IP spray at volume comparable to the cap remains a real, accepted residual risk — a materially different, higher-cost attack class than a single actor, requiring coordinated infrastructure rather than one attacker with a script, consistent with this component's documented single-instance scope and the same Redis-backed-horizontal-coordination answer named above for scaling beyond one instance.

## Observability

Security events are structured and correlated without logging credentials, tokens, full request bodies, or unnecessary PII. Authentication failure, authorization denial, step-up, grant use, suspicious rate-limit activity, and secret/configuration failure are observable.
