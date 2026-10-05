package com.wego.toursoperator.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Value object wrapping a payment record identifier.
 */
@JvmInline
value class PaymentId(
    val value: UUID,
) {
    companion object {
        fun generate(): PaymentId = PaymentId(UUID.randomUUID())
    }
}

/** One append-only payment status change; [fromStatus] is null for creation. */
data class PaymentTransition(
    val fromStatus: PaymentStatus?,
    val toStatus: PaymentStatus,
    val providerStatus: String?,
    val occurredAt: Instant,
)

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
    val providerReference: String,
    paymobOrderId: String?,
    paymobTransactionId: String?,
    providerCheckoutToken: String?,
    status: PaymentStatus,
    providerStatus: String?,
    lastCallbackAudit: String?,
    val createdAt: Instant,
    paidAt: Instant?,
    failedAt: Instant?,
    refundedAt: Instant?,
    revenueRecognisedAt: Instant?,
) {
    var paymobOrderId: String? = paymobOrderId
        private set

    var paymobTransactionId: String? = paymobTransactionId
        private set

    var providerCheckoutToken: String? = providerCheckoutToken
        private set

    var status: PaymentStatus = status
        private set

    var providerStatus: String? = providerStatus
        private set

    var lastCallbackAudit: String? = lastCallbackAudit
        private set

    var paidAt: Instant? = paidAt
        private set

    var failedAt: Instant? = failedAt
        private set

    var refundedAt: Instant? = refundedAt
        private set

    /**
     * When the capture was accepted as revenue. Set only by [markPaid], absent
     * for a payment initially held for review, and kept through a later review
     * or refund so finance can distinguish a captured sale from an
     * unrecognised payment.
     */
    var revenueRecognisedAt: Instant? = revenueRecognisedAt
        private set

    private val unsavedTransitions = mutableListOf<PaymentTransition>()

    /**
     * Status changes made on this instance since it was loaded or created, in
     * order. The repository persists them append-only in the same transaction
     * as the state itself, then they are cleared.
     */
    fun drainTransitions(): List<PaymentTransition> = unsavedTransitions.toList().also { unsavedTransitions.clear() }

    private fun transitionTo(
        target: PaymentStatus,
        now: Instant,
    ) {
        unsavedTransitions += PaymentTransition(status, target, providerStatus, now)
        status = target
    }

    init {
        require(amountEur > BigDecimal.ZERO) { "Payment amount must be positive" }
        require(amountMinorUnits > 0L) { "Payment minor units must be positive" }
        require(currencyCode.matches(Regex("^[A-Z]{3}$"))) {
            "currencyCode must be a 3-letter ISO-4217 code"
        }
        require(providerReference.matches(Regex("^[A-Za-z0-9-]{8,80}$"))) {
            "providerReference must be a stable non-secret merchant reference"
        }
        validateStatusConsistency()
    }

    private fun validateStatusConsistency() {
        val providerCaptured = status in setOf(PaymentStatus.PAID, PaymentStatus.REFUNDED, PaymentStatus.REVIEW_REQUIRED)
        require(providerCaptured == (paidAt != null)) {
            "paidAt must be set for provider-captured states only (status: $status)"
        }
        require((status == PaymentStatus.FAILED) == (failedAt != null)) {
            "failedAt must be set if and only if status is FAILED (status: $status)"
        }
        require((status == PaymentStatus.REFUNDED) == (refundedAt != null)) {
            "refundedAt must be set if and only if status is REFUNDED (status: $status)"
        }
        require(status != PaymentStatus.PAID || revenueRecognisedAt != null) {
            "a PAID payment must be recognised as revenue"
        }
        require(revenueRecognisedAt == null || status in setOf(PaymentStatus.PAID, PaymentStatus.REFUNDED, PaymentStatus.REVIEW_REQUIRED)) {
            "only captured payments can carry revenue recognition (status: $status)"
        }
    }

    /** Paymob checkout created — record its external order and short-lived client secret. */
    fun assignPaymobCheckout(
        orderId: String,
        checkoutToken: String,
    ) {
        require(paymobOrderId == null) { "Paymob order already assigned: $paymobOrderId" }
        require(orderId.isNotBlank()) { "Paymob orderId must not be blank" }
        require(checkoutToken.isNotBlank()) { "Paymob checkout token must not be blank" }
        paymobOrderId = orderId
        providerCheckoutToken = checkoutToken
    }

    /**
     * The provider may have accepted an intention even though Wego did not
     * receive or persist the response. Keep every known identifier and block
     * automatic retries until an operator/provider inquiry reconciles it.
     */
    fun markReconciliationRequired(
        providerStatus: String,
        now: Instant,
    ) {
        require(status == PaymentStatus.PENDING) {
            "Only an initiating PENDING payment can require reconciliation (current: $status)"
        }
        providerCheckoutToken = null
        this.providerStatus = providerStatus.take(32)
        transitionTo(PaymentStatus.RECONCILIATION_REQUIRED, now)
        failedAt = null
    }

    /**
     * Webhook received — payment succeeded.
     * Safe to call multiple times (idempotent): AlreadyPaid guard in service.
     */
    fun markPaid(
        transactionId: String,
        providerStatus: String,
        callbackAudit: String,
        now: Instant,
    ) {
        require(status in setOf(PaymentStatus.PENDING, PaymentStatus.FAILED, PaymentStatus.RECONCILIATION_REQUIRED)) {
            "Only uncaptured payments can be marked PAID (current: $status)"
        }
        this.paymobTransactionId = transactionId
        this.providerStatus = providerStatus
        this.lastCallbackAudit = callbackAudit
        this.providerCheckoutToken = null
        transitionTo(PaymentStatus.PAID, now)
        this.paidAt = now
        this.revenueRecognisedAt = now
        this.failedAt = null
    }

    /** Webhook received — payment declined or timed out. */
    fun markFailed(
        transactionId: String?,
        providerStatus: String,
        callbackAudit: String,
        now: Instant,
    ) {
        require(status in setOf(PaymentStatus.PENDING, PaymentStatus.RECONCILIATION_REQUIRED)) {
            "Only uncaptured payments can be marked FAILED (current: $status)"
        }
        this.paymobTransactionId = transactionId
        this.providerStatus = providerStatus
        this.lastCallbackAudit = callbackAudit
        this.providerCheckoutToken = null
        transitionTo(PaymentStatus.FAILED, now)
        this.failedAt = now
    }

    /** Keeps a verified non-terminal callback for reconciliation without changing state. */
    fun recordPendingCallback(
        providerStatus: String,
        callbackAudit: String,
    ) {
        require(status in setOf(PaymentStatus.PENDING, PaymentStatus.RECONCILIATION_REQUIRED)) {
            "Only uncaptured payments accept pending callbacks"
        }
        this.providerStatus = providerStatus
        this.lastCallbackAudit = callbackAudit
    }

    /**
     * Provider captured money after the booking became terminal. Do not silently
     * confirm or reclaim inventory: make the financial exception explicit.
     */
    fun markReviewRequired(
        transactionId: String,
        providerStatus: String,
        callbackAudit: String,
        now: Instant,
    ) {
        require(status in setOf(PaymentStatus.PENDING, PaymentStatus.FAILED, PaymentStatus.RECONCILIATION_REQUIRED)) {
            "Only previously uncaptured payments can require review (current: $status)"
        }
        this.paymobTransactionId = transactionId
        this.providerStatus = providerStatus
        this.lastCallbackAudit = callbackAudit
        this.providerCheckoutToken = null
        transitionTo(PaymentStatus.REVIEW_REQUIRED, now)
        this.paidAt = now
        this.failedAt = null
    }

    /**
     * A callback reported a financial exception after this payment had
     * already been accepted as revenue (for example a partial refund that
     * cannot be represented as a full REFUNDED transition). Keep the original
     * revenue timestamp, make the exception visible to staff, and require a
     * deliberate reconciliation instead of returning an error forever to the
     * provider.
     *
     * Returns false for an exact callback replay so callers do not emit a
     * second alert or append a duplicate audit transition.
     */
    fun markCapturedReviewRequired(
        providerStatus: String,
        callbackAudit: String,
        now: Instant,
    ): Boolean {
        require(status == PaymentStatus.PAID || status == PaymentStatus.REVIEW_REQUIRED) {
            "Only captured payments can require captured-payment review (current: $status)"
        }
        if (status == PaymentStatus.REVIEW_REQUIRED && this.providerStatus == providerStatus && lastCallbackAudit == callbackAudit) {
            return false
        }
        this.providerStatus = providerStatus.take(32)
        this.lastCallbackAudit = callbackAudit
        this.providerCheckoutToken = null
        if (status == PaymentStatus.PAID) {
            transitionTo(PaymentStatus.REVIEW_REQUIRED, now)
        }
        return true
    }

    /**
     * A partial refund reported after a full refund is already terminal.
     * Preserve the truthful REFUNDED lifecycle state, but retain the latest
     * provider evidence so the webhook handler can emit a durable staff alert.
     */
    fun recordRefundAnomaly(
        providerStatus: String,
        callbackAudit: String,
    ): Boolean {
        require(status == PaymentStatus.REFUNDED) {
            "Only fully refunded payments can record a refund anomaly (current: $status)"
        }
        if (this.providerStatus == providerStatus && lastCallbackAudit == callbackAudit) return false
        this.providerStatus = providerStatus.take(32)
        this.lastCallbackAudit = callbackAudit
        this.providerCheckoutToken = null
        return true
    }

    /** Refund confirmed by Paymob. */
    fun markRefunded(
        providerStatus: String,
        callbackAudit: String,
        now: Instant,
    ) {
        require(status == PaymentStatus.PAID || status == PaymentStatus.REVIEW_REQUIRED) {
            "Only captured payments can be refunded (current: $status)"
        }
        this.providerStatus = providerStatus
        this.lastCallbackAudit = callbackAudit
        this.providerCheckoutToken = null
        transitionTo(PaymentStatus.REFUNDED, now)
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
                providerReference = "sts-${id.value}",
                paymobOrderId = null,
                paymobTransactionId = null,
                providerCheckoutToken = null,
                status = PaymentStatus.PENDING,
                providerStatus = null,
                lastCallbackAudit = null,
                createdAt = now,
                paidAt = null,
                failedAt = null,
                refundedAt = null,
                revenueRecognisedAt = null,
            ).also { it.unsavedTransitions += PaymentTransition(null, PaymentStatus.PENDING, null, now) }
    }
}
