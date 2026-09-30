package com.wego.toursoperator.application

import com.wego.toursoperator.domain.NotificationId
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed class ResendNotificationResult {
    data object Requeued : ResendNotificationResult()

    data object NotFound : ResendNotificationResult()

    /** The message is no longer true (e.g. a confirmation for a cancelled booking). */
    data object BookingStateChanged : ResendNotificationResult()
}

/** Staff action: queue a notification again (after FAILED, SKIPPED, or a lost SENT email). */
class ResendNotificationService(
    private val notificationRepository: NotificationRepository,
    private val bookingRepository: BookingRepository,
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
            val booking = bookingRepository.findById(notification.bookingId)
            if (booking?.status != notification.kind.requiredBookingStatus) {
                return@runInTransaction ResendNotificationResult.BookingStateChanged
            }
            notification.requeue(Instant.now(clock), actorUserId)
            notificationRepository.save(notification)
            ResendNotificationResult.Requeued
        }
}
