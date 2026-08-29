package com.wego.divers.infrastructure

import com.wego.divers.application.BoatCharterAuditRecorder
import com.wego.divers.domain.BoatCharterId
import com.wego.generated.jooq.tables.DiversBoatCharterAuditEvent.DIVERS_BOAT_CHARTER_AUDIT_EVENT
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Component
class JooqBoatCharterAuditRecorder(
    private val dsl: DSLContext,
) : BoatCharterAuditRecorder {
    @Transactional
    override fun recordCharterCreated(
        charterId: BoatCharterId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    ) = insert(charterId, "CHARTER_CREATED", actorUserId, occurredAt, correlationId)

    @Transactional
    override fun recordCharterEnded(
        charterId: BoatCharterId,
        actorUserId: UUID,
        occurredAt: Instant,
        correlationId: UUID?,
    ) = insert(charterId, "CHARTER_ENDED", actorUserId, occurredAt, correlationId)

    private fun insert(
        charterId: BoatCharterId,
        eventType: String,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    ) {
        dsl
            .insertInto(DIVERS_BOAT_CHARTER_AUDIT_EVENT)
            .set(DIVERS_BOAT_CHARTER_AUDIT_EVENT.ID, UUID.randomUUID())
            .set(DIVERS_BOAT_CHARTER_AUDIT_EVENT.BOAT_CHARTER_ID, charterId.value)
            .set(DIVERS_BOAT_CHARTER_AUDIT_EVENT.OCCURRED_AT, OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
            .set(DIVERS_BOAT_CHARTER_AUDIT_EVENT.EVENT_TYPE, eventType)
            .set(DIVERS_BOAT_CHARTER_AUDIT_EVENT.ACTOR_USER_ID, actorUserId)
            .set(DIVERS_BOAT_CHARTER_AUDIT_EVENT.CORRELATION_ID, correlationId)
            .execute()
    }
}
