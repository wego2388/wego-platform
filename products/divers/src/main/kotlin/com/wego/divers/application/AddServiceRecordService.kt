package com.wego.divers.application

import com.wego.divers.domain.EquipmentId
import com.wego.divers.domain.EquipmentServiceRecord
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class AddServiceRecordCommand(
    val equipmentId: EquipmentId,
    val servicedOn: LocalDate,
    val description: String,
    val performedBy: String?,
)

sealed interface AddServiceRecordResult {
    data class Added(
        val record: EquipmentServiceRecord,
    ) : AddServiceRecordResult

    data object EquipmentNotFound : AddServiceRecordResult
}

class AddServiceRecordService(
    private val equipmentRepository: EquipmentRepository,
    private val serviceRecordRepository: EquipmentServiceRecordRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun add(command: AddServiceRecordCommand): AddServiceRecordResult =
        transactionRunner.runInTransaction {
            if (equipmentRepository.findById(command.equipmentId) == null) return@runInTransaction AddServiceRecordResult.EquipmentNotFound

            val record =
                EquipmentServiceRecord(
                    id = UUID.randomUUID(),
                    equipmentId = command.equipmentId,
                    servicedOn = command.servicedOn,
                    description = command.description,
                    performedBy = command.performedBy,
                    createdAt = Instant.now(clock),
                )
            serviceRecordRepository.save(record)
            AddServiceRecordResult.Added(record)
        }
}
