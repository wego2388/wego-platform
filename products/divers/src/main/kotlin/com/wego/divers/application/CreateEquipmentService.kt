package com.wego.divers.application
import com.wego.divers.domain.Equipment
import com.wego.divers.domain.EquipmentId
import com.wego.divers.domain.EquipmentType
import com.wego.transaction.TransactionRunner
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class CreateEquipmentCommand(
    val equipmentType: EquipmentType,
    val label: String,
    val qrCode: String,
    val itemSize: String?,
    val serialNumber: String?,
    val createdByUserId: UUID?,
    val correlationId: UUID?,
)

sealed interface CreateEquipmentResult {
    data class Created(
        val equipment: Equipment,
    ) : CreateEquipmentResult

    data object DuplicateQrCode : CreateEquipmentResult
}

class CreateEquipmentService(
    private val equipmentRepository: EquipmentRepository,
    private val equipmentAuditRecorder: EquipmentAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun create(command: CreateEquipmentCommand): CreateEquipmentResult =
        transactionRunner.runInTransaction {
            if (equipmentRepository.findByQrCode(command.qrCode) != null) {
                return@runInTransaction CreateEquipmentResult.DuplicateQrCode
            }

            val now = Instant.now(clock)
            val equipment =
                Equipment.create(
                    id = EquipmentId.generate(),
                    equipmentType = command.equipmentType,
                    label = command.label,
                    qrCode = command.qrCode,
                    itemSize = command.itemSize,
                    serialNumber = command.serialNumber,
                    createdByUserId = command.createdByUserId,
                    now = now,
                )
            equipmentRepository.save(equipment)
            equipmentAuditRecorder.recordEquipmentCreated(equipment.id, command.createdByUserId, now, command.correlationId)
            CreateEquipmentResult.Created(equipment)
        }
}
