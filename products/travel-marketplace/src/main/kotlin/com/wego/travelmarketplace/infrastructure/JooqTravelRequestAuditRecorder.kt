package com.wego.travelmarketplace.infrastructure

import com.wego.generated.jooq.tables.TravelRequestAuditEvent.TRAVEL_REQUEST_AUDIT_EVENT
import com.wego.travelmarketplace.application.TravelRequestAuditRecorder
import com.wego.travelmarketplace.domain.TravelRequestActorType
import com.wego.travelmarketplace.domain.TravelRequestCancelReason
import com.wego.travelmarketplace.domain.TravelRequestStatus
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class JooqTravelRequestAuditRecorder(
    private val dsl: DSLContext,
) : TravelRequestAuditRecorder {
    @Transactional
    override fun record(
        requestId: UUID,
        fromStatus: TravelRequestStatus?,
        toStatus: TravelRequestStatus,
        actorType: TravelRequestActorType,
        actorUserId: UUID?,
        reason: TravelRequestCancelReason?,
        detail: String?,
        correlationId: UUID?,
        occurredAt: Instant,
    ) {
        dsl
            .insertInto(TRAVEL_REQUEST_AUDIT_EVENT)
            .set(TRAVEL_REQUEST_AUDIT_EVENT.ID, UUID.randomUUID())
            .set(TRAVEL_REQUEST_AUDIT_EVENT.REQUEST_ID, requestId)
            .set(TRAVEL_REQUEST_AUDIT_EVENT.OCCURRED_AT, OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
            .set(TRAVEL_REQUEST_AUDIT_EVENT.FROM_STATUS, fromStatus?.name)
            .set(TRAVEL_REQUEST_AUDIT_EVENT.TO_STATUS, toStatus.name)
            .set(TRAVEL_REQUEST_AUDIT_EVENT.ACTOR_TYPE, actorType.name)
            .set(TRAVEL_REQUEST_AUDIT_EVENT.ACTOR_USER_ID, actorUserId)
            .set(TRAVEL_REQUEST_AUDIT_EVENT.REASON, reason?.name)
            .set(TRAVEL_REQUEST_AUDIT_EVENT.DETAIL, detail)
            .set(TRAVEL_REQUEST_AUDIT_EVENT.CORRELATION_ID, correlationId)
            .execute()
    }
}
