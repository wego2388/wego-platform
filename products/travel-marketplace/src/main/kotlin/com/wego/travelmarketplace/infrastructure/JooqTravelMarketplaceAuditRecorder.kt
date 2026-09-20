package com.wego.travelmarketplace.infrastructure

import com.wego.generated.jooq.tables.TravelMarketplaceAuditEvent.TRAVEL_MARKETPLACE_AUDIT_EVENT
import com.wego.travelmarketplace.application.TravelMarketplaceAuditRecorder
import com.wego.travelmarketplace.domain.AuditAggregateType
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Component
class JooqTravelMarketplaceAuditRecorder(
    private val dsl: DSLContext,
) : TravelMarketplaceAuditRecorder {
    @Transactional
    override fun record(
        aggregateType: AuditAggregateType,
        aggregateId: UUID,
        eventType: String,
        actorUserId: UUID?,
        occurredAt: Instant,
    ) {
        dsl
            .insertInto(TRAVEL_MARKETPLACE_AUDIT_EVENT)
            .set(TRAVEL_MARKETPLACE_AUDIT_EVENT.ID, UUID.randomUUID())
            .set(TRAVEL_MARKETPLACE_AUDIT_EVENT.AGGREGATE_TYPE, aggregateType.name)
            .set(TRAVEL_MARKETPLACE_AUDIT_EVENT.AGGREGATE_ID, aggregateId)
            .set(TRAVEL_MARKETPLACE_AUDIT_EVENT.OCCURRED_AT, OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
            .set(TRAVEL_MARKETPLACE_AUDIT_EVENT.EVENT_TYPE, eventType)
            .set(TRAVEL_MARKETPLACE_AUDIT_EVENT.ACTOR_USER_ID, actorUserId)
            .execute()
    }
}
