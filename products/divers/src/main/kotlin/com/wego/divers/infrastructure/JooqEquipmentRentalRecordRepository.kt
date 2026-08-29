package com.wego.divers.infrastructure

import com.wego.divers.application.EquipmentRentalRecordRepository
import com.wego.divers.domain.EquipmentId
import com.wego.divers.domain.EquipmentRentalRecord
import com.wego.generated.jooq.tables.DiversEquipmentRentalRecord.DIVERS_EQUIPMENT_RENTAL_RECORD
import com.wego.generated.jooq.tables.records.DiversEquipmentRentalRecordRecord
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class JooqEquipmentRentalRecordRepository(
    private val dsl: DSLContext,
) : EquipmentRentalRecordRepository {
    @Transactional(readOnly = true)
    override fun findById(id: UUID): EquipmentRentalRecord? {
        val record =
            dsl.selectFrom(DIVERS_EQUIPMENT_RENTAL_RECORD).where(DIVERS_EQUIPMENT_RENTAL_RECORD.ID.eq(id)).fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional(readOnly = true)
    override fun findByEquipmentId(
        equipmentId: EquipmentId,
        limit: Int,
        offset: Int,
    ): List<EquipmentRentalRecord> =
        dsl
            .selectFrom(DIVERS_EQUIPMENT_RENTAL_RECORD)
            .where(DIVERS_EQUIPMENT_RENTAL_RECORD.EQUIPMENT_ID.eq(equipmentId.value))
            .orderBy(DIVERS_EQUIPMENT_RENTAL_RECORD.RENTED_ON.desc(), DIVERS_EQUIPMENT_RENTAL_RECORD.ID)
            .limit(limit)
            .offset(offset)
            .fetch()
            .map(::toDomain)

    @Transactional(readOnly = true)
    override fun findOpenByEquipmentId(equipmentId: EquipmentId): EquipmentRentalRecord? {
        val record =
            dsl
                .selectFrom(DIVERS_EQUIPMENT_RENTAL_RECORD)
                .where(DIVERS_EQUIPMENT_RENTAL_RECORD.EQUIPMENT_ID.eq(equipmentId.value))
                .and(DIVERS_EQUIPMENT_RENTAL_RECORD.RETURNED_ON.isNull())
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional
    override fun save(record: EquipmentRentalRecord) {
        dsl
            .insertInto(DIVERS_EQUIPMENT_RENTAL_RECORD)
            .set(DIVERS_EQUIPMENT_RENTAL_RECORD.ID, record.id)
            .set(DIVERS_EQUIPMENT_RENTAL_RECORD.EQUIPMENT_ID, record.equipmentId.value)
            .set(DIVERS_EQUIPMENT_RENTAL_RECORD.CUSTOMER_NAME, record.customerName)
            .set(DIVERS_EQUIPMENT_RENTAL_RECORD.RENTED_ON, record.rentedOn)
            .set(DIVERS_EQUIPMENT_RENTAL_RECORD.RETURNED_ON, record.returnedOn)
            .set(DIVERS_EQUIPMENT_RENTAL_RECORD.NOTES, record.notes)
            .set(DIVERS_EQUIPMENT_RENTAL_RECORD.CREATED_AT, toOffset(record.createdAt))
            .onConflict(DIVERS_EQUIPMENT_RENTAL_RECORD.ID)
            .doUpdate()
            .set(DIVERS_EQUIPMENT_RENTAL_RECORD.RETURNED_ON, record.returnedOn)
            .execute()
    }

    private fun toDomain(record: DiversEquipmentRentalRecordRecord): EquipmentRentalRecord =
        EquipmentRentalRecord(
            id = record.id,
            equipmentId = EquipmentId(record.equipmentId),
            customerName = record.customerName,
            rentedOn = record.rentedOn,
            returnedOn = record.returnedOn,
            notes = record.notes,
            createdAt = record.createdAt.toInstant(),
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
