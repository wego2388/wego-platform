package com.wego.divers.application

import com.wego.divers.domain.EquipmentId
import com.wego.divers.domain.EquipmentRentalRecord
import java.time.LocalDate

sealed interface RecordRentalReturnResult {
    data class Returned(
        val record: EquipmentRentalRecord,
    ) : RecordRentalReturnResult

    data object NoOpenRental : RecordRentalReturnResult
}

class RecordRentalReturnService(
    private val rentalRecordRepository: EquipmentRentalRecordRepository,
    private val transactionRunner: TransactionRunner,
) {
    fun returnItem(
        equipmentId: EquipmentId,
        returnedOn: LocalDate,
    ): RecordRentalReturnResult =
        transactionRunner.runInTransaction {
            val open =
                rentalRecordRepository.findOpenByEquipmentId(equipmentId) ?: return@runInTransaction RecordRentalReturnResult.NoOpenRental

            val returned = open.copy(returnedOn = returnedOn)
            rentalRecordRepository.save(returned)
            RecordRentalReturnResult.Returned(returned)
        }
}
