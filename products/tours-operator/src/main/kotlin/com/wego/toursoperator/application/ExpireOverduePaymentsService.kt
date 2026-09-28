package com.wego.toursoperator.application

import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.BookingStatus
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Called by the scheduled expiry job after the 30-minute payment window.
 * For each NEW booking whose payment is PENDING and older than 30 minutes,
 * expires the booking and releases the slot.
 *
 * Designed to be idempotent — re-running for the same booking is safe.
 */
class ExpireOverduePaymentsService(
    private val paymentRepository: PaymentRepository,
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
        val candidates = paymentRepository.findPendingOlderThanMinutes(PAYMENT_WINDOW_MINUTES)
        var expired = 0
        for (payment in candidates) {
            val result = expireBookingService.expire(payment.bookingId, null)
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
