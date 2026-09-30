package com.wego.toursoperator.application

import com.wego.toursoperator.domain.Booking
import com.wego.toursoperator.domain.CustomerNotification
import com.wego.toursoperator.domain.NotificationKind
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class NotificationSettings(
    /** Public site base URL, e.g. https://example.com — the My Booking page is under it. */
    val siteBaseUrl: String,
    /** Where customers leave a review; review requests are skipped while it is unset. */
    val reviewUrl: String?,
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
 * classes — never addresses or bodies.
 */
class DispatchNotificationsService(
    private val notificationRepository: NotificationRepository,
    private val bookingRepository: BookingRepository,
    private val tourRepository: TourRepository,
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
        notification: CustomerNotification,
        now: Instant,
    ) {
        // Everything, including loading and rendering, counts as an attempt:
        // a row that always throws must end FAILED, not block the queue head.
        try {
            val booking = bookingRepository.findById(notification.bookingId)
            val skipReason = skipReason(notification, booking, now)
            if (skipReason != null) {
                notification.markSkipped(skipReason)
                log.info("Notification {} ({}) skipped: {}", notification.id.value, notification.kind, skipReason)
                return
            }
            send(notification, checkNotNull(booking), now)
        } catch (ex: Exception) {
            notification.markAttemptFailed(ex.javaClass.simpleName, now, settings.maxAttempts)
            log.warn(
                "Notification {} ({}) attempt {} failed: {} — status {}",
                notification.id.value,
                notification.kind,
                notification.attemptCount,
                ex.javaClass.simpleName,
                notification.status,
            )
        }
    }

    /**
     * Messages are sent only while still true. A confirmation delayed by
     * retries must not reach a customer whose booking was cancelled since,
     * and a confirmation for a tour that already took place is pointless.
     */
    private fun skipReason(
        notification: CustomerNotification,
        booking: Booking?,
        now: Instant,
    ): String? {
        if (booking == null) return "booking_missing"
        if (booking.status != notification.kind.requiredBookingStatus) return "booking_state_changed"
        if (notification.kind == NotificationKind.BOOKING_CONFIRMED &&
            booking.tourDate.isBefore(LocalDate.ofInstant(now, REPORTING_ZONE))
        ) {
            return "tour_already_past"
        }
        if (booking.customer.email.isNullOrBlank()) return "no_customer_email"
        if (notification.kind == NotificationKind.REVIEW_REQUEST && settings.reviewUrl.isNullOrBlank()) {
            return "review_link_not_configured"
        }
        return null
    }

    private fun send(
        notification: CustomerNotification,
        booking: Booking,
        now: Instant,
    ) {
        val tour = tourRepository.findById(booking.tourId)
        val rendered =
            NotificationTemplates.render(
                kind = notification.kind,
                locale = booking.locale,
                context =
                    NotificationContext(
                        reference = booking.reference,
                        tourName = tour?.nameEn?.takeIf { it.isNotBlank() } ?: tour?.slug ?: "Safari Tours Sharm",
                        tourDate = booking.tourDate,
                        timeSlot = booking.timeSlot,
                        adults = booking.pricing.adultsCount,
                        children = booking.pricing.childrenCount,
                        totalEur =
                            booking.pricing.totalEur.amount
                                .toPlainString(),
                        myBookingUrl = settings.siteBaseUrl.trimEnd('/') + "/my-booking",
                        reviewUrl = settings.reviewUrl,
                    ),
            )
        emailSender.send(EmailMessage(to = checkNotNull(booking.customer.email).trim(), subject = rendered.subject, body = rendered.body))
        notification.markSent(now)
        log.info("Notification {} ({}) sent", notification.id.value, notification.kind)
    }

    private companion object {
        val REPORTING_ZONE: ZoneId = ZoneId.of("Africa/Cairo")
    }
}
