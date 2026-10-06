package com.wego.toursoperator.application

import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.TourSlot
import com.wego.toursoperator.domain.TourSlotId
import java.time.LocalDate

interface TourSlotRepository {
    fun findById(id: TourSlotId): TourSlot?

    /**
     * Row-lock for booking — prevents two concurrent bookings on the same
     * slot from both passing the capacity check.
     */
    fun findByIdForUpdate(id: TourSlotId): TourSlot?

    fun findByTourAndDate(
        tourId: TourId,
        date: LocalDate,
    ): List<TourSlot>

    /**
     * Find available (non-blocked, non-full) slots for a tour
     * in a date range — used by the public availability calendar.
     */
    fun findAvailable(
        tourId: TourId,
        from: LocalDate,
        to: LocalDate,
    ): List<TourSlot>

    /** Every slot of [date] across all tours (the day's departures). */
    fun findByDate(date: LocalDate): List<TourSlot>

    fun save(slot: TourSlot)
}
