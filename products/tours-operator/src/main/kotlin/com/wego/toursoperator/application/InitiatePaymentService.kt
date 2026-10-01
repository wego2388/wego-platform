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

    /** A manager paused payment checkouts (emergency sales control). Nothing was created. */
    data object PaymentsPaused : InitiatePaymentResult()

    /**
     * Booking is not in NEW state — e.g. already confirmed, cancelled, or expired.
     * Returns the existing payment if one exists so the caller can redirect.
     */
    data class BookingNotPayable(
        val bookingStatus: BookingStatus,
    ) : InitiatePaymentResult()

    /** A prior payment reached a terminal state; its provider identity must never be overwritten. */
    data class PaymentNotPayable(
        val paymentStatus: PaymentStatus,
    ) : InitiatePaymentResult()

    /** Provider creation may have happened; a second automatic attempt could double-charge. */
    data object ReconciliationRequired : InitiatePaymentResult()

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
 * - A stable payment/provider reference is committed before the external call.
 *   A crash or ambiguous timeout can therefore be reconciled without issuing
 *   a second payable intention.
 * - The booking row is locked before checking/creating its one payment.
 * - If a PENDING payment already exists, its short-lived checkout token is
 *   reused. PENDING-without-token, FAILED, and RECONCILIATION_REQUIRED are
 *   never retried automatically because their original provider identity is
 *   financial evidence.
 * - If the booking is not in NEW state, we refuse.
 */
