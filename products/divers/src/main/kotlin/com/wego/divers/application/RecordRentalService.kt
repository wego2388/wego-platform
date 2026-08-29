package com.wego.divers.application

import com.wego.divers.domain.EquipmentId
import com.wego.divers.domain.EquipmentRentalRecord
import com.wego.divers.domain.EquipmentStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class RecordRentalCommand(
    val equipmentId: EquipmentId,
    val customerName: String,
    val rentedOn: LocalDate,
    val notes: String?,
)

sealed interface RecordRentalResult {
    data class Recorded(
        val record: EquipmentRentalRecord,
    ) : RecordRentalResult

    data object EquipmentNotFound : RecordRentalResult

    data object EquipmentNotAvailable : RecordRentalResult

    data object AlreadyOut : RecordRentalResult
}

class RecordRentalService(
    private val equipmentRepository: EquipmentRepository,
    private val rentalRecordRepository: EquipmentRentalRecordRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun record(command: RecordRentalCommand): RecordRentalResult =
        transactionRunner.runInTransaction {
            val equipment =
                equipmentRepository.findById(command.equipmentId) ?: return@runInTransaction RecordRentalResult.EquipmentNotFound
            if (equipment.status != EquipmentStatus.ACTIVE) return@runInTransaction RecordRentalResult.EquipmentNotAvailable
            if (rentalRecordRepository.findOpenByEquipmentId(command.equipmentId) != null) {
                return@runInTransaction RecordRentalResult.AlreadyOut
            }

            val record =
                EquipmentRentalRecord(
                    id = UUID.randomUUID(),
                    equipmentId = command.equipmentId,
                    customerName = command.customerName,
                    rentedOn = command.rentedOn,
                    returnedOn = null,
                    notes = command.notes,
                    createdAt = Instant.now(clock),
                )
            rentalRecordRepository.save(record)
            RecordRentalResult.Recorded(record)
        }
}
