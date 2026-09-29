package com.wego.toursoperator.application

import com.wego.toursoperator.domain.Booking
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.BookingStatus
import com.wego.toursoperator.domain.TourId
import java.time.Instant
import java.time.LocalDate

interface BookingRepository {
    fun findById(id: BookingId): Booking?

    fun findByIdForUpdate(id: BookingId): Booking?

    fun findByReference(reference: String): Booking?

    /**
     * Public lookup: customer retrieves their own booking by reference + phone.
     * No authentication required — the combination is treated as a shared secret.
     */
    fun findByReferenceAndPhone(
        reference: String,
        phone: String,
    ): Booking?

    fun findAll(
        tourId: TourId?,
        status: BookingStatus?,
        date: LocalDate?,
        limit: Int,
        offset: Int,
    ): List<Booking>

    /** NEW bookings whose payment window has elapsed, using the caller's injected clock. */
    fun findNewCreatedBefore(cutoff: Instant): List<Booking>

    /** Count bookings for a slot — used by the dashboard today-schedule view. */
    fun countBySlotId(slotId: com.wego.toursoperator.domain.TourSlotId): Int

    /**
     * Returns the next available sequence number for the STR-YYYY-NNNN
     * reference, scoped to the given year. Must be called inside a
     * transaction with the slot row already locked to avoid gaps or
     * duplicates under concurrent creation.
     */
    fun nextReferenceSequence(year: Int): Long

    fun save(booking: Booking)
}
