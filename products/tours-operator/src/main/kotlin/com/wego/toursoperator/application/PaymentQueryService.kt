package com.wego.toursoperator.application

import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.Payment
import com.wego.toursoperator.domain.PaymentId
import com.wego.toursoperator.domain.PaymentStatus
import java.time.Instant

/**
 * Read-only queries for payment records.
 * Used by ERP and reconciliation views.
 */
class PaymentQueryService(
    private val paymentRepository: PaymentRepository,
) {
    fun findByBookingId(bookingId: BookingId): Payment? = paymentRepository.findByBookingId(bookingId)

    fun historyForBooking(bookingId: BookingId): List<PaymentHistoryEntry> = paymentRepository.historyForBooking(bookingId)

    fun listActivity(
        fromInclusive: Instant,
        toExclusive: Instant,
        status: PaymentStatus?,
        afterId: PaymentId?,
        size: Int,
    ): List<PaymentActivity> = paymentRepository.listActivity(fromInclusive, toExclusive, status, afterId, size)
}
