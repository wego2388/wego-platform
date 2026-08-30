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
    // Row-locked: `withUpdatedDetails` copies the read `status` unchanged into the saved row, so an
    // unlocked read here can race RetireEquipmentService/StartMaintenanceService — a plain label/size
    // edit that read the row just before a concurrent retire/maintenance commit would overwrite that
    // terminal status back to the stale pre-transition value. See EquipmentRepository.findByIdForUpdate.
    fun update(command: UpdateEquipmentCommand): UpdateEquipmentResult =
        transactionRunner.runInTransaction {
            val existing =
                equipmentRepository.findByIdForUpdate(command.equipmentId) ?: return@runInTransaction UpdateEquipmentResult.NotFound
            val updated = existing.withUpdatedDetails(command.label, command.itemSize, command.serialNumber)
            equipmentRepository.save(updated)
            UpdateEquipmentResult.Updated(updated)
        }
}
