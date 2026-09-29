package com.wego.toursoperator.application

import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.Payment
import com.wego.toursoperator.domain.PaymentId

/**
 * Repository contract for Payment aggregate.
 * All mutating methods must be called inside a transaction.
 */
interface PaymentRepository {
    fun findById(id: PaymentId): Payment?

    fun findByIdForUpdate(id: PaymentId): Payment?

    /** Returns null if no payment exists for this booking yet. */
    fun findByBookingId(bookingId: BookingId): Payment?

    fun findByBookingIdForUpdate(bookingId: BookingId): Payment?

    /** Returns null if the Paymob order ID is unknown. */
    fun findByPaymobOrderId(paymobOrderId: String): Payment?

    fun findByPaymobOrderIdForUpdate(paymobOrderId: String): Payment?

    fun save(payment: Payment)
}