class InitiatePaymentService(
    private val bookingRepository: BookingRepository,
    private val paymentRepository: PaymentRepository,
    private val paymobClient: PaymobClient,
    private val salesControlRepository: SalesControlRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun initiate(command: InitiatePaymentCommand): InitiatePaymentResult {
        val preparation =
            transactionRunner.runInTransaction {
                val booking =
                    bookingRepository.findByIdForUpdate(command.bookingId)
                        ?: return@runInTransaction PaymentPreparation.Completed(InitiatePaymentResult.BookingNotFound)

                if (booking.status != BookingStatus.NEW) {
                    return@runInTransaction PaymentPreparation.Completed(
                        InitiatePaymentResult.BookingNotPayable(booking.status),
                    )
                }

                // Checked before any payment row is written and before resuming
                // an existing checkout: while paused, nobody is sent to the provider.
                if (salesControlRepository.current().paymentsPaused) {
                    return@runInTransaction PaymentPreparation.Completed(InitiatePaymentResult.PaymentsPaused)
                }

                val existing = paymentRepository.findByBookingIdForUpdate(command.bookingId)
                if (existing?.status == PaymentStatus.PENDING && existing.providerCheckoutToken != null) {
                    val resumeUrl = paymobClient.buildCheckoutUrl(existing.providerCheckoutToken!!)
                    return@runInTransaction PaymentPreparation.Completed(
                        InitiatePaymentResult.AlreadyInitiated(existing, resumeUrl),
                    )
                }
                if (existing?.status in setOf(PaymentStatus.PENDING, PaymentStatus.RECONCILIATION_REQUIRED)) {
                    return@runInTransaction PaymentPreparation.Completed(
                        InitiatePaymentResult.ReconciliationRequired,
                    )
                }
                if (existing != null) {
                    return@runInTransaction PaymentPreparation.Completed(
                        InitiatePaymentResult.PaymentNotPayable(existing.status),
                    )
                }

                val amountEur = booking.pricing.totalEur.amount
                val amountMinorUnits =
                    amountEur
                        .multiply(BigDecimal(100))
                        .setScale(0, RoundingMode.HALF_UP)
                        .toLongExact()

                val payment =
                    Payment.createPending(
                        id = PaymentId.generate(),
                        bookingId = command.bookingId,
                        amountEur = amountEur,
                        amountMinorUnits = amountMinorUnits,
                        currencyCode = Money.CURRENCY_CODE,
                        now = Instant.now(clock),
                    )
                paymentRepository.save(payment)

                PaymentPreparation.CreateAtProvider(
                    bookingId = booking.id,
                    paymentId = payment.id,
                    checkoutCommand =
                        PaymobCheckoutCommand(
                            merchantRefNumber = payment.providerReference,
                            amountCents = amountMinorUnits,
                            currencyCode = Money.CURRENCY_CODE,
                            items =
                                listOf(
                                    PaymobOrderItem(
                                        name = "Safari Tours Sharm booking",
                                        amountCents = amountMinorUnits,
                                        quantity = 1,
                                    ),
                                ),
                            billing =
                                PaymobBillingData(
                                    fullName = booking.customer.fullName,
                                    phone = booking.customer.phone,
                                    email = booking.customer.email,
                                ),
                        ),
                )
            }

        if (preparation is PaymentPreparation.Completed) return preparation.result
        preparation as PaymentPreparation.CreateAtProvider

        val checkoutResult =
            try {
                paymobClient.createCheckout(preparation.checkoutCommand)
            } catch (_: RuntimeException) {
                PaymobCheckoutResult.Failure("Provider checkout outcome is unknown")
            }

        return when (checkoutResult) {
            is PaymobCheckoutResult.Success -> attachProviderCheckout(preparation, checkoutResult)
            is PaymobCheckoutResult.Failure -> {
                markReconciliationRequired(preparation, "CREATE_OUTCOME_UNKNOWN")
                InitiatePaymentResult.ProviderError(checkoutResult.message)
            }
        }
    }

    private fun attachProviderCheckout(
        preparation: PaymentPreparation.CreateAtProvider,
        checkout: PaymobCheckoutResult.Success,
    ): InitiatePaymentResult =
        transactionRunner.runInTransaction {
            val booking =
                bookingRepository.findByIdForUpdate(preparation.bookingId)
                    ?: return@runInTransaction InitiatePaymentResult.BookingNotFound
            val payment =
                paymentRepository.findByIdForUpdate(preparation.paymentId)
                    ?: return@runInTransaction InitiatePaymentResult.BookingNotFound

            if (payment.paymobOrderId != null && payment.providerCheckoutToken != null) {
                return@runInTransaction InitiatePaymentResult.AlreadyInitiated(
                    payment,
                    paymobClient.buildCheckoutUrl(payment.providerCheckoutToken!!),
                )
            }
            if (payment.status != PaymentStatus.PENDING || payment.paymobOrderId != null) {
                return@runInTransaction InitiatePaymentResult.ReconciliationRequired
            }

            payment.assignPaymobCheckout(checkout.orderId, checkout.checkoutToken)
            if (booking.status != BookingStatus.NEW) {
                payment.markReconciliationRequired("BOOKING_${booking.status.name}", Instant.now(clock))
                paymentRepository.save(payment)
                return@runInTransaction InitiatePaymentResult.ReconciliationRequired
            }
            paymentRepository.save(payment)

            InitiatePaymentResult.Initiated(
                payment,
                paymobClient.buildCheckoutUrl(checkout.checkoutToken),
            )
        }

    private fun markReconciliationRequired(
        preparation: PaymentPreparation.CreateAtProvider,
        providerStatus: String,
    ) {
        transactionRunner.runInTransaction {
            bookingRepository.findByIdForUpdate(preparation.bookingId)
            val payment = paymentRepository.findByIdForUpdate(preparation.paymentId)
            if (payment?.status == PaymentStatus.PENDING && payment.paymobOrderId == null) {
                payment.markReconciliationRequired(providerStatus, Instant.now(clock))
                paymentRepository.save(payment)
            }
        }
    }

    private fun BigDecimal.toLongExact(): Long = this.longValueExact()
}

private sealed interface PaymentPreparation {
    data class Completed(
        val result: InitiatePaymentResult,
    ) : PaymentPreparation

    data class CreateAtProvider(
        val bookingId: BookingId,
        val paymentId: PaymentId,
        val checkoutCommand: PaymobCheckoutCommand,
    ) : PaymentPreparation
}
