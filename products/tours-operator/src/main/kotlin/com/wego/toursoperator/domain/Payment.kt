package com.wego.toursoperator.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Value object wrapping a payment record identifier.
 */
@JvmInline
value class PaymentId(val value: UUID) {
    companion object {
        fun generate(): PaymentId = PaymentId(UUID.randomUUID())
    }
}

/**
 * Payment aggregate for a tours-operator booking.
 *
 * State machine:
 *   PENDING → PAID      (Paymob webhook: success)
 *   PENDING → FAILED    (Paymob webhook: declined / timeout)
 *   PAID    → REFUNDED  (refund API call confirmed by Paymob)
 *
 * Invariants:
 * - amountEur and amountMinorUnits are immutable after creation.
 * - bookingId is set once and never changes.
 * - Only server-side booking total is ever used — the client never
 *   sends an amount to the payment layer.
 * - A paymobOrderId is assigned at order-creation time; the
 *   paymobTransactionId is set only when a webhook arrives.
 */
class Payment(
    val id: PaymentId,
    val bookingId: BookingId,
    /** Immutable EUR amount captured from booking pricing snapshot. */
    val amountEur: BigDecimal,
    /**
     * EUR cents (amount × 100, rounded to nearest integer).
     * Paymob expects amounts in minor units (EGP piastres or EUR cents).
     */
    val amountMinorUnits: Long,
    val currencyCode: String,
    paymobOrderId: String?,
    paymobTransactionId: String?,
    status: PaymentStatus,
    providerStatus: String?,
    lastCallbackPayload: String?,
    val createdAt: Instant,
    paidAt: Instant?,
    failedAt: Instant?,
    refundedAt: Instant?,
) {
    var paymobOrderId: String? = paymobOrderId
        private set

    var paymobTransactionId: String? = paymobTransactionId
        private set

    var status: PaymentStatus = status
        private set

    var providerStatus: String? = providerStatus
        private set

    var lastCallbackPayload: String? = lastCallbackPayload
        private set

    var paidAt: Instant? = paidAt
        private set

    var failedAt: Instant? = failedAt
        private set

    var refundedAt: Instant? = refundedAt
        private set

    init {
        require(amountEur > BigDecimal.ZERO) { "Payment amount must be positive" }
        require(amountMinorUnits > 0L) { "Payment minor units must be positive" }
        require(currencyCode.matches(Regex("^[A-Z]{3}$"))) {
            "currencyCode must be a 3-letter ISO-4217 code"
        }
        validateStatusConsistency()
    }

    private fun validateStatusConsistency() {
        require((status == PaymentStatus.PAID) == (paidAt != null)) {
            "paidAt must be set if and only if status is PAID (status: $status)"
        }
        require((status == PaymentStatus.FAILED) == (failedAt != null)) {
            "failedAt must be set if and only if status is FAILED (status: $status)"
        }
        require((status == PaymentStatus.REFUNDED) == (refundedAt != null)) {
            "refundedAt must be set if and only if status is REFUNDED (status: $status)"
        }
    }

    /** Paymob order created — record the external order ID. */
    fun assignPaymobOrder(orderId: String) {
        require(paymobOrderId == null) { "Paymob order already assigned: $paymobOrderId" }
        require(orderId.isNotBlank()) { "Paymob orderId must not be blank" }
        paymobOrderId = orderId
    }

    /**
     * Webhook received — payment succeeded.
     * Safe to call multiple times (idempotent): AlreadyPaid guard in service.
     */
    fun markPaid(
        transactionId: String,
        providerStatus: String,
        callbackPayload: String,
        now: Instant,
    ) {
        require(status == PaymentStatus.PENDING) {
            "Only PENDING payments can be marked PAID (current: $status)"
        }
        this.paymobTransactionId = transactionId
        this.providerStatus = providerStatus
        this.lastCallbackPayload = callbackPayload
        this.status = PaymentStatus.PAID
        this.paidAt = now
    }

    /** Webhook received — payment declined or timed out. */
    fun markFailed(
        transactionId: String?,
        providerStatus: String,
        callbackPayload: String,
        now: Instant,
    ) {
        require(status == PaymentStatus.PENDING) {
            "Only PENDING payments can be marked FAILED (current: $status)"
        }
        this.paymobTransactionId = transactionId
        this.providerStatus = providerStatus
        this.lastCallbackPayload = callbackPayload
        this.status = PaymentStatus.FAILED
        this.failedAt = now
    }

    /** Refund confirmed by Paymob. */
    fun markRefunded(
        callbackPayload: String,
        now: Instant,
    ) {
        require(status == PaymentStatus.PAID) {
            "Only PAID payments can be refunded (current: $status)"
        }
        this.lastCallbackPayload = callbackPayload
        this.status = PaymentStatus.REFUNDED
        this.refundedAt = now
    }

    companion object {
        fun createPending(
            id: PaymentId,
            bookingId: BookingId,
            amountEur: BigDecimal,
            amountMinorUnits: Long,
            currencyCode: String,
            now: Instant,
        ): Payment =
            Payment(
                id = id,
                bookingId = bookingId,
                amountEur = amountEur,
                amountMinorUnits = amountMinorUnits,
                currencyCode = currencyCode,
                paymobOrderId = null,
                paymobTransactionId = null,
                status = PaymentStatus.PENDING,
                providerStatus = null,
                lastCallbackPayload = null,
                createdAt = now,
                paidAt = null,
                failedAt = null,
                refundedAt = null,
            )
    }
}
