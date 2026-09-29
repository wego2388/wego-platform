package com.wego.toursoperator.application

import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.TourSlotId

sealed interface SetSlotBlockedResult {
    data object Success : SetSlotBlockedResult

    data object NotFound : SetSlotBlockedResult

    /** The slot exists but belongs to a different tour than the path's tourId. */
    data object WrongTour : SetSlotBlockedResult
}

/**
 * Wraps block/unblock in a single transaction so the row lock is held until
 * save() commits. Also enforces slot-ownership: a slot that does not belong
 * to the given tourId is rejected with WrongTour, preventing cross-tour
 * mutations via /tour-A/slots/slot-B/block.
 */
class SetSlotBlockedService(
    private val slotRepository: TourSlotRepository,
    private val transactionRunner: TransactionRunner,
) {
    fun block(
        tourId: TourId,
        slotId: TourSlotId,
    ): SetSlotBlockedResult =
        transactionRunner.runInTransaction {
            val slot = slotRepository.findByIdForUpdate(slotId) ?: return@runInTransaction SetSlotBlockedResult.NotFound
            if (slot.tourId != tourId) return@runInTransaction SetSlotBlockedResult.WrongTour
            slot.block()
            slotRepository.save(slot)
            SetSlotBlockedResult.Success
        }

    fun unblock(
        tourId: TourId,
        slotId: TourSlotId,
    ): SetSlotBlockedResult =
        transactionRunner.runInTransaction {
            val slot = slotRepository.findByIdForUpdate(slotId) ?: return@runInTransaction SetSlotBlockedResult.NotFound
            if (slot.tourId != tourId) return@runInTransaction SetSlotBlockedResult.WrongTour
            slot.unblock()
            slotRepository.save(slot)
            SetSlotBlockedResult.Success
        }
}
