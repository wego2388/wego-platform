package com.wego.divers.infrastructure

import com.wego.divers.application.BoatCharterRepository
import com.wego.divers.domain.BoatCharter
import com.wego.divers.domain.BoatCharterId
import com.wego.divers.domain.CharterStatus
import com.wego.divers.domain.CharterType
import com.wego.generated.jooq.tables.DiversBoatCharter.DIVERS_BOAT_CHARTER
import com.wego.generated.jooq.tables.records.DiversBoatCharterRecord
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqBoatCharterRepository(
    private val dsl: DSLContext,
) : BoatCharterRepository {
    @Transactional(readOnly = true)
    override fun findById(id: BoatCharterId): BoatCharter? {
        val record = dsl.selectFrom(DIVERS_BOAT_CHARTER).where(DIVERS_BOAT_CHARTER.ID.eq(id.value)).fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional
    override fun findByIdForUpdate(id: BoatCharterId): BoatCharter? {
        val record =
            dsl
                .selectFrom(DIVERS_BOAT_CHARTER)
                .where(DIVERS_BOAT_CHARTER.ID.eq(id.value))
                .forUpdate()
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional(readOnly = true)
    override fun findAll(
        charterType: CharterType?,
        status: CharterStatus?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<BoatCharter> {
        var condition = DSL.noCondition()
        if (charterType != null) {
            condition = condition.and(DIVERS_BOAT_CHARTER.CHARTER_TYPE.eq(charterType.name))
        }
        if (status != null) {
            condition = condition.and(DIVERS_BOAT_CHARTER.STATUS.eq(status.name))
        }
        if (!search.isNullOrBlank()) {
            condition = condition.and(DIVERS_BOAT_CHARTER.BOAT_NAME.containsIgnoreCase(search.trim()))
        }
        return dsl
            .selectFrom(DIVERS_BOAT_CHARTER)
            .where(condition)
            .orderBy(DIVERS_BOAT_CHARTER.STARTS_ON.desc(), DIVERS_BOAT_CHARTER.ID)
            .limit(limit)
            .offset(offset)
            .fetch()
            .map(::toDomain)
    }

    @Transactional
    override fun save(charter: BoatCharter) {
        dsl
            .insertInto(DIVERS_BOAT_CHARTER)
            .set(DIVERS_BOAT_CHARTER.ID, charter.id.value)
            .set(DIVERS_BOAT_CHARTER.BOAT_NAME, charter.boatName)
            .set(DIVERS_BOAT_CHARTER.CHARTER_TYPE, charter.charterType.name)
            .set(DIVERS_BOAT_CHARTER.LICENSED_CAPACITY, charter.licensedCapacity)
            .set(DIVERS_BOAT_CHARTER.STARTS_ON, charter.startsOn)
            .set(DIVERS_BOAT_CHARTER.ENDS_ON, charter.endsOn)
            .set(DIVERS_BOAT_CHARTER.NOTES, charter.notes)
            .set(DIVERS_BOAT_CHARTER.STATUS, charter.status.name)
            .set(DIVERS_BOAT_CHARTER.CREATED_BY_USER_ID, charter.createdByUserId)
            .set(DIVERS_BOAT_CHARTER.CREATED_AT, toOffset(charter.createdAt))
            .set(DIVERS_BOAT_CHARTER.ENDED_AT, charter.endedAt?.let(::toOffset))
            .onConflict(DIVERS_BOAT_CHARTER.ID)
            .doUpdate()
            .set(DIVERS_BOAT_CHARTER.BOAT_NAME, charter.boatName)
            .set(DIVERS_BOAT_CHARTER.LICENSED_CAPACITY, charter.licensedCapacity)
            .set(DIVERS_BOAT_CHARTER.STARTS_ON, charter.startsOn)
            .set(DIVERS_BOAT_CHARTER.ENDS_ON, charter.endsOn)
            .set(DIVERS_BOAT_CHARTER.NOTES, charter.notes)
            .set(DIVERS_BOAT_CHARTER.STATUS, charter.status.name)
            .set(DIVERS_BOAT_CHARTER.ENDED_AT, charter.endedAt?.let(::toOffset))
            .execute()
    }

    private fun toDomain(record: DiversBoatCharterRecord): BoatCharter =
        BoatCharter(
            id = BoatCharterId(record.id),
            boatName = record.boatName,
            charterType = CharterType.valueOf(record.charterType),
            licensedCapacity = record.licensedCapacity,
            startsOn = record.startsOn,
            endsOn = record.endsOn,
            notes = record.notes,
            status = CharterStatus.valueOf(record.status),
            createdByUserId = record.createdByUserId,
            createdAt = record.createdAt.toInstant(),
            endedAt = record.endedAt?.toInstant(),
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
