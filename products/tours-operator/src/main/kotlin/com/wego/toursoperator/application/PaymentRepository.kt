package com.wego.toursoperator.application

import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.Payment
import com.wego.toursoperator.domain.PaymentId
import com.wego.toursoperator.domain.PaymentStatus
import com.wego.toursoperator.domain.TourId
import java.time.Instant

/** Minimum non-PII booking context required to aggregate a payment by tour. */
data class PaymentActivity(
    val payment: Payment,
    val tourId: TourId,
    val adultsCount: Int,
    val childrenCount: Int,
)

/** One recorded payment status change, as shown to finance staff. */
data class PaymentHistoryEntry(
    val paymentId: PaymentId,
    val fromStatus: PaymentStatus?,
    val toStatus: PaymentStatus,
    val providerStatus: String?,
    val occurredAt: Instant,
    /** False for steps reconstructed from pre-history state (V21 backfill). */
    val recorded: Boolean,
)

/**
 * Repository contract for Payment aggregate.
 * All mutating methods must be called inside a transaction.
 */
interface PaymentRepository {
    /** Startup guard: existence only, all statuses, no booking or customer data. */
    fun hasAnyPayments(): Boolean

    fun findById(id: PaymentId): Payment?

    fun findByIdForUpdate(id: PaymentId): Payment?

    /** Returns null if no payment exists for this booking yet. */
    fun findByBookingId(bookingId: BookingId): Payment?

    fun findByBookingIdForUpdate(bookingId: BookingId): Payment?

    /** Returns null if the Paymob order ID is unknown. */
    fun findByPaymobOrderId(paymobOrderId: String): Payment?

    fun findByPaymobOrderIdForUpdate(paymobOrderId: String): Payment?

    /**
     * Staff ledger query. A row is included when any financial lifecycle event
     * occurred inside the half-open interval, so a refund in a later period is
     * never hidden merely because its original payment was older.
     *
     * Keyset-paged by payment id ascending: pass the last id of the previous
     * page as [afterId]. Unlike offset paging, rows inserted or changed while a
     * report is paging can never make a payment appear on two pages.
     */
    fun listActivity(
        fromInclusive: Instant,
        toExclusive: Instant,
        status: PaymentStatus?,
        afterId: PaymentId?,
        size: Int,
    ): List<PaymentActivity>

    /** Payment status history for a booking, oldest first. */
    fun historyForBooking(bookingId: BookingId): List<PaymentHistoryEntry>

    /**
     * Claims one provider refund callback identity for a payment.
     *
     * The claim is durable and unique per (payment, providerRefundId), so a
     * replay remains idempotent even after a different refund callback was
     * received in between. Returns true only for the first observation.
     * Must be called inside the payment webhook transaction.
     */
    fun claimRefundCallback(
        paymentId: PaymentId,
        providerRefundId: String,
        amountMinorUnits: Long,
        receivedAt: Instant,
    ): Boolean

    /** Persists state and appends every drained [Payment.drainTransitions] entry in one transaction. */
    fun save(payment: Payment)
}
