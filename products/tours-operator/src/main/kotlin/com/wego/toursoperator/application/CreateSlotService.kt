package com.wego.toursoperator.application

import com.wego.toursoperator.domain.TimeSlot
import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.TourSlot
import com.wego.toursoperator.domain.TourSlotId
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

data class CreateSlotCommand(
    val tourId: TourId,
    val date: LocalDate,
    val timeSlot: TimeSlot,
    /** null means inherit from tour capacity; must be >= 1 if provided */
    val capacity: Int,
)

sealed interface CreateSlotResult {
    data class Success(
        val slot: TourSlot,
    ) : CreateSlotResult

    /** The tour referenced by tourId does not exist. */
    data object TourNotFound : CreateSlotResult

    /** A slot for this tour/date/time combination already exists. */
    data object AlreadyExists : CreateSlotResult
}

/**
 * Duplicate-check is optimistic (no lock before the DB unique constraint).
 * A concurrent insert race is caught by the unique constraint on
 * (tour_id, date, time_slot) and surfaced as AlreadyExists at the service
 * boundary rather than letting a DataIntegrityViolation propagate. The DB
 * constraint is the authoritative backstop; the pre-check here keeps the
 * happy-path clean.
 */
class CreateSlotService(
    private val slotRepository: TourSlotRepository,
    private val tourRepository: TourRepository,
    private val clock: Clock,
) {
    fun create(command: CreateSlotCommand): CreateSlotResult {
        // Verify tour exists — surfaces a distinct error rather than a FK violation.
        tourRepository.findById(command.tourId)
            ?: return CreateSlotResult.TourNotFound

        val existing = slotRepository.findByTourAndDate(command.tourId, command.date)
        if (existing.any { it.timeSlot == command.timeSlot }) {
            return CreateSlotResult.AlreadyExists
        }

        val slot =
            TourSlot(
                id = TourSlotId(UUID.randomUUID()),
                tourId = command.tourId,
                date = command.date,
                timeSlot = command.timeSlot,
                capacity = command.capacity,
                bookedCount = 0,
                isBlocked = false,
                createdAt = clock.instant(),
            )
        slotRepository.save(slot)
        return CreateSlotResult.Success(slot)
    }
}
