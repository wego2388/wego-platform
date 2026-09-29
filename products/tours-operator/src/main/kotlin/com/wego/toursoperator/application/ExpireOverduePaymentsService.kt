package com.wego.toursoperator.application

import java.time.Clock
import java.time.Instant

/**
 * Called by the scheduled expiry job after the 30-minute payment window.
 * Expires every NEW booking older than the window, including bookings where
 * payment initiation never reached the provider or already failed.
 *
 * Designed to be idempotent — re-running for the same booking is safe.
 */
class ExpireOverduePaymentsService(
    private val bookingRepository: BookingRepository,
    private val expireBookingService: ExpireBookingService,
    private val clock: Clock,
) {
    companion object {
        const val PAYMENT_WINDOW_MINUTES = 30L
    }

    /**
     * Finds all PENDING payments older than the payment window and expires
     * the associated bookings.  Returns the number of bookings expired.
     */
    fun expireOverdue(): Int {
        val cutoff = Instant.now(clock).minusSeconds(PAYMENT_WINDOW_MINUTES * 60)
        val candidates = bookingRepository.findNewCreatedBefore(cutoff)
        var expired = 0
        for (booking in candidates) {
            val result = expireBookingService.expire(booking.id, null)
            when (result) {
                is ExpireBookingResult.Expired -> expired++
                is ExpireBookingResult.AlreadyExpired -> { /* safe — already done */ }
                is ExpireBookingResult.NotFound -> { /* booking deleted externally — ignore */ }
                is ExpireBookingResult.CannotExpire -> { /* booking moved to another terminal state */ }
            }
        }
        return expired
    }
}
