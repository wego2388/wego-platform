package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.AuditAggregateType
import java.time.Instant
import java.util.UUID

/**
 * One generic recorder for all three catalog aggregates — see the schema
 * comment on `wego.travel_marketplace_audit_event` for why this phase uses a
 * single table/interface instead of Divers' one-per-aggregate convention.
 */
interface TravelMarketplaceAuditRecorder {
    fun record(
        aggregateType: AuditAggregateType,
        aggregateId: UUID,
        eventType: String,
        actorUserId: UUID?,
        occurredAt: Instant,
    )
}
