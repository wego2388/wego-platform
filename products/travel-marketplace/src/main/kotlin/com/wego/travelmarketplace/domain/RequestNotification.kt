package com.wego.travelmarketplace.domain

import java.time.Duration
import java.time.Instant
import java.util.UUID

@JvmInline
value class NotificationId(
    val value: UUID,
) {
    companion object {
        fun generate(): NotificationId = NotificationId(UUID.randomUUID())
    }
}

enum class NotificationKind(
    val isCustomerFacing: Boolean,
) {
    /** Alert to the configured staff address that a request arrived. */
    STAFF_NEW_REQUEST(false),

    /** Receipt to the customer; wording depends on whether the request is already confirmed. */
    CUSTOMER_REQUEST_RECEIVED(true),
    CUSTOMER_REQUEST_CONFIRMED(true),
    CUSTOMER_REQUEST_CANCELLED(true),
    ;

    /** Whether this message is still true for a request now in [status]. */
    fun isStillTrueFor(status: TravelRequestStatus): Boolean =
        when (this) {
            STAFF_NEW_REQUEST, CUSTOMER_REQUEST_RECEIVED ->
                status == TravelRequestStatus.NEW ||
                    status == TravelRequestStatus.IN_REVIEW ||
                    status == TravelRequestStatus.CONFIRMED
            CUSTOMER_REQUEST_CONFIRMED ->
                status == TravelRequestStatus.CONFIRMED || status == TravelRequestStatus.COMPLETED
            CUSTOMER_REQUEST_CANCELLED -> status == TravelRequestStatus.CANCELLED
        }
}

enum class NotificationStatus {
    PENDING,
    SENT,
    FAILED,
    SKIPPED,
}

/**
 * One notification intent for a request. At most one exists per request and
 * kind; delivery state changes only through the methods below.
 *
 * [lastError] is always a short token (an error class or a skip code) —
 * never an address or a message body; the V8 migration enforces that shape.
 */
class RequestNotification(
    val id: NotificationId,
    val requestId: TravelRequestId,
    val kind: NotificationKind,
    status: NotificationStatus,
    attemptCount: Int,
    availableAt: Instant,
    val createdAt: Instant,
    sentAt: Instant?,
    lastError: String?,
    resendCount: Int = 0,
    lastResentByUserId: UUID? = null,
    lastResentAt: Instant? = null,
) {
    var status: NotificationStatus = status
        private set

    var attemptCount: Int = attemptCount
        private set

    var availableAt: Instant = availableAt
        private set

    var sentAt: Instant? = sentAt
        private set

    var lastError: String? = lastError
        private set

    var resendCount: Int = resendCount
        private set

    var lastResentByUserId: UUID? = lastResentByUserId
        private set

    var lastResentAt: Instant? = lastResentAt
        private set

    init {
        require(attemptCount >= 0) { "attemptCount must not be negative" }
        require((status == NotificationStatus.SENT) == (sentAt != null)) {
            "sentAt must be set if and only if status is SENT (status: $status)"
        }
    }

    /**
     * Dispatcher lease: counts the attempt and hides the row until [leaseUntil]
     * BEFORE anything is sent, and the claim is committed before the send. If
     * the process dies, or the outcome cannot be saved, after the provider
     * accepted the message, the attempt is already on record, so duplicates
     * are bounded by the maximum attempts.
     */
    fun claim(leaseUntil: Instant) {
        requirePending()
        attemptCount += 1
        availableAt = leaseUntil
    }

    /** The attempt budget is spent (an attempt that never recorded its outcome was the last one). */
    fun markExhausted() {
        requirePending()
        status = NotificationStatus.FAILED
        lastError = "attempts_exhausted"
    }

    fun markSent(now: Instant) {
        requirePending()
        status = NotificationStatus.SENT
        sentAt = now
        lastError = null
    }

    /** Nothing to send (e.g. no customer email); visible to staff, never retried automatically. */
    fun markSkipped(reason: String) {
        requirePending()
        status = NotificationStatus.SKIPPED
        lastError = toReasonCode(reason)
    }

    /**
     * The claimed attempt failed. Retries with exponential backoff until
     * [maxAttempts], then stays FAILED for a staff resend. The attempt itself
     * was already counted by [claim].
     */
    fun markAttemptFailed(
        reason: String,
        now: Instant,
        maxAttempts: Int,
    ) {
        requirePending()
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        lastError = toReasonCode(reason)
        if (attemptCount >= maxAttempts) {
            status = NotificationStatus.FAILED
        } else {
            availableAt = now.plus(backoffAfter(attemptCount))
        }
    }

    /**
     * Staff resend: queue again now, including after SENT (the customer lost
     * the email). Delivery counters restart; the resend itself is recorded.
     */
    fun requeue(
        now: Instant,
        actorUserId: UUID?,
    ) {
        resendCount += 1
        lastResentByUserId = actorUserId
        lastResentAt = now
        status = NotificationStatus.PENDING
        attemptCount = 0
        availableAt = now
        sentAt = null
        lastError = null
    }

    private fun requirePending() {
        require(status == NotificationStatus.PENDING) { "Only a PENDING notification can be delivered (status: $status)" }
    }

    companion object {
        const val MAX_ERROR_LENGTH = 200

        /** 1, 2, 4, 8 ... minutes, capped at one hour. */
        fun backoffAfter(attempts: Int): Duration {
            val minutes = 1L shl (attempts - 1).coerceIn(0, 6)
            return Duration.ofMinutes(minutes.coerceAtMost(60))
        }

        /** Reduces any text to a bare token so an address or message fragment can never be stored. */
        fun toReasonCode(reason: String): String = reason.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(MAX_ERROR_LENGTH)

        fun queue(
            requestId: TravelRequestId,
            kind: NotificationKind,
            availableAt: Instant,
            now: Instant,
        ): RequestNotification =
            RequestNotification(
                id = NotificationId.generate(),
                requestId = requestId,
                kind = kind,
                status = NotificationStatus.PENDING,
                attemptCount = 0,
                availableAt = availableAt,
                createdAt = now,
                sentAt = null,
                lastError = null,
            )
    }
}
