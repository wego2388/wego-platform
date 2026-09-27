package com.wego.toursoperator.domain

import java.time.Instant
import java.time.LocalDate

/**
 * A single bookable slot for a tour on a specific date and time window.
 *
 * Capacity here overrides the tour's default capacity for this specific date —
 * the operator can reduce capacity for a slot (e.g. one guide is away) or
 * increase it. bookedCount is a running total incremented on CONFIRMED bookings
 * and decremented on CANCELLED bookings; it is the authoritative count for the
 * capacity check and must only be mutated through [book] and [releaseOne].
 *
 * isBlocked allows the operator to close a slot without touching the tour's
 * active status. A blocked slot does not accept new bookings but existing
 * confirmed bookings are unaffected.
 */
class TourSlot(
    val id: TourSlotId,
    val tourId: TourId,
    val date: LocalDate,
    val timeSlot: TimeSlot,
    val capacity: Int,
    bookedCount: Int,
    isBlocked: Boolean,
    val createdAt: Instant,
) {
    var bookedCount: Int = bookedCount
        private set

    var isBlocked: Boolean = isBlocked
        private set

    val available: Int get() = (capacity - bookedCount).coerceAtLeast(0)

    init {
        require(capacity >= 1) { "Slot capacity must be at least 1" }
        require(bookedCount >= 0) { "Booked count must not be negative" }
        require(bookedCount <= capacity) { "Booked count ($bookedCount) must not exceed capacity ($capacity)" }
    }

    /**
     * Reserves one seat. Returns false (without mutating) when the slot is
     * blocked or fully booked so the caller can surface a domain-level result
     * rather than relying on a database constraint violation.
     */
    fun book(): Boolean {
        if (isBlocked || bookedCount >= capacity) return false
        bookedCount++
        return true
    }

    /**
     * Releases one seat — called when a booking is cancelled.
     * Clamps at zero defensively; a negative count is never meaningful.
     */
    fun releaseOne() {
        if (bookedCount > 0) bookedCount--
    }

    fun block() {
        isBlocked = true
    }

    fun unblock() {
        isBlocked = false
    }
}
