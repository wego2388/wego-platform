package com.wego.divers.application

import com.wego.divers.domain.EquipmentId
import com.wego.divers.domain.EquipmentStatus
import java.time.Instant
import java.util.UUID

interface EquipmentAuditRecorder {
    fun recordEquipmentCreated(
        equipmentId: EquipmentId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordStatusChanged(
        equipmentId: EquipmentId,
        actorUserId: UUID?,
        occurredAt: Instant,
        fromStatus: EquipmentStatus,
        toStatus: EquipmentStatus,
        correlationId: UUID?,
    )
}
