package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.NotificationId
import com.wego.travelmarketplace.domain.NotificationKind
import com.wego.travelmarketplace.domain.TravelRequestId
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed class ResendNotificationResult {
    data object Requeued : ResendNotificationResult()

    data object NotFound : ResendNotificationResult()

    /** The message is no longer true (e.g. a confirmation for a cancelled request). */
    data object RequestStateChanged : ResendNotificationResult()
}

/** Staff action: queue a notification again (after FAILED, SKIPPED, or a lost SENT email). */
class ResendNotificationService(
    private val notificationRepository: NotificationRepository,
    private val requestRepository: TravelRequestRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun resend(
        id: NotificationId,
        actorUserId: UUID?,
    ): ResendNotificationResult =
        transactionRunner.runInTransaction {
            val notification =
                notificationRepository.findByIdForUpdate(id)
                    ?: return@runInTransaction ResendNotificationResult.NotFound
            val request = requestRepository.findById(TravelRequestId(notification.requestId.value))
            val confirmationQueued =
                request != null && notificationRepository.exists(request.id, NotificationKind.CUSTOMER_REQUEST_CONFIRMED)
            if (NotificationEligibility.stateSkipReason(notification.kind, request, confirmationQueued) != null) {
                return@runInTransaction ResendNotificationResult.RequestStateChanged
            }
            notification.requeue(Instant.now(clock), actorUserId)
            notificationRepository.save(notification)
            ResendNotificationResult.Requeued
        }
}
