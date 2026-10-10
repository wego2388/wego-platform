package com.wego.travelmarketplace.infrastructure

import com.wego.generated.jooq.tables.TravelSalesControl.TRAVEL_SALES_CONTROL
import com.wego.generated.jooq.tables.TravelSalesControlEvent.TRAVEL_SALES_CONTROL_EVENT
import com.wego.generated.jooq.tables.records.TravelSalesControlRecord
import com.wego.travelmarketplace.application.SalesControlRepository
import com.wego.travelmarketplace.domain.SalesControl
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqSalesControlRepository(
    private val dsl: DSLContext,
) : SalesControlRepository {
    private val t = TRAVEL_SALES_CONTROL

    @Transactional(readOnly = true)
    override fun current(): SalesControl = requireNotNull(dsl.selectFrom(t).where(t.ID.eq(1.toShort())).fetchOne()).toControl()

    @Transactional(propagation = Propagation.MANDATORY)
    override fun lockForRequest(): SalesControl =
        requireNotNull(
            dsl
                .selectFrom(t)
                .where(t.ID.eq(1.toShort()))
                .forShare()
                .fetchOne(),
        ).toControl()

    @Transactional(propagation = Propagation.MANDATORY)
    override fun lockForUpdate(): SalesControl =
        requireNotNull(
            dsl
                .selectFrom(t)
                .where(t.ID.eq(1.toShort()))
                .forUpdate()
                .fetchOne(),
        ).toControl()

    @Transactional(propagation = Propagation.MANDATORY)
    override fun saveAndAudit(
        before: SalesControl,
        after: SalesControl,
    ) {
        val at = OffsetDateTime.ofInstant(requireNotNull(after.updatedAt), ZoneOffset.UTC)
        check(
            dsl
                .update(t)
                .set(t.REQUESTS_PAUSED, after.requestsPaused)
                .set(t.VERSION, after.version)
                .set(t.REASON, after.reason)
                .set(t.UPDATED_BY_USER_ID, after.updatedByUserId)
                .set(t.UPDATED_AT, at)
                .where(t.ID.eq(1.toShort()).and(t.VERSION.eq(before.version)))
                .execute() == 1,
        )
        val e = TRAVEL_SALES_CONTROL_EVENT
        dsl
            .insertInto(e)
            .set(e.VERSION, after.version)
            .set(e.PREVIOUSLY_PAUSED, before.requestsPaused)
            .set(e.REQUESTS_PAUSED, after.requestsPaused)
            .set(e.REASON, after.reason)
            .set(e.ACTOR_USER_ID, after.updatedByUserId)
            .set(e.OCCURRED_AT, at)
            .execute()
    }

    private fun TravelSalesControlRecord.toControl() =
        SalesControl(requestsPaused, version, reason, updatedByUserId, updatedAt?.toInstant())
}
