package com.wego.divers.application

import com.wego.divers.domain.Equipment
import com.wego.divers.domain.EquipmentId
import com.wego.divers.domain.EquipmentStatus
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface StartMaintenanceResult {
    data class Started(
        val equipment: Equipment,
    ) : StartMaintenanceResult

    data object NotFound : StartMaintenanceResult

    data object NotActive : StartMaintenanceResult
}

class StartMaintenanceService(
    private val equipmentRepository: EquipmentRepository,
    private val equipmentAuditRecorder: EquipmentAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun start(
        equipmentId: EquipmentId,
        actorUserId: UUID?,
        correlationId: UUID?,
    ): StartMaintenanceResult =
        transactionRunner.runInTransaction {
            val equipment = equipmentRepository.findById(equipmentId) ?: return@runInTransaction StartMaintenanceResult.NotFound
            if (equipment.status != EquipmentStatus.ACTIVE) return@runInTransaction StartMaintenanceResult.NotActive

            equipment.startMaintenance()
            equipmentRepository.save(equipment)
            equipmentAuditRecorder.recordStatusChanged(
                equipment.id,
                actorUserId,
                Instant.now(clock),
                EquipmentStatus.ACTIVE,
                EquipmentStatus.IN_MAINTENANCE,
                correlationId,
            )
            StartMaintenanceResult.Started(equipment)
        }
}
