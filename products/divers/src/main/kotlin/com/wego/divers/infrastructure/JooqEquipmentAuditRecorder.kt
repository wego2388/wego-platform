package com.wego.divers.infrastructure

import com.wego.divers.application.EquipmentAuditRecorder
import com.wego.divers.domain.EquipmentId
import com.wego.divers.domain.EquipmentStatus
import com.wego.generated.jooq.tables.DiversEquipmentAuditEvent.DIVERS_EQUIPMENT_AUDIT_EVENT
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Component
class JooqEquipmentAuditRecorder(
    private val dsl: DSLContext,
) : EquipmentAuditRecorder {
    @Transactional
    override fun recordEquipmentCreated(
        equipmentId: EquipmentId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    ) = insert(equipmentId, "EQUIPMENT_CREATED", actorUserId, occurredAt, null, null, correlationId)

    @Transactional
    override fun recordStatusChanged(
        equipmentId: EquipmentId,
        actorUserId: UUID?,
        occurredAt: Instant,
        fromStatus: EquipmentStatus,
        toStatus: EquipmentStatus,
        correlationId: UUID?,
    ) = insert(equipmentId, "EQUIPMENT_STATUS_CHANGED", actorUserId, occurredAt, fromStatus.name, toStatus.name, correlationId)

    private fun insert(
        equipmentId: EquipmentId,
        eventType: String,
        actorUserId: UUID?,
        occurredAt: Instant,
        fromStatus: String?,
        toStatus: String?,
        correlationId: UUID?,
    ) {
        dsl
            .insertInto(DIVERS_EQUIPMENT_AUDIT_EVENT)
            .set(DIVERS_EQUIPMENT_AUDIT_EVENT.ID, UUID.randomUUID())
            .set(DIVERS_EQUIPMENT_AUDIT_EVENT.EQUIPMENT_ID, equipmentId.value)
            .set(DIVERS_EQUIPMENT_AUDIT_EVENT.OCCURRED_AT, OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
            .set(DIVERS_EQUIPMENT_AUDIT_EVENT.EVENT_TYPE, eventType)
            .set(DIVERS_EQUIPMENT_AUDIT_EVENT.ACTOR_USER_ID, actorUserId)
            .set(DIVERS_EQUIPMENT_AUDIT_EVENT.FROM_STATUS, fromStatus)
            .set(DIVERS_EQUIPMENT_AUDIT_EVENT.TO_STATUS, toStatus)
            .set(DIVERS_EQUIPMENT_AUDIT_EVENT.CORRELATION_ID, correlationId)
            .execute()
    }
}
