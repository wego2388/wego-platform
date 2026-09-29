package com.wego.toursoperator.application

import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.Payment

/**
 * Read-only queries for payment records.
 * Used by ERP and reconciliation views.
 */
class PaymentQueryService(
    private val paymentRepository: PaymentRepository,
) {
    fun findByBookingId(bookingId: BookingId): Payment? = paymentRepository.findByBookingId(bookingId)
}
