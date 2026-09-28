package com.wego.toursoperator.api

import com.wego.toursoperator.application.CreateSlotCommand
import com.wego.toursoperator.application.CreateSlotResult
import com.wego.toursoperator.application.CreateSlotService
import com.wego.toursoperator.application.SetSlotBlockedResult
import com.wego.toursoperator.application.SetSlotBlockedService
import com.wego.toursoperator.application.TourSlotQueryService
import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.TourSlot
import com.wego.toursoperator.domain.TourSlotId
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.time.LocalDate
import java.util.UUID

@Validated
@RestController
@RequestMapping("/api/v1/tours-operator")
class TourSlotController(
    private val tourSlotQueryService: TourSlotQueryService,
    private val createSlotService: CreateSlotService,
    private val setSlotBlockedService: SetSlotBlockedService,
) {
    // ── Public endpoints ─────────────────────────────────────────────────────

    @GetMapping("/tours/{tourId}/slots")
    fun listAvailable(
        @PathVariable tourId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): List<TourSlotResponse> =
        tourSlotQueryService.findAvailable(TourId(tourId), from, to).map { it.toResponse() }

    @GetMapping("/tours/{tourId}/slots/by-date")
    fun listByDate(
        @PathVariable tourId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
    ): List<TourSlotResponse> =
        tourSlotQueryService.findByTourAndDate(TourId(tourId), date).map { it.toResponse() }

    // ── Staff endpoints — /staff/tours/{tourId}/slots/** ────────────────────

    @PostMapping("/staff/tours/{tourId}/slots")
    @PreAuthorize("hasAuthority('tours-operator.slot:manage')")
    fun createSlot(
        @PathVariable tourId: UUID,
        @Valid @RequestBody request: CreateSlotRequest,
    ): ResponseEntity<Any> {
        val command = CreateSlotCommand(
            tourId = TourId(tourId),
            date = request.date,
            timeSlot = request.timeSlot,
            capacity = request.capacity,
        )
        return when (val result = createSlotService.create(command)) {
            is CreateSlotResult.Success -> {
                val uri = ServletUriComponentsBuilder
                    .fromCurrentContextPath()
                    .path("/api/v1/tours-operator/staff/tours/{tourId}/slots/{id}")
                    .buildAndExpand(tourId, result.slot.id.value)
                    .toUri()
                ResponseEntity.created(uri).body(result.slot.toResponse())
            }
            CreateSlotResult.TourNotFound ->
                ResponseEntity.notFound().build()
            CreateSlotResult.AlreadyExists ->
                ResponseEntity.status(409).body(ErrorResponse("slot_already_exists"))
        }
    }

    @PatchMapping("/staff/tours/{tourId}/slots/{slotId}/block")
    @PreAuthorize("hasAuthority('tours-operator.slot:manage')")
    fun blockSlot(
        @PathVariable tourId: UUID,
        @PathVariable slotId: UUID,
    ): ResponseEntity<Any> =
        when (setSlotBlockedService.block(TourId(tourId), TourSlotId(slotId))) {
            SetSlotBlockedResult.Success -> ResponseEntity.noContent().build()
            SetSlotBlockedResult.NotFound -> ResponseEntity.notFound().build()
            SetSlotBlockedResult.WrongTour -> ResponseEntity.notFound().build()
        }

    @PatchMapping("/staff/tours/{tourId}/slots/{slotId}/unblock")
    @PreAuthorize("hasAuthority('tours-operator.slot:manage')")
    fun unblockSlot(
        @PathVariable tourId: UUID,
        @PathVariable slotId: UUID,
    ): ResponseEntity<Any> =
        when (setSlotBlockedService.unblock(TourId(tourId), TourSlotId(slotId))) {
            SetSlotBlockedResult.Success -> ResponseEntity.noContent().build()
            SetSlotBlockedResult.NotFound -> ResponseEntity.notFound().build()
            SetSlotBlockedResult.WrongTour -> ResponseEntity.notFound().build()
        }
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
