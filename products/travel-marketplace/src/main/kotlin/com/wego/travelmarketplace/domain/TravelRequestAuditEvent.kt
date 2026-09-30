package com.wego.travelmarketplace.domain

import java.time.Instant
import java.util.UUID

/** A read model of one `travel_request_audit_event` row — append-only, never mutated, so no domain behavior beyond the data itself. */
data class TravelRequestAuditEvent(
    val id: UUID,
    val requestId: UUID,
    val occurredAt: Instant,
    val fromStatus: TravelRequestStatus?,
    val toStatus: TravelRequestStatus,
    val actorType: TravelRequestActorType,
    val actorUserId: UUID?,
    val reason: TravelRequestCancelReason?,
    val detail: String?,
    val correlationId: UUID?,
)
