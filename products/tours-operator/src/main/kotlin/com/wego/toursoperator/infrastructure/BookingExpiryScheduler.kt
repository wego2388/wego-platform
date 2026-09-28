package com.wego.toursoperator.infrastructure

import com.wego.toursoperator.application.ExpireOverduePaymentsService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Scheduled job that expires bookings whose 30-minute payment window has elapsed.
 *
 * Runs every 5 minutes — safe to run more frequently since ExpireBookingService
 * is idempotent (AlreadyExpired is a no-op).
 *
 * This bean requires @EnableScheduling on the application.  The scheduling is
 * enabled in the main application configuration via the existing Spring Boot
 * auto-configuration.
 *
 * Production note: for a clustered deployment, use ShedLock or a similar
 * distributed lock to prevent concurrent runs across pods.  For the initial
 * single-instance deployment this is acceptable.
 */
@Component
class BookingExpiryScheduler(
    private val expireOverduePaymentsService: ExpireOverduePaymentsService,
) {
    private val log = LoggerFactory.getLogger(BookingExpiryScheduler::class.java)

    @Scheduled(fixedDelayString = "\${tours-operator.expiry-job.interval-ms:300000}")
    fun runExpiryJob() {
        try {
            val count = expireOverduePaymentsService.expireOverdue()
            if (count > 0) {
                log.info("BookingExpiryScheduler: expired {} overdue booking(s)", count)
            }
        } catch (ex: Exception) {
            // Log but do not rethrow — a scheduler exception would stop future runs.
            log.error("BookingExpiryScheduler: unexpected error during expiry run", ex)
        }
    }
}
