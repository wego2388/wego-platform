package com.wego.divers.application

import com.wego.divers.domain.Equipment
import com.wego.divers.domain.EquipmentId
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface RetireEquipmentResult {
    data class Retired(
        val equipment: Equipment,
    ) : RetireEquipmentResult

    data object NotFound : RetireEquipmentResult

    data object AlreadyRetired : RetireEquipmentResult

    /** Retiring an item currently out with a customer would silently orphan that open rental. */
    data object HasOpenRental : RetireEquipmentResult
}

class RetireEquipmentService(
    private val equipmentRepository: EquipmentRepository,
    private val rentalRecordRepository: EquipmentRentalRecordRepository,
    private val equipmentAuditRecorder: EquipmentAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun retire(
        equipmentId: EquipmentId,
        actorUserId: UUID?,
        correlationId: UUID?,
    ): RetireEquipmentResult =
        transactionRunner.runInTransaction {
            val equipment = equipmentRepository.findByIdForUpdate(equipmentId) ?: return@runInTransaction RetireEquipmentResult.NotFound
            if (equipment.isRetired) return@runInTransaction RetireEquipmentResult.AlreadyRetired
            if (rentalRecordRepository.findOpenByEquipmentId(equipmentId) !=
                null
            ) {
                return@runInTransaction RetireEquipmentResult.HasOpenRental
            }

            val fromStatus = equipment.status
            val now = Instant.now(clock)
            equipment.retire(now)
            equipmentRepository.save(equipment)
            equipmentAuditRecorder.recordStatusChanged(equipment.id, actorUserId, now, fromStatus, equipment.status, correlationId)
            RetireEquipmentResult.Retired(equipment)
        }
}
