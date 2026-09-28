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

    // ── assignPaymobOrder ──────────────────────────────────────────────────

    @Test
    fun `assignPaymobOrder sets the order ID`() {
        val p = pendingPayment()
        p.assignPaymobOrder("ORDER-123")
        assertEquals("ORDER-123", p.paymobOrderId)
    }

    @Test
    fun `assignPaymobOrder rejects blank ID`() {
        val p = pendingPayment()
        assertThrows<IllegalArgumentException> { p.assignPaymobOrder("") }
    }

    @Test
    fun `assignPaymobOrder rejects double assignment`() {
        val p = pendingPayment()
        p.assignPaymobOrder("ORDER-123")
        assertThrows<IllegalArgumentException> { p.assignPaymobOrder("ORDER-456") }
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

    // ── markRefunded ───────────────────────────────────────────────────────

    @Test
    fun `markRefunded transitions PAID to REFUNDED`() {
        val p = pendingPayment()
        p.markPaid("TXN-1", "APPROVED", "{}", now)
        p.markRefunded("{refund}", now.plusSeconds(60))
        assertEquals(PaymentStatus.REFUNDED, p.status)
        assertNotNull(p.refundedAt)
    }

    @Test
    fun `markRefunded rejects non-PAID payment`() {
        val p = pendingPayment()
        assertThrows<IllegalArgumentException> {
            p.markRefunded("{}", now)
        }
    }
}
