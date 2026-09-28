package com.wego.toursoperator.application

import com.wego.toursoperator.domain.CancellationPolicy
import com.wego.toursoperator.domain.TimeSlot
import com.wego.toursoperator.domain.Tour
import com.wego.toursoperator.domain.TourCategory
import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.TourType
import java.time.Clock
import java.util.UUID

data class CreateTourCommand(
    val slug: String,
    val category: TourCategory,
    val durationText: String,
    val priceAdultCents: Long,
    val priceChildCents: Long?,
    val capacity: Int,
    val availableTimeSlots: Set<TimeSlot>,
    val sortOrder: Int,
    val nameEn: String?,
    val tourType: TourType,
    val imageUrl: String?,
    val cancellationPolicy: CancellationPolicy,
    val createdByUserId: UUID?,
    val pricingNote: String? = null,
)

sealed interface CreateTourResult {
    data class Success(val tour: Tour) : CreateTourResult
    data object SlugAlreadyExists : CreateTourResult
}

class CreateTourService(
    private val tourRepository: TourRepository,
    private val clock: Clock,
) {
    fun create(command: CreateTourCommand): CreateTourResult {
        if (tourRepository.existsBySlug(command.slug)) {
            return CreateTourResult.SlugAlreadyExists
        }
        val tour =
            Tour.create(
                id = TourId(UUID.randomUUID()),
                slug = command.slug,
                category = command.category,
                durationText = command.durationText,
                priceAdultCents = command.priceAdultCents,
                priceChildCents = command.priceChildCents,
                capacity = command.capacity,
                availableTimeSlots = command.availableTimeSlots,
                sortOrder = command.sortOrder,
                createdByUserId = command.createdByUserId,
                now = clock.instant(),
                nameEn = command.nameEn,
                tourType = command.tourType,
                imageUrl = command.imageUrl,
                cancellationPolicy = command.cancellationPolicy,
                pricingNote = command.pricingNote,
            )
        tourRepository.save(tour)
        return CreateTourResult.Success(tour)
    }
}
