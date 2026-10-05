package com.wego.identity.application

sealed interface ThrottleDecision {
    data object Allowed : ThrottleDecision

    data class Rejected(
        val retryAfterSeconds: Long,
    ) : ThrottleDecision
}

/**
 * Keyed by the login identifier itself (an email, not an IP address). A
 * rejected decision is a hard gate before account lookup and password
 * verification for every candidate, so callers cannot use the endpoint as a
 * password oracle during the retry window. The edge limiter separately bounds
 * work from one source address. Legitimate recovery during a window uses the
 * advertised retry delay, password reset, or an authenticated admin path.
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
