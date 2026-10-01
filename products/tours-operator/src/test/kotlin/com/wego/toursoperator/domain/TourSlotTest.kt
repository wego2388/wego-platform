package com.wego.toursoperator.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class TourSlotTest {
    private fun slot(
        capacity: Int,
        bookedCount: Int = 0,
        isBlocked: Boolean = false,
    ): TourSlot =
        TourSlot(
            id = TourSlotId(UUID.randomUUID()),
            tourId = TourId(UUID.randomUUID()),
            date = LocalDate.of(2027, 6, 15),
            timeSlot = TimeSlot.MORNING,
            capacity = capacity,
            bookedCount = bookedCount,
            isBlocked = isBlocked,
            createdAt = Instant.now(),
        )

    // ── reserve(1) happy path ─────────────────────────────────────────────────────

    @Test
    fun `reserve returns true and increments bookedCount`() {
        val s = slot(capacity = 5, bookedCount = 0)
        val result = s.reserve(1)
        assertTrue(result)
        assertEquals(1, s.bookedCount)
        assertEquals(4, s.available)
    }

    @Test
    fun `reserve on last available seat succeeds and leaves zero available`() {
        val s = slot(capacity = 3, bookedCount = 2)
        val result = s.reserve(1)
        assertTrue(result)
        assertEquals(3, s.bookedCount)
        assertEquals(0, s.available)
    }

    // ── book() at capacity ────────────────────────────────────────────────────

    @Test
    fun `reserve on fully booked slot returns false without mutating bookedCount`() {
        val s = slot(capacity = 2, bookedCount = 2)
        val result = s.reserve(1)
        assertFalse(result)
        assertEquals(2, s.bookedCount) // unchanged
        assertEquals(0, s.available)
    }

    @Test
    fun `bookedCount never exceeds capacity after repeated reserve calls`() {
        val s = slot(capacity = 1)
        s.reserve(1) // succeeds
        s.reserve(1) // rejected
        s.reserve(1) // rejected
        assertEquals(1, s.bookedCount)
    }

    // ── book() on blocked slot ────────────────────────────────────────────────

    @Test
    fun `reserve on blocked slot returns false without mutating`() {
        val s = slot(capacity = 10, bookedCount = 0, isBlocked = true)
        val result = s.reserve(1)
        assertFalse(result)
        assertEquals(0, s.bookedCount)
    }

    @Test
    fun `reserve on blocked-and-full slot returns false`() {
        val s = slot(capacity = 2, bookedCount = 2, isBlocked = true)
        assertFalse(s.reserve(1))
    }

    // ── release() ──────────────────────────────────────────────────────────

    @Test
    fun `release decrements bookedCount`() {
        val s = slot(capacity = 5, bookedCount = 3)
        s.release(1)
        assertEquals(2, s.bookedCount)
        assertEquals(3, s.available)
    }

    @Test
    fun `release clamps at zero and does not go negative`() {
        val s = slot(capacity = 5, bookedCount = 0)
        s.release(1)
        assertEquals(0, s.bookedCount)
    }

    // ── places are guests, not bookings ─────────────────────────────────────

    @Test
    fun `reserve takes one place per guest`() {
        val s = slot(capacity = 10, bookedCount = 3)
        assertTrue(s.reserve(5))
        assertEquals(8, s.bookedCount)
        assertEquals(2, s.available)
    }

    @Test
    fun `reserve refuses a group larger than the places left without mutating`() {
        val s = slot(capacity = 10, bookedCount = 7)
        assertFalse(s.reserve(4))
        assertEquals(7, s.bookedCount)
        assertTrue(s.reserve(3))
        assertEquals(0, s.available)
    }

    @Test
    fun `release returns a group's places and clamps at zero`() {
        val s = slot(capacity = 10, bookedCount = 6)
        s.release(4)
        assertEquals(2, s.bookedCount)
        s.release(5)
        assertEquals(0, s.bookedCount)
    }

    @Test
    fun `reserve and release reject non-positive seat counts`() {
        val s = slot(capacity = 10)
        assertThrows<IllegalArgumentException> { s.reserve(0) }
        assertThrows<IllegalArgumentException> { s.release(0) }
    }

    // ── block / unblock ───────────────────────────────────────────────────────

    @Test
    fun `block sets isBlocked to true`() {
        val s = slot(capacity = 5)
        s.block()
        assertTrue(s.isBlocked)
    }

    @Test
    fun `unblock sets isBlocked to false`() {
        val s = slot(capacity = 5, isBlocked = true)
        s.unblock()
        assertFalse(s.isBlocked)
    }

    // ── domain invariants ─────────────────────────────────────────────────────

    @Test
    fun `capacity below 1 is rejected`() {
        assertThrows<IllegalArgumentException> {
            slot(capacity = 0)
        }
    }

    @Test
    fun `bookedCount exceeding capacity is rejected`() {
        assertThrows<IllegalArgumentException> {
            slot(capacity = 3, bookedCount = 4)
        }
    }

    @Test
    fun `negative bookedCount is rejected`() {
        assertThrows<IllegalArgumentException> {
            slot(capacity = 5, bookedCount = -1)
        }
    }

    // ── available computed property ───────────────────────────────────────────

    @Test
    fun `available returns capacity minus bookedCount`() {
        val s = slot(capacity = 10, bookedCount = 3)
        assertEquals(7, s.available)
    }

    @Test
    fun `available is zero when fully booked`() {
        val s = slot(capacity = 4, bookedCount = 4)
        assertEquals(0, s.available)
    }
}
