package com.wego.divers.infrastructure

import com.wego.divers.application.OfferingBoatCharterLinkRepository
import com.wego.divers.domain.BoatCharterId
import com.wego.divers.domain.OfferingBoatCharterLink
import com.wego.divers.domain.OfferingId
import com.wego.generated.jooq.tables.DiversOfferingBoatCharter.DIVERS_OFFERING_BOAT_CHARTER
import com.wego.generated.jooq.tables.records.DiversOfferingBoatCharterRecord
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqOfferingBoatCharterLinkRepository(
    private val dsl: DSLContext,
) : OfferingBoatCharterLinkRepository {
    @Transactional(readOnly = true)
    override fun findByOfferingId(offeringId: OfferingId): OfferingBoatCharterLink? {
        val record =
            dsl
                .selectFrom(DIVERS_OFFERING_BOAT_CHARTER)
                .where(DIVERS_OFFERING_BOAT_CHARTER.OFFERING_ID.eq(offeringId.value))
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional(readOnly = true)
    override fun findByBoatCharterId(boatCharterId: BoatCharterId): List<OfferingBoatCharterLink> =
        dsl
            .selectFrom(DIVERS_OFFERING_BOAT_CHARTER)
            .where(DIVERS_OFFERING_BOAT_CHARTER.BOAT_CHARTER_ID.eq(boatCharterId.value))
            .fetch()
            .map(::toDomain)

    @Transactional
    override fun save(link: OfferingBoatCharterLink) {
        dsl
            .insertInto(DIVERS_OFFERING_BOAT_CHARTER)
            .set(DIVERS_OFFERING_BOAT_CHARTER.OFFERING_ID, link.offeringId.value)
            .set(DIVERS_OFFERING_BOAT_CHARTER.BOAT_CHARTER_ID, link.boatCharterId.value)
            .set(DIVERS_OFFERING_BOAT_CHARTER.LINKED_AT, toOffset(link.linkedAt))
            .onConflict(DIVERS_OFFERING_BOAT_CHARTER.OFFERING_ID)
            .doUpdate()
            .set(DIVERS_OFFERING_BOAT_CHARTER.BOAT_CHARTER_ID, link.boatCharterId.value)
            .set(DIVERS_OFFERING_BOAT_CHARTER.LINKED_AT, toOffset(link.linkedAt))
            .execute()
    }

    @Transactional
    override fun delete(offeringId: OfferingId) {
        dsl.deleteFrom(DIVERS_OFFERING_BOAT_CHARTER).where(DIVERS_OFFERING_BOAT_CHARTER.OFFERING_ID.eq(offeringId.value)).execute()
    }

    private fun toDomain(record: DiversOfferingBoatCharterRecord): OfferingBoatCharterLink =
        OfferingBoatCharterLink(
            offeringId = OfferingId(record.offeringId),
            boatCharterId = BoatCharterId(record.boatCharterId),
            linkedAt = record.linkedAt.toInstant(),
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
