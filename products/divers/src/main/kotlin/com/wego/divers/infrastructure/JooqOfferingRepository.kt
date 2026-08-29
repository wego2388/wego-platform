package com.wego.divers.infrastructure

import com.wego.divers.application.OfferingRepository
import com.wego.divers.domain.Money
import com.wego.divers.domain.Offering
import com.wego.divers.domain.OfferingId
import com.wego.divers.domain.OfferingStatus
import com.wego.divers.domain.OfferingType
import com.wego.divers.domain.PricingBasis
import com.wego.generated.jooq.tables.DiversOffering.DIVERS_OFFERING
import com.wego.generated.jooq.tables.records.DiversOfferingRecord
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqOfferingRepository(
    private val dsl: DSLContext,
) : OfferingRepository {
    @Transactional(readOnly = true)
    override fun findById(id: OfferingId): Offering? {
        val record = dsl.selectFrom(DIVERS_OFFERING).where(DIVERS_OFFERING.ID.eq(id.value)).fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional
    override fun findByIdForUpdate(id: OfferingId): Offering? {
        val record =
            dsl
                .selectFrom(DIVERS_OFFERING)
                .where(DIVERS_OFFERING.ID.eq(id.value))
                .forUpdate()
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional(readOnly = true)
    override fun findAll(
        offeringType: OfferingType?,
        status: OfferingStatus?,
        limit: Int,
        offset: Int,
    ): List<Offering> {
        var condition = DSL.noCondition()
        if (offeringType != null) {
            condition = condition.and(DIVERS_OFFERING.OFFERING_TYPE.eq(offeringType.name))
        }
        if (status != null) {
            condition = condition.and(DIVERS_OFFERING.STATUS.eq(status.name))
        }
        return dsl
            .selectFrom(DIVERS_OFFERING)
            .where(condition)
            // ID as a tie-breaker: STARTS_ON alone is not unique, and offset
            // pagination without a deterministic full ordering can skip or
            // duplicate rows across two separate page queries whenever any
            // rows share a date — not just under concurrent writes.
            .orderBy(DIVERS_OFFERING.STARTS_ON, DIVERS_OFFERING.ID)
            .limit(limit)
            .offset(offset)
            .fetch()
            .map(::toDomain)
    }

    @Transactional
    override fun save(offering: Offering) {
        dsl
            .insertInto(DIVERS_OFFERING)
            .set(DIVERS_OFFERING.ID, offering.id.value)
            .set(DIVERS_OFFERING.OFFERING_TYPE, offering.offeringType.name)
            .set(DIVERS_OFFERING.TITLE, offering.title)
            .set(DIVERS_OFFERING.DESCRIPTION, offering.description)
            .set(DIVERS_OFFERING.STARTS_ON, offering.startsOn)
            .set(DIVERS_OFFERING.ENDS_ON, offering.endsOn)
            .set(DIVERS_OFFERING.CAPACITY, offering.capacity)
            .set(DIVERS_OFFERING.PRICING_BASIS, offering.pricingBasis.name)
            .set(DIVERS_OFFERING.UNIT_PRICE, offering.unitPrice.amount)
            .set(DIVERS_OFFERING.CURRENCY_CODE, offering.unitPrice.currencyCode)
            .set(DIVERS_OFFERING.STATUS, offering.status.name)
            .set(DIVERS_OFFERING.CREATED_BY_USER_ID, offering.createdByUserId)
            .set(DIVERS_OFFERING.CREATED_AT, toOffset(offering.createdAt))
            .set(DIVERS_OFFERING.CLOSED_AT, offering.closedAt?.let(::toOffset))
            .onConflict(DIVERS_OFFERING.ID)
            .doUpdate()
            .set(DIVERS_OFFERING.STATUS, offering.status.name)
            .set(DIVERS_OFFERING.CLOSED_AT, offering.closedAt?.let(::toOffset))
            .execute()
    }

    private fun toDomain(record: DiversOfferingRecord): Offering =
        Offering(
            id = OfferingId(record.id),
            offeringType = OfferingType.valueOf(record.offeringType),
            title = record.title,
            description = record.description,
            startsOn = record.startsOn,
            endsOn = record.endsOn,
            capacity = record.capacity,
            pricingBasis = PricingBasis.valueOf(record.pricingBasis),
            unitPrice = Money(record.unitPrice, record.currencyCode),
            status = OfferingStatus.valueOf(record.status),
            createdByUserId = record.createdByUserId,
            createdAt = record.createdAt.toInstant(),
            closedAt = record.closedAt?.toInstant(),
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
