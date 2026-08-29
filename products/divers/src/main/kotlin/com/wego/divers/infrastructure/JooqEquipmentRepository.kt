package com.wego.divers.infrastructure

import com.wego.divers.application.EquipmentRepository
import com.wego.divers.domain.Equipment
import com.wego.divers.domain.EquipmentId
import com.wego.divers.domain.EquipmentStatus
import com.wego.divers.domain.EquipmentType
import com.wego.generated.jooq.tables.DiversEquipment.DIVERS_EQUIPMENT
import com.wego.generated.jooq.tables.records.DiversEquipmentRecord
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqEquipmentRepository(
    private val dsl: DSLContext,
) : EquipmentRepository {
    @Transactional(readOnly = true)
    override fun findById(id: EquipmentId): Equipment? {
        val record = dsl.selectFrom(DIVERS_EQUIPMENT).where(DIVERS_EQUIPMENT.ID.eq(id.value)).fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional
    override fun findByIdForUpdate(id: EquipmentId): Equipment? {
        val record =
            dsl
                .selectFrom(DIVERS_EQUIPMENT)
                .where(DIVERS_EQUIPMENT.ID.eq(id.value))
                .forUpdate()
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional(readOnly = true)
    override fun findByQrCode(qrCode: String): Equipment? {
        val record = dsl.selectFrom(DIVERS_EQUIPMENT).where(DIVERS_EQUIPMENT.QR_CODE.eq(qrCode)).fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional(readOnly = true)
    override fun findAll(
        equipmentType: EquipmentType?,
        status: EquipmentStatus?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<Equipment> {
        var condition = DSL.noCondition()
        if (equipmentType != null) {
            condition = condition.and(DIVERS_EQUIPMENT.EQUIPMENT_TYPE.eq(equipmentType.name))
        }
        if (status != null) {
            condition = condition.and(DIVERS_EQUIPMENT.STATUS.eq(status.name))
        }
        if (!search.isNullOrBlank()) {
            condition =
                condition.and(
                    DIVERS_EQUIPMENT.LABEL
                        .containsIgnoreCase(search.trim())
                        .or(DIVERS_EQUIPMENT.QR_CODE.containsIgnoreCase(search.trim()))
                        .or(DIVERS_EQUIPMENT.SERIAL_NUMBER.containsIgnoreCase(search.trim())),
                )
        }
        return dsl
            .selectFrom(DIVERS_EQUIPMENT)
            .where(condition)
            .orderBy(DIVERS_EQUIPMENT.LABEL, DIVERS_EQUIPMENT.ID)
            .limit(limit)
            .offset(offset)
            .fetch()
            .map(::toDomain)
    }

    @Transactional
    override fun save(equipment: Equipment) {
        dsl
            .insertInto(DIVERS_EQUIPMENT)
            .set(DIVERS_EQUIPMENT.ID, equipment.id.value)
            .set(DIVERS_EQUIPMENT.EQUIPMENT_TYPE, equipment.equipmentType.name)
            .set(DIVERS_EQUIPMENT.LABEL, equipment.label)
            .set(DIVERS_EQUIPMENT.QR_CODE, equipment.qrCode)
            .set(DIVERS_EQUIPMENT.ITEM_SIZE, equipment.itemSize)
            .set(DIVERS_EQUIPMENT.SERIAL_NUMBER, equipment.serialNumber)
            .set(DIVERS_EQUIPMENT.STATUS, equipment.status.name)
            .set(DIVERS_EQUIPMENT.CREATED_BY_USER_ID, equipment.createdByUserId)
            .set(DIVERS_EQUIPMENT.CREATED_AT, toOffset(equipment.createdAt))
            .set(DIVERS_EQUIPMENT.RETIRED_AT, equipment.retiredAt?.let(::toOffset))
            .onConflict(DIVERS_EQUIPMENT.ID)
            .doUpdate()
            .set(DIVERS_EQUIPMENT.LABEL, equipment.label)
            .set(DIVERS_EQUIPMENT.ITEM_SIZE, equipment.itemSize)
            .set(DIVERS_EQUIPMENT.SERIAL_NUMBER, equipment.serialNumber)
            .set(DIVERS_EQUIPMENT.STATUS, equipment.status.name)
            .set(DIVERS_EQUIPMENT.RETIRED_AT, equipment.retiredAt?.let(::toOffset))
            .execute()
    }

    private fun toDomain(record: DiversEquipmentRecord): Equipment =
        Equipment(
            id = EquipmentId(record.id),
            equipmentType = EquipmentType.valueOf(record.equipmentType),
            label = record.label,
            qrCode = record.qrCode,
            itemSize = record.itemSize,
            serialNumber = record.serialNumber,
            status = EquipmentStatus.valueOf(record.status),
            createdByUserId = record.createdByUserId,
            createdAt = record.createdAt.toInstant(),
            retiredAt = record.retiredAt?.toInstant(),
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
