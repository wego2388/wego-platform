package com.wego.toursoperator.application

import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.TourSlot
import java.time.LocalDate

class TourSlotQueryService(
    private val slotRepository: TourSlotRepository,
) {
    fun findAvailable(
        tourId: TourId,
        from: LocalDate,
        to: LocalDate,
    ): List<TourSlot> = slotRepository.findAvailable(tourId, from, to)

    fun findByTourAndDate(
        tourId: TourId,
        date: LocalDate,
    ): List<TourSlot> = slotRepository.findByTourAndDate(tourId, date)
}
