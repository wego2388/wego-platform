package com.wego.divers.application

import com.wego.divers.domain.Equipment
import com.wego.divers.domain.EquipmentId

data class UpdateEquipmentCommand(
    val equipmentId: EquipmentId,
    val label: String,
    val itemSize: String?,
    val serialNumber: String?,
)

sealed interface UpdateEquipmentResult {
    data class Updated(
        val equipment: Equipment,
    ) : UpdateEquipmentResult

    data object NotFound : UpdateEquipmentResult
}

class UpdateEquipmentService(
    private val equipmentRepository: EquipmentRepository,
    private val transactionRunner: TransactionRunner,
) {
    fun update(command: UpdateEquipmentCommand): UpdateEquipmentResult =
        transactionRunner.runInTransaction {
            val existing = equipmentRepository.findById(command.equipmentId) ?: return@runInTransaction UpdateEquipmentResult.NotFound
            val updated = existing.withUpdatedDetails(command.label, command.itemSize, command.serialNumber)
            equipmentRepository.save(updated)
            UpdateEquipmentResult.Updated(updated)
        }
}
