package com.wego.toursoperator.domain

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
    /** The booking status in which this message is still true. */
    val requiredBookingStatus: BookingStatus,
) {
    BOOKING_CONFIRMED(BookingStatus.CONFIRMED),
    BOOKING_CANCELLED(BookingStatus.CANCELLED),
    REVIEW_REQUEST(BookingStatus.COMPLETED),
}

enum class NotificationStatus {
    PENDING,
    SENT,
    FAILED,
    SKIPPED,
}

/**
 * One customer notification intent for a booking. At most one exists per
 * booking and kind; delivery state changes only through the methods below.
 *
 * lastError is always a short, PII-free reason (an error class or a skip
 * code) — never an address or a message body.
 */
class CustomerNotification(
    val id: NotificationId,
    val bookingId: BookingId,
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

    fun markSent(now: Instant) {
        requirePending()
        attemptCount += 1
        status = NotificationStatus.SENT
        sentAt = now
        lastError = null
    }

    /** Nothing to send (e.g. no customer email); visible to staff, never retried automatically. */
    fun markSkipped(reason: String) {
        requirePending()
        status = NotificationStatus.SKIPPED
        lastError = reason.take(MAX_ERROR_LENGTH)
    }

    /**
     * A delivery attempt failed. Retries with exponential backoff until
     * [maxAttempts], then stays FAILED for a staff resend.
     */
    fun markAttemptFailed(
        reason: String,
        now: Instant,
        maxAttempts: Int,
    ) {
        requirePending()
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        attemptCount += 1
        lastError = reason.take(MAX_ERROR_LENGTH)
        if (attemptCount >= maxAttempts) {
            status = NotificationStatus.FAILED
        } else {
            availableAt = now.plus(backoffAfter(attemptCount))
        }
    }

    /**
     * Staff resend: queue again now, including after SENT (customer lost the
     * email). The delivery counters restart; the resend itself is recorded.
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

        /** 1, 2, 4, 8 … minutes, capped at one hour. */
        fun backoffAfter(attempts: Int): Duration {
            val minutes = 1L shl (attempts - 1).coerceIn(0, 6)
            return Duration.ofMinutes(minutes.coerceAtMost(60))
        }

        fun queue(
            bookingId: BookingId,
            kind: NotificationKind,
            availableAt: Instant,
            now: Instant,
        ): CustomerNotification =
            CustomerNotification(
                id = NotificationId.generate(),
                bookingId = bookingId,
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
