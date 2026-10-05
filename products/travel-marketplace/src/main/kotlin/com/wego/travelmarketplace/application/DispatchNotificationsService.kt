package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.NotificationKind
import com.wego.travelmarketplace.domain.RequestNotification
import com.wego.travelmarketplace.domain.TravelRequest
import com.wego.travelmarketplace.domain.TravelRequestId
import com.wego.travelmarketplace.domain.TravelRequestStatus
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class NotificationSettings(
    /** Public site base URL, e.g. https://sharmtogo.example; the /track/<reference> page is under it. */
    val siteBaseUrl: String,
    /** Staff alert recipient; staff alerts are skipped while it is unset. */
    val staffAddress: String?,
    val whatsapp: String,
    val contactEmail: String,
    val maxAttempts: Int,
    /**
     * How long a claimed row stays hidden from other dispatchers while its
     * email is being sent. Must comfortably exceed the SMTP timeouts, or a
     * slow send could overlap the next claim.
     */
    val lease: Duration = DEFAULT_LEASE,
) {
    init {
        require(maxAttempts > 0) { "maxAttempts must be positive" }
        require(lease >= MIN_LEASE) { "lease must be at least $MIN_LEASE (twice the SMTP timeout)" }
    }

    companion object {
        /** Twice the SMTP timeout the mail sender applies by default. */
        val MIN_LEASE: Duration = SMTP_TIMEOUT.multipliedBy(2)
        val DEFAULT_LEASE: Duration = Duration.ofMinutes(5)
    }
}

/** The SMTP connect/read/write timeout the mail sender applies unless configured otherwise. */
val SMTP_TIMEOUT: Duration = Duration.ofSeconds(15)

/**
 * Delivers due notifications one row at a time, in three steps so that no
 * transaction, row lock or DB connection is held while the mail relay is
 * contacted and every send is on record before it happens:
 *
 * 1. Claim (own transaction): lock one due row (FOR UPDATE SKIP LOCKED), count
 *    the attempt, push available_at out by the lease, commit. Other
 *    dispatchers cannot see the row until the lease runs out.
 * 2. Look up the request and send, outside any transaction. The lookups run
 *    in their own read transaction, so a database error there is just a
 *    failed attempt and cannot poison anything else.
 * 3. Record the outcome (own transaction), guarded to apply only while the row
 *    is still PENDING at the claimed attempt.
 *
 * Delivery is at-least-once, bounded by the maximum attempts: if the process
 * dies or step 3 fails after the provider accepted the message, the row comes
 * back after the lease, and a row whose attempts are used up ends FAILED
 * without another send. Logs carry only ids, kinds and error classes, never
 * addresses or bodies.
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

    /** Returns how many notifications were claimed and attempted in this run. */
    fun dispatchDue(limit: Int): Int {
        var processed = 0
        while (processed < limit) {
            // The transaction runner rejects a null result, hence the list.
            val claimed = transactionRunner.runInTransaction { listOfNotNull(claimNext()) }.firstOrNull() ?: break
            processed++
            val claimedAttempt = claimed.attemptCount
            val sentAt = Instant.now(clock)
            deliver(claimed, sentAt)
            try {
                val recorded = transactionRunner.runInTransaction { notificationRepository.recordOutcome(claimed, claimedAttempt) }
                if (!recorded) {
                    log.warn("Notification {} ({}) outcome not recorded: row changed meanwhile", claimed.id.value, claimed.kind)
                }
            } catch (ex: Exception) {
                // The attempt is already counted and the row stays leased, so it
                // returns after the lease and can never exceed the attempt budget.
                log.error(
                    "Notification {} ({}) outcome not recorded: {}",
                    claimed.id.value,
                    claimed.kind,
                    ex.javaClass.simpleName,
                )
            }
        }
        return processed
    }

    private fun claimNext(): RequestNotification? {
        val now = Instant.now(clock)
        while (true) {
            val notification = notificationRepository.claimNextDue(now) ?: return null
            if (notification.attemptCount >= settings.maxAttempts) {
                // An earlier attempt claimed this row but never recorded its result.
                notification.markExhausted()
                notificationRepository.save(notification)
                log.warn("Notification {} ({}) exhausted its attempts without a recorded result", notification.id.value, notification.kind)
                continue
            }
            notification.claim(now.plus(settings.lease))
            notificationRepository.save(notification)
            return notification
        }
    }

    private fun deliver(
        notification: RequestNotification,
        now: Instant,
    ) {
        // Loading and rendering count as the attempt too: a row that always
        // throws must end FAILED, not block the head of the queue.
        try {
            val lookup =
                transactionRunner.runInTransaction {
                    val request = requestRepository.findById(TravelRequestId(notification.requestId.value))
                    val confirmationQueued =
                        request != null && notificationRepository.exists(request.id, NotificationKind.CUSTOMER_REQUEST_CONFIRMED)
                    request to confirmationQueued
                }
            val (request, confirmationQueued) = lookup
            val skipReason = skipReason(notification, request, confirmationQueued)
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
        confirmationQueued: Boolean,
    ): String? {
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
