package com.wego.toursoperator.application

import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.CustomerNotification
import com.wego.toursoperator.domain.NotificationId
import com.wego.toursoperator.domain.NotificationKind
import com.wego.toursoperator.domain.NotificationStatus
import java.time.Instant

/** A notification as listed to staff: no recipient address, only the booking reference. */
data class NotificationListItem(
    val notification: CustomerNotification,
    val bookingReference: String,
)

/**
 * Delivery facts of one confirmation notification. [resendCount] survives a
 * resend's requeue and a later skip, so it proves the customer was once sent it.
 */
data class ConfirmationFact(
    val status: NotificationStatus,
    val attemptCount: Int,
    val resendCount: Int,
) {
    /** Possibly seen by the customer: sent, resent, or ambiguously failed after an attempt. */
    val possiblyAnnounced: Boolean
        get() = status == NotificationStatus.SENT || resendCount > 0 || (status == NotificationStatus.FAILED && attemptCount > 0)
}

interface NotificationRepository {
    /**
     * Records the intent unless one already exists for this booking and kind.
     * Must run inside the transaction of the booking transition that causes it.
     * Returns false when it already existed (a replayed transition).
     */
    fun enqueueOnce(
        bookingId: BookingId,
        kind: NotificationKind,
        availableAt: Instant,
        now: Instant,
    ): Boolean

    /**
     * Delivery facts of the booking's confirmation notifications (BOOKING_CONFIRMED and
     * OFFICE_REQUEST_CONFIRMED). Empty when none was ever queued.
     */
    fun confirmationFacts(bookingId: BookingId): List<ConfirmationFact>

    /** Locks and returns the oldest due PENDING row, skipping rows another dispatcher holds. */
    fun claimNextDue(now: Instant): CustomerNotification?

    fun findByIdForUpdate(id: NotificationId): CustomerNotification?

    fun list(
        status: NotificationStatus?,
        limit: Int,
        offset: Int,
    ): List<NotificationListItem>

    fun save(notification: CustomerNotification)
}
