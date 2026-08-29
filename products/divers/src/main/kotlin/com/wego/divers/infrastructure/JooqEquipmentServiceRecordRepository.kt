package com.wego.divers.infrastructure

import com.wego.divers.application.EquipmentServiceRecordRepository
import com.wego.divers.domain.EquipmentId
import com.wego.divers.domain.EquipmentServiceRecord
import com.wego.generated.jooq.tables.DiversEquipmentServiceRecord.DIVERS_EQUIPMENT_SERVICE_RECORD
import com.wego.generated.jooq.tables.records.DiversEquipmentServiceRecordRecord
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqEquipmentServiceRecordRepository(
    private val dsl: DSLContext,
) : EquipmentServiceRecordRepository {
    @Transactional(readOnly = true)
    override fun findByEquipmentId(
        equipmentId: EquipmentId,
        limit: Int,
        offset: Int,
    ): List<EquipmentServiceRecord> =
        dsl
            .selectFrom(DIVERS_EQUIPMENT_SERVICE_RECORD)
            .where(DIVERS_EQUIPMENT_SERVICE_RECORD.EQUIPMENT_ID.eq(equipmentId.value))
            .orderBy(DIVERS_EQUIPMENT_SERVICE_RECORD.SERVICED_ON.desc(), DIVERS_EQUIPMENT_SERVICE_RECORD.ID)
            .limit(limit)
            .offset(offset)
            .fetch()
            .map(::toDomain)

    @Transactional
    override fun save(record: EquipmentServiceRecord) {
        dsl
            .insertInto(DIVERS_EQUIPMENT_SERVICE_RECORD)
            .set(DIVERS_EQUIPMENT_SERVICE_RECORD.ID, record.id)
            .set(DIVERS_EQUIPMENT_SERVICE_RECORD.EQUIPMENT_ID, record.equipmentId.value)
            .set(DIVERS_EQUIPMENT_SERVICE_RECORD.SERVICED_ON, record.servicedOn)
            .set(DIVERS_EQUIPMENT_SERVICE_RECORD.DESCRIPTION, record.description)
            .set(DIVERS_EQUIPMENT_SERVICE_RECORD.PERFORMED_BY, record.performedBy)
            .set(DIVERS_EQUIPMENT_SERVICE_RECORD.CREATED_AT, toOffset(record.createdAt))
            .execute()
    }

    private fun toDomain(record: DiversEquipmentServiceRecordRecord): EquipmentServiceRecord =
        EquipmentServiceRecord(
            id = record.id,
            equipmentId = EquipmentId(record.equipmentId),
            servicedOn = record.servicedOn,
            description = record.description,
            performedBy = record.performedBy,
            createdAt = record.createdAt.toInstant(),
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
