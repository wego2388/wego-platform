package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.TravelRequestActorType
import com.wego.travelmarketplace.domain.TravelRequestCancelReason
import com.wego.travelmarketplace.domain.TravelRequestStatus
import java.time.Instant
import java.util.UUID

/**
 * A dedicated recorder, not a reuse of [TravelMarketplaceAuditRecorder] —
 * that generic one only carries `(aggregateType, aggregateId, eventType,
 * actorUserId, occurredAt)`, but `delivery/01_REQUEST_AND_BOOKING.md`
 * requires "audit events with correlation id, actor, from/to states and
 * reason", which is a richer shape (from/to status pair, a typed actor kind
 * that includes an unauthenticated customer or the system, a typed
 * cancellation reason, a correlation id for tracing one customer action
 * across surfaces).
 */
interface TravelRequestAuditRecorder {
    fun record(
        requestId: UUID,
        fromStatus: TravelRequestStatus?,
        toStatus: TravelRequestStatus,
        actorType: TravelRequestActorType,
        actorUserId: UUID?,
        reason: TravelRequestCancelReason?,
        detail: String?,
        correlationId: UUID?,
        occurredAt: Instant,
    )
}
