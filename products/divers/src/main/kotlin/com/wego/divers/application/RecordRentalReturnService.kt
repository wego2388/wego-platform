package com.wego.divers.application
import com.wego.divers.domain.EquipmentId
import com.wego.divers.domain.EquipmentRentalRecord
import com.wego.transaction.TransactionRunner
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
    // Row-locked: two concurrent returns for the same item both read the same "open" row under an
    // unlocked findOpenByEquipmentId, and the second `save()` (an UPSERT by id) simply overwrites the
    // first return's committed `returnedOn` with its own value instead of correctly seeing no open
    // rental. With the lock, the row's own returnedOn (now non-null) makes the second locked read
    // correctly fail the `RETURNED_ON.isNull()` predicate and return NoOpenRental.
    fun returnItem(
        equipmentId: EquipmentId,
        returnedOn: LocalDate,
    ): RecordRentalReturnResult =
        transactionRunner.runInTransaction {
            val open =
                rentalRecordRepository.findOpenByEquipmentIdForUpdate(equipmentId)
                    ?: return@runInTransaction RecordRentalReturnResult.NoOpenRental

            val returned = open.copy(returnedOn = returnedOn)
            rentalRecordRepository.save(returned)
            RecordRentalReturnResult.Returned(returned)
        }
}
