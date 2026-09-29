package com.wego.toursoperator.application

import com.wego.toursoperator.domain.TourId

sealed interface SetTourActiveResult {
    data object Success : SetTourActiveResult

    data object NotFound : SetTourActiveResult
}

/**
 * Wraps activate/deactivate in a single transaction so the row lock acquired
 * by findByIdForUpdate() is held until save() commits. Without this wrapper
 * a concurrent activate/deactivate race could silently overwrite the state
 * of the other writer.
 */
class SetTourActiveService(
    private val tourRepository: TourRepository,
    private val transactionRunner: TransactionRunner,
) {
    fun activate(id: TourId): SetTourActiveResult =
        transactionRunner.runInTransaction {
            val tour = tourRepository.findByIdForUpdate(id) ?: return@runInTransaction SetTourActiveResult.NotFound
            tour.activate()
            tourRepository.save(tour)
            SetTourActiveResult.Success
        }

    fun deactivate(id: TourId): SetTourActiveResult =
        transactionRunner.runInTransaction {
            val tour = tourRepository.findByIdForUpdate(id) ?: return@runInTransaction SetTourActiveResult.NotFound
            tour.deactivate()
            tourRepository.save(tour)
            SetTourActiveResult.Success
        }
}
