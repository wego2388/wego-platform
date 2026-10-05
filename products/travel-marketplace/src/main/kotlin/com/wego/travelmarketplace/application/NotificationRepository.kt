package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.NotificationId
import com.wego.travelmarketplace.domain.NotificationKind
import com.wego.travelmarketplace.domain.NotificationStatus
import com.wego.travelmarketplace.domain.RequestNotification
import com.wego.travelmarketplace.domain.TravelRequestId
import java.time.Instant

/** A notification as listed to staff: no recipient address, only the request reference. */
data class NotificationListItem(
    val notification: RequestNotification,
    val requestReference: String,
)

interface NotificationRepository {
    /**
     * Records the intent unless one already exists for this request and kind.
     * Must run inside the transaction of the request transition that causes
     * it. Returns false when it already existed (a replayed transition).
     */
    fun enqueueOnce(
        requestId: TravelRequestId,
        kind: NotificationKind,
        availableAt: Instant,
        now: Instant,
    ): Boolean

    fun exists(
        requestId: TravelRequestId,
        kind: NotificationKind,
    ): Boolean

    /** Locks and returns the oldest due PENDING row, skipping rows another dispatcher holds. */
    fun claimNextDue(now: Instant): RequestNotification?

    fun findByIdForUpdate(id: NotificationId): RequestNotification?

    fun list(
        status: NotificationStatus?,
        limit: Int,
        offset: Int,
    ): List<NotificationListItem>

    fun save(notification: RequestNotification)

    /**
     * Saves a dispatch outcome only if the row is still in the state this
     * dispatcher claimed: PENDING with [expectedAttemptCount]. Returns false
     * when a staff resend or a later lease changed it meanwhile, so a late
     * outcome never overwrites newer state.
     */
    fun recordOutcome(
        notification: RequestNotification,
        expectedAttemptCount: Int,
    ): Boolean
}
