package com.wego.divers.application

import com.wego.divers.domain.BoatCharter
import com.wego.divers.domain.BoatCharterId
import java.time.LocalDate

data class UpdateBoatCharterCommand(
    val charterId: BoatCharterId,
    val boatName: String,
    val licensedCapacity: Int,
    val startsOn: LocalDate,
    val endsOn: LocalDate?,
    val notes: String?,
)

sealed interface UpdateBoatCharterResult {
    data class Updated(
        val charter: BoatCharter,
    ) : UpdateBoatCharterResult

    data object NotFound : UpdateBoatCharterResult

    /** Would leave at least one currently linked offering claiming more seats than the new, lower capacity allows. */
    data object CapacityBelowLinkedOfferings : UpdateBoatCharterResult
}

class UpdateBoatCharterService(
    private val boatCharterRepository: BoatCharterRepository,
    private val linkRepository: OfferingBoatCharterLinkRepository,
    private val offeringRepository: OfferingRepository,
    private val transactionRunner: TransactionRunner,
) {
    fun update(command: UpdateBoatCharterCommand): UpdateBoatCharterResult =
        transactionRunner.runInTransaction {
            val existing =
                boatCharterRepository.findByIdForUpdate(command.charterId) ?: return@runInTransaction UpdateBoatCharterResult.NotFound

            if (command.licensedCapacity < existing.licensedCapacity) {
                val stillExceeds =
                    linkRepository.findByBoatCharterId(command.charterId).any { link ->
                        val capacity = offeringRepository.findById(link.offeringId)?.capacity
                        capacity != null && capacity > command.licensedCapacity
                    }
                if (stillExceeds) return@runInTransaction UpdateBoatCharterResult.CapacityBelowLinkedOfferings
            }

            val updated =
                existing.withUpdatedDetails(command.boatName, command.licensedCapacity, command.startsOn, command.endsOn, command.notes)
            boatCharterRepository.save(updated)
            UpdateBoatCharterResult.Updated(updated)
        }
}
