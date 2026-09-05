package com.wego.divers.application
import com.wego.divers.domain.Equipment
import com.wego.divers.domain.EquipmentId
import com.wego.divers.domain.EquipmentStatus
import com.wego.transaction.TransactionRunner
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface CompleteMaintenanceResult {
    data class Completed(
        val equipment: Equipment,
    ) : CompleteMaintenanceResult

    data object NotFound : CompleteMaintenanceResult

    data object NotInMaintenance : CompleteMaintenanceResult
}

class CompleteMaintenanceService(
    private val equipmentRepository: EquipmentRepository,
    private val equipmentAuditRecorder: EquipmentAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun complete(
        equipmentId: EquipmentId,
        actorUserId: UUID?,
        correlationId: UUID?,
    ): CompleteMaintenanceResult =
        transactionRunner.runInTransaction {
            val equipment = equipmentRepository.findByIdForUpdate(equipmentId) ?: return@runInTransaction CompleteMaintenanceResult.NotFound
            if (equipment.status != EquipmentStatus.IN_MAINTENANCE) return@runInTransaction CompleteMaintenanceResult.NotInMaintenance

            equipment.completeMaintenance()
            equipmentRepository.save(equipment)
            equipmentAuditRecorder.recordStatusChanged(
                equipment.id,
                actorUserId,
                Instant.now(clock),
                EquipmentStatus.IN_MAINTENANCE,
                EquipmentStatus.ACTIVE,
                correlationId,
            )
            CompleteMaintenanceResult.Completed(equipment)
        }
}
