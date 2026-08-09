package com.wego.identity.application

sealed interface ThrottleDecision {
    data object Allowed : ThrottleDecision

    data class Rejected(
        val retryAfterSeconds: Long,
    ) : ThrottleDecision
}

/**
 * Keyed by the login identifier itself (an email, not an IP address) —
 * this is what closes the gap an IP-based edge limiter structurally can't:
 * an attacker spreading attempts across many source addresses is still
 * throttled here, because the key is the target account.
 *
 * A flat minimum-interval throttle only paces an attacker, it doesn't stop
 * one: someone patient enough to wait exactly that long between attempts
 * still eventually reaches the account's own failure-count lockout. Callers
 * must report each attempt's real outcome via [recordFailure]/[recordSuccess]
 * so an implementation can make each successive attempt against the same
 * key cost more than the last.
 */
interface LoginAttemptThrottle {
    fun tryAcquire(key: String): ThrottleDecision

    /** Called after an attempt for [key] is known to have failed. */
    fun recordFailure(key: String)

    /** Called after an attempt for [key] is known to have succeeded. */
    fun recordSuccess(key: String)
}
