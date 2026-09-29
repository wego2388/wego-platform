package com.wego.toursoperator.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant

class PaymentTest {

    private val bookingId = BookingId.generate()
    private val now = Instant.parse("2026-09-28T10:00:00Z")
    private val amountEur = BigDecimal("45.00")
    private val amountMinorUnits = 4500L

    private fun pendingPayment() = Payment.createPending(
        id = PaymentId.generate(),
        bookingId = bookingId,
        amountEur = amountEur,
        amountMinorUnits = amountMinorUnits,
        currencyCode = "EUR",
        now = now,
    )

    // ── createPending ──────────────────────────────────────────────────────

    @Test
    fun `createPending sets PENDING status with no timestamps`() {
        val p = pendingPayment()
        assertEquals(PaymentStatus.PENDING, p.status)
        assertNull(p.paidAt)
        assertNull(p.failedAt)
        assertNull(p.refundedAt)
        assertNull(p.paymobOrderId)
        assertNull(p.paymobTransactionId)
    }

    @Test
    fun `createPending rejects zero amount`() {
        assertThrows<IllegalArgumentException> {
            Payment.createPending(
                id = PaymentId.generate(),
                bookingId = bookingId,
                amountEur = BigDecimal.ZERO,
                amountMinorUnits = 0L,
                currencyCode = "EUR",
                now = now,
            )
        }
    }

    @Test
    fun `createPending rejects invalid currency code`() {
        assertThrows<IllegalArgumentException> {
            Payment.createPending(
                id = PaymentId.generate(),
                bookingId = bookingId,
                amountEur = amountEur,
                amountMinorUnits = amountMinorUnits,
                currencyCode = "eu",   // lowercase — invalid
                now = now,
            )
        }
    }

    // ── assignPaymobCheckout ───────────────────────────────────────────────

    @Test
    fun `assignPaymobCheckout sets the order ID and token`() {
        val p = pendingPayment()
        p.assignPaymobCheckout("ORDER-123", "TOKEN-123")
        assertEquals("ORDER-123", p.paymobOrderId)
        assertEquals("TOKEN-123", p.providerCheckoutToken)
    }

    @Test
    fun `assignPaymobCheckout rejects blank ID`() {
        val p = pendingPayment()
        assertThrows<IllegalArgumentException> { p.assignPaymobCheckout("", "TOKEN") }
    }

    @Test
    fun `assignPaymobCheckout rejects double assignment`() {
        val p = pendingPayment()
        p.assignPaymobCheckout("ORDER-123", "TOKEN-123")
        assertThrows<IllegalArgumentException> { p.assignPaymobCheckout("ORDER-456", "TOKEN-456") }
    }

    // ── markPaid ───────────────────────────────────────────────────────────

    @Test
    fun `markPaid transitions PENDING to PAID`() {
        val p = pendingPayment()
        p.markPaid("TXN-1", "APPROVED", "{}", now)
        assertEquals(PaymentStatus.PAID, p.status)
        assertEquals("TXN-1", p.paymobTransactionId)
        assertNotNull(p.paidAt)
        assertNull(p.failedAt)
    }

    @Test
    fun `markPaid rejects non-PENDING payment`() {
        val p = pendingPayment()
        p.markPaid("TXN-1", "APPROVED", "{}", now)
        assertThrows<IllegalArgumentException> {
            p.markPaid("TXN-2", "APPROVED", "{}", now)
        }
    }

    // ── markFailed ─────────────────────────────────────────────────────────

    @Test
    fun `markFailed transitions PENDING to FAILED`() {
        val p = pendingPayment()
        p.markFailed("TXN-DECLINE", "DECLINED", "{}", now)
        assertEquals(PaymentStatus.FAILED, p.status)
        assertNotNull(p.failedAt)
        assertNull(p.paidAt)
    }

    @Test
    fun `markFailed accepts null transactionId for timeout case`() {
        val p = pendingPayment()
        p.markFailed(null, "TIMEOUT", "{}", now)
        assertEquals(PaymentStatus.FAILED, p.status)
        assertNull(p.paymobTransactionId)
    }

    @Test
    fun `reconciliation preserves provider identity and blocks automatic retry`() {
        val p = pendingPayment()
        val stableReference = p.providerReference
        p.assignPaymobCheckout("ORDER-1", "TOKEN-1")
        p.markReconciliationRequired("CREATE_OUTCOME_UNKNOWN")

        assertEquals(PaymentStatus.RECONCILIATION_REQUIRED, p.status)
        assertNull(p.failedAt)
        assertEquals("ORDER-1", p.paymobOrderId)
        assertNull(p.paymobTransactionId)
        assertNull(p.providerCheckoutToken)
        assertEquals(stableReference, p.providerReference)
    }

    @Test
    fun `reconciled payment can accept a late verified success`() {
        val p = pendingPayment()
        p.assignPaymobCheckout("ORDER-1", "TOKEN-1")
        p.markReconciliationRequired("CREATE_OUTCOME_UNKNOWN")

        p.markPaid("TXN-LATE", "APPROVED", "{}", now)

        assertEquals(PaymentStatus.PAID, p.status)
        assertEquals("ORDER-1", p.paymobOrderId)
        assertEquals("TXN-LATE", p.paymobTransactionId)
        assertNotNull(p.paidAt)
    }

    @Test
    fun `late captured payment becomes REVIEW_REQUIRED`() {
        val p = pendingPayment()
        p.markReviewRequired("TXN-LATE", "SUCCESS", "{}", now)

        assertEquals(PaymentStatus.REVIEW_REQUIRED, p.status)
        assertNotNull(p.paidAt)
        assertNull(p.failedAt)
    }

    // ── markRefunded ───────────────────────────────────────────────────────

    @Test
    fun `markRefunded transitions PAID to REFUNDED`() {
        val p = pendingPayment()
        p.markPaid("TXN-1", "APPROVED", "{}", now)
        p.markRefunded("{refund}", now.plusSeconds(60))
        assertEquals(PaymentStatus.REFUNDED, p.status)
        assertNotNull(p.refundedAt)
        assertNotNull(p.paidAt)
    }

    @Test
    fun `markRefunded rejects non-PAID payment`() {
        val p = pendingPayment()
        assertThrows<IllegalArgumentException> {
            p.markRefunded("{}", now)
        }
    }
}
