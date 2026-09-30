package com.wego.toursoperator.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class BookingTest {
    private val now: Instant = Instant.parse("2026-09-27T00:00:00Z")
    private val later: Instant = Instant.parse("2026-09-27T01:00:00Z")

    private fun pricing(
        adults: Int = 2,
        children: Int = 0,
    ): BookingPricing {
        val priceAdult = Money(BigDecimal("35.00"))
        val priceChild = if (children > 0) Money(BigDecimal("17.50")) else null
        return BookingPricing.compute(adults, children, priceAdult, priceChild)
    }

    private fun newBooking(reference: String = "STR-2026-1"): Booking =
        Booking.createNew(
            id = BookingId(UUID.randomUUID()),
            reference = reference,
            tourId = TourId(UUID.randomUUID()),
            slotId = TourSlotId(UUID.randomUUID()),
            tourDate = LocalDate.of(2027, 6, 15),
            timeSlot = TimeSlot.MORNING,
            pricing = pricing(),
            customer =
                CustomerContact(
                    fullName = "Ahmed Hassan",
                    phone = "+201234567890",
                    nationality = "EG",
                    email = null,
                ),
            hotelName = "Hilton Sharm Dreams",
            hotelRoom = "312",
            specialRequests = null,
            locale = "en",
            now = now,
        )

    // ── confirm() ─────────────────────────────────────────────────────────────

    @Test
    fun `confirm transitions NEW to CONFIRMED and sets confirmedAt`() {
        val b = newBooking()
        b.confirm(later)
        assertEquals(BookingStatus.CONFIRMED, b.status)
        assertEquals(later, b.confirmedAt)
    }

    @Test
    fun `confirm on CONFIRMED booking throws`() {
        val b = newBooking()
        b.confirm(later)
        assertThrows<IllegalArgumentException> { b.confirm(later) }
    }

    @Test
    fun `confirm on CANCELLED booking throws`() {
        val b = newBooking()
        b.cancel(later, "changed mind")
        assertThrows<IllegalArgumentException> { b.confirm(later) }
    }

    @Test
    fun `confirm on EXPIRED booking throws`() {
        val b = newBooking()
        b.expire(later)
        assertThrows<IllegalArgumentException> { b.confirm(later) }
    }

    // ── cancel() ──────────────────────────────────────────────────────────────

    @Test
    fun `cancel on NEW booking transitions to CANCELLED`() {
        val b = newBooking()
        b.cancel(later, "Customer request")
        assertEquals(BookingStatus.CANCELLED, b.status)
        assertEquals(later, b.cancelledAt)
        assertEquals("Customer request", b.cancellationReason)
    }

    @Test
    fun `cancel on CONFIRMED booking transitions to CANCELLED`() {
        val b = newBooking()
        b.confirm(later)
        b.cancel(later, "Operator cancelled")
        assertEquals(BookingStatus.CANCELLED, b.status)
        assertNotNull(b.cancelledAt)
    }

    @Test
    fun `cancel requires non-blank reason`() {
        val b = newBooking()
        assertThrows<IllegalArgumentException> { b.cancel(later, "") }
        assertThrows<IllegalArgumentException> { b.cancel(later, "   ") }
    }

    @Test
    fun `cancel on COMPLETED booking throws`() {
        val b = newBooking()
        b.confirm(later)
        b.complete(later)
        assertThrows<IllegalArgumentException> { b.cancel(later, "too late") }
    }

    @Test
    fun `cancel on EXPIRED booking throws`() {
        val b = newBooking()
        b.expire(later)
        assertThrows<IllegalArgumentException> { b.cancel(later, "already expired") }
    }

    @Test
    fun `cancel on already CANCELLED booking throws`() {
        val b = newBooking()
        b.cancel(later, "first cancel")
        assertThrows<IllegalArgumentException> { b.cancel(later, "second cancel") }
    }

    // ── complete() ────────────────────────────────────────────────────────────

    @Test
    fun `complete transitions CONFIRMED to COMPLETED`() {
        val b = newBooking()
        b.confirm(later)
        b.complete(later)
        assertEquals(BookingStatus.COMPLETED, b.status)
        assertEquals(later, b.completedAt)
    }

    @Test
    fun `complete on NEW booking throws`() {
        val b = newBooking()
        assertThrows<IllegalArgumentException> { b.complete(later) }
    }

    @Test
    fun `complete on CANCELLED booking throws`() {
        val b = newBooking()
        b.cancel(later, "reason")
        assertThrows<IllegalArgumentException> { b.complete(later) }
    }

    // ── expire() ──────────────────────────────────────────────────────────────

    @Test
    fun `expire transitions NEW to EXPIRED`() {
        val b = newBooking()
        b.expire(later)
        assertEquals(BookingStatus.EXPIRED, b.status)
        assertEquals(later, b.expiredAt)
    }

    @Test
    fun `expire on CONFIRMED booking throws`() {
        val b = newBooking()
        b.confirm(later)
        assertThrows<IllegalArgumentException> { b.expire(later) }
    }

    @Test
    fun `expire on CANCELLED booking throws`() {
        val b = newBooking()
        b.cancel(later, "reason")
        assertThrows<IllegalArgumentException> { b.expire(later) }
    }

    // ── reference format ──────────────────────────────────────────────────────

    @Test
    fun `valid STR-YYYY-N references are accepted`() {
        // single digit
        newBooking("STR-2026-1")
        // four digits
        newBooking("STR-2026-4821")
        // large sequence
        newBooking("STR-2030-99999")
    }

    @Test
    fun `reference without STR prefix is rejected`() {
        assertThrows<IllegalArgumentException> { newBooking("REF-2026-1") }
    }

    @Test
    fun `reference with wrong separator is rejected`() {
        assertThrows<IllegalArgumentException> { newBooking("STR/2026/1") }
    }

    @Test
    fun `reference with non-numeric year is rejected`() {
        assertThrows<IllegalArgumentException> { newBooking("STR-ABCD-1") }
    }

    @Test
    fun `reference with no sequence number is rejected`() {
        assertThrows<IllegalArgumentException> { newBooking("STR-2026-") }
    }

    @Test
    fun `empty reference is rejected`() {
        assertThrows<IllegalArgumentException> { newBooking("") }
    }

    // ── immutability of confirmedAt / cancelledAt on NEW ─────────────────────

    @Test
    fun `NEW booking has null confirmedAt cancelledAt completedAt expiredAt`() {
        val b = newBooking()
        assertEquals(BookingStatus.NEW, b.status)
        assertNull(b.confirmedAt)
        assertNull(b.cancelledAt)
        assertNull(b.cancellationReason)
        assertNull(b.completedAt)
        assertNull(b.expiredAt)
    }

    // ── pricing ───────────────────────────────────────────────────────────────

    @Test
    fun `pricing snapshot is preserved on booking`() {
        val b = newBooking()
        assertEquals(2, b.pricing.adultsCount)
        assertEquals(0, b.pricing.childrenCount)
        assertEquals(BigDecimal("35.00"), b.pricing.priceAdult.amount)
        assertEquals(BigDecimal("70.00"), b.pricing.totalEur.amount)
    }

    @Test
    fun `pricing with children computes correct total`() {
        val p = pricing(adults = 2, children = 1)
        // 2 × 35.00 + 1 × 17.50 = 87.50
        assertEquals(BigDecimal("87.50"), p.totalEur.amount)
    }
}
