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
