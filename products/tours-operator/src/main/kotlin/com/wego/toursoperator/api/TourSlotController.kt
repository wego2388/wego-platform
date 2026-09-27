package com.wego.toursoperator.api

import com.wego.toursoperator.application.TourSlotQueryService
import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.TourSlot
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@Validated
@RestController
@RequestMapping("/api/v1/tours-operator/tours/{tourId}/slots")
class TourSlotController(
    private val tourSlotQueryService: TourSlotQueryService,
) {
    /** Public — used by the booking card calendar to shade available dates. */
    @GetMapping
    fun listAvailable(
        @PathVariable tourId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): List<TourSlotResponse> =
        tourSlotQueryService
            .findAvailable(TourId(tourId), from, to)
            .map { it.toResponse() }

    /** Public — used when the user picks a specific date to show available time slots. */
    @GetMapping("/by-date")
    fun listByDate(
        @PathVariable tourId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    ): List<TourSlotResponse> =
        tourSlotQueryService
            .findByTourAndDate(TourId(tourId), date)
            .map { it.toResponse() }
}

internal fun TourSlot.toResponse() =
    TourSlotResponse(
        id = id.value,
        tourId = tourId.value,
        date = date,
        timeSlot = timeSlot,
        capacity = capacity,
        bookedCount = bookedCount,
        available = available,
        isBlocked = isBlocked,
    )
