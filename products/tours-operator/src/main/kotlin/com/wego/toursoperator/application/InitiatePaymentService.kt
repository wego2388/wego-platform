package com.wego.toursoperator.application

import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.BookingStatus
import com.wego.toursoperator.domain.Money
import com.wego.toursoperator.domain.Payment
import com.wego.toursoperator.domain.PaymentId
import com.wego.toursoperator.domain.PaymentStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class InitiatePaymentCommand(
    val bookingId: BookingId,
    val correlationId: UUID?,
)

sealed class InitiatePaymentResult {
    /** Payment record created and Paymob order created. Returns the Paymob checkout URL. */
    data class Initiated(
        val payment: Payment,
        val checkoutUrl: String,
    ) : InitiatePaymentResult()

    /** Booking not found. */
    data object BookingNotFound : InitiatePaymentResult()

    /**
     * Booking is not in NEW state — e.g. already confirmed, cancelled, or expired.
     * Returns the existing payment if one exists so the caller can redirect.
     */
    data class BookingNotPayable(
        val bookingStatus: BookingStatus,
    ) : InitiatePaymentResult()

    /** Payment already initiated — return the existing checkout URL for resume. */
    data class AlreadyInitiated(
        val payment: Payment,
        val checkoutUrl: String,
    ) : InitiatePaymentResult()

    /** Paymob order creation failed (network / provider error). */
    data class ProviderError(
        val message: String,
    ) : InitiatePaymentResult()
}

/**
 * Creates a PENDING payment record and opens a Paymob order.
 *
 * Key invariants:
 * - The amount is always taken from the server-side booking snapshot —
 *   never from any client-supplied value.
 * - If a PENDING payment already exists for the booking, we reuse the
 *   existing Paymob order (idempotent resume).
 * - If the booking is not in NEW state, we refuse.
 */
class InitiatePaymentService(
    private val bookingRepository: BookingRepository,
    private val paymentRepository: PaymentRepository,
    private val paymobClient: PaymobClient,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun initiate(command: InitiatePaymentCommand): InitiatePaymentResult {
        // Step 1 — load booking (read-only, outside transaction)
        val booking = bookingRepository.findById(command.bookingId)
            ?: return InitiatePaymentResult.BookingNotFound

        if (booking.status != BookingStatus.NEW) {
            return InitiatePaymentResult.BookingNotPayable(booking.status)
        }

        // Step 2 — check for existing PENDING payment (idempotent resume)
        val existing = paymentRepository.findByBookingId(command.bookingId)
        if (existing != null && existing.status == PaymentStatus.PENDING && existing.paymobOrderId != null) {
            val resumeUrl = paymobClient.buildCheckoutUrl(existing.paymobOrderId!!)
            return InitiatePaymentResult.AlreadyInitiated(existing, resumeUrl)
        }

        // Step 3 — compute minor units from booking total (EUR cents)
        val amountEur = booking.pricing.totalEur.amount
        val amountMinorUnits = amountEur
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .toLongExact()

        // Step 4 — call Paymob to create the order (outside DB transaction to avoid long lock)
        val orderResult = paymobClient.createOrder(
            merchantRefNumber = command.bookingId.value.toString(),
            amountCents = amountMinorUnits,
            currencyCode = Money.CURRENCY_CODE,
            items = listOf(
                PaymobOrderItem(
                    name = "Tour Booking ${booking.reference}",
                    amountCents = amountMinorUnits,
                    quantity = 1,
                ),
            ),
        )

        val paymobOrderId = when (orderResult) {
            is PaymobOrderResult.Success -> orderResult.orderId
            is PaymobOrderResult.Failure -> return InitiatePaymentResult.ProviderError(orderResult.message)
        }

        // Step 5 — persist payment record + assign order ID in a transaction
        val payment = transactionRunner.runInTransaction {
            val now = Instant.now(clock)
            val p = Payment.createPending(
                id = PaymentId.generate(),
                bookingId = command.bookingId,
                amountEur = amountEur,
                amountMinorUnits = amountMinorUnits,
                currencyCode = Money.CURRENCY_CODE,
                now = now,
            )
            p.assignPaymobOrder(paymobOrderId)
            paymentRepository.save(p)
            p
        }

        val checkoutUrl = paymobClient.buildCheckoutUrl(paymobOrderId)
        return InitiatePaymentResult.Initiated(payment, checkoutUrl)
    }

    private fun BigDecimal.toLongExact(): Long = this.longValueExact()
}
