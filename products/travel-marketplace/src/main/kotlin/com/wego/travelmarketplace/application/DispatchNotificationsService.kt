package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.NotificationKind
import com.wego.travelmarketplace.domain.RequestNotification
import com.wego.travelmarketplace.domain.TravelRequest
import com.wego.travelmarketplace.domain.TravelRequestId
import com.wego.travelmarketplace.domain.TravelRequestStatus
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant

data class NotificationSettings(
    /** Public site base URL, e.g. https://sharmtogo.example; the /track/<reference> page is under it. */
    val siteBaseUrl: String,
    /** Staff alert recipient; staff alerts are skipped while it is unset. */
    val staffAddress: String?,
    val whatsapp: String,
    val contactEmail: String,
    val maxAttempts: Int,
) {
    init {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
    }
}

/**
 * Delivers due notifications one at a time. Each row is claimed with
 * FOR UPDATE SKIP LOCKED inside its own transaction, so concurrent
 * dispatchers never send the same row, and the outcome is saved in the same
 * transaction as the claim.
 *
 * Delivery is at-least-once: if the provider accepts the email but the commit
 * then fails, the row is retried. Logs carry only ids, kinds and error
 * classes, never addresses or bodies.
 */
class DispatchNotificationsService(
    private val notificationRepository: NotificationRepository,
    private val requestRepository: TravelRequestRepository,
    private val emailSender: EmailSender,
    private val transactionRunner: TransactionRunner,
    private val settings: NotificationSettings,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(DispatchNotificationsService::class.java)

    /** Returns how many notifications reached a final or retry state in this run. */
    fun dispatchDue(limit: Int): Int {
        var processed = 0
        while (processed < limit) {
            val handled =
                transactionRunner.runInTransaction {
                    val now = Instant.now(clock)
                    val notification = notificationRepository.claimNextDue(now) ?: return@runInTransaction false
                    deliver(notification, now)
                    notificationRepository.save(notification)
                    true
                }
            if (!handled) break
            processed++
        }
        return processed
    }

    private fun deliver(
        notification: RequestNotification,
        now: Instant,
    ) {
        // Loading and rendering count as an attempt too: a row that always
        // throws must end FAILED, not block the head of the queue.
        try {
            val request = requestRepository.findById(TravelRequestId(notification.requestId.value))
            val skipReason = skipReason(notification, request)
            if (skipReason != null) {
                notification.markSkipped(skipReason)
                log.info("Notification {} ({}) skipped: {}", notification.id.value, notification.kind, skipReason)
                return
            }
            send(notification, checkNotNull(request), now)
        } catch (ex: Exception) {
            notification.markAttemptFailed(ex.javaClass.simpleName, now, settings.maxAttempts)
            log.warn(
                "Notification {} ({}) attempt {} failed: {} - status {}",
                notification.id.value,
                notification.kind,
                notification.attemptCount,
                ex.javaClass.simpleName,
                notification.status,
            )
        }
    }

    private fun skipReason(
        notification: RequestNotification,
        request: TravelRequest?,
    ): String? {
        val confirmationQueued =
            request != null &&
                notificationRepository.exists(request.id, NotificationKind.CUSTOMER_REQUEST_CONFIRMED)
        NotificationEligibility.stateSkipReason(notification.kind, request, confirmationQueued)?.let { return it }
        if (notification.kind.isCustomerFacing) {
            if (checkNotNull(request).customer.email.isNullOrBlank()) return "no_customer_email"
        } else if (settings.staffAddress.isNullOrBlank()) {
            return "no_staff_address"
        }
        return null
    }

    private fun send(
        notification: RequestNotification,
        request: TravelRequest,
        now: Instant,
    ) {
        // Customers read their own locale; the staff alert is always English.
        val english = request.locale != "ar" || !notification.kind.isCustomerFacing
        val rendered =
            NotificationTemplates.render(
                kind = notification.kind,
                locale = request.locale,
                context =
                    NotificationContext(
                        reference = request.reference.value,
                        serviceName = if (english) request.serviceName.en else request.serviceName.ar,
                        optionLabel = if (english) request.optionLabel.en else request.optionLabel.ar,
                        requestedDate = request.requestedDate,
                        requestedTime = request.requestedTime,
                        adults = request.adults,
                        children = request.children,
                        customerName = request.customer.name,
                        confirmed = request.status == TravelRequestStatus.CONFIRMED,
                        trackUrl = settings.siteBaseUrl.trimEnd('/') + "/track/" + request.reference.value,
                        whatsapp = settings.whatsapp,
                        contactEmail = settings.contactEmail,
                    ),
            )
        val recipient =
            if (notification.kind.isCustomerFacing) {
                checkNotNull(request.customer.email).trim()
            } else {
                checkNotNull(settings.staffAddress).trim()
            }
        emailSender.send(EmailMessage(to = recipient, subject = rendered.subject, body = rendered.body))
        notification.markSent(now)
        log.info("Notification {} ({}) sent", notification.id.value, notification.kind)
    }
}
