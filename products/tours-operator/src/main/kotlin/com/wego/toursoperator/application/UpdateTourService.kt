package com.wego.toursoperator.application

import com.wego.toursoperator.domain.CancellationPolicy
import com.wego.toursoperator.domain.TimeSlot
import com.wego.toursoperator.domain.Tour
import com.wego.toursoperator.domain.TourCategory
import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.TourType

data class UpdateTourCommand(
    val id: TourId,
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
    val pricingNote: String? = null,
)

sealed interface UpdateTourResult {
    data object Success : UpdateTourResult

    data object NotFound : UpdateTourResult
}

/**
 * Wraps the full read-modify-write cycle in a single transaction so the
 * row lock acquired by findByIdForUpdate() is held until save() completes.
 * Without this, a concurrent update could overwrite the locked row between
 * the repository read returning and the subsequent save call.
 */
class UpdateTourService(
    private val tourRepository: TourRepository,
    private val transactionRunner: TransactionRunner,
) {
    fun update(command: UpdateTourCommand): UpdateTourResult =
        transactionRunner.runInTransaction {
            val tour = tourRepository.findByIdForUpdate(command.id) ?: return@runInTransaction UpdateTourResult.NotFound
            val updated =
                Tour(
                    id = tour.id,
                    slug = tour.slug,
                    category = command.category,
                    durationText = command.durationText,
                    priceAdultCents = command.priceAdultCents,
                    priceChildCents = command.priceChildCents,
                    capacity = command.capacity,
                    availableTimeSlots = command.availableTimeSlots,
                    sortOrder = command.sortOrder,
                    isActive = tour.isActive,
                    createdAt = tour.createdAt,
                    createdByUserId = tour.createdByUserId,
                    nameEn = command.nameEn,
                    tourType = command.tourType,
                    imageUrl = command.imageUrl,
                    cancellationPolicy = command.cancellationPolicy,
                    pricingNote = command.pricingNote,
                )
            tourRepository.save(updated)
            UpdateTourResult.Success
        }
}
