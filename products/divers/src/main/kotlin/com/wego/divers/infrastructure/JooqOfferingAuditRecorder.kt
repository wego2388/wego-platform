package com.wego.divers.infrastructure

import com.wego.divers.application.OfferingAuditRecorder
import com.wego.divers.domain.OfferingId
import com.wego.generated.jooq.tables.DiversOfferingAuditEvent.DIVERS_OFFERING_AUDIT_EVENT
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Component
class JooqOfferingAuditRecorder(
    private val dsl: DSLContext,
) : OfferingAuditRecorder {
    @Transactional
    override fun recordOfferingCreated(
        offeringId: OfferingId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    ) = insert(offeringId, "OFFERING_CREATED", actorUserId, occurredAt, null, correlationId)

    @Transactional
    override fun recordOfferingClosed(
        offeringId: OfferingId,
        actorUserId: UUID,
        occurredAt: Instant,
        reason: String?,
        correlationId: UUID?,
    ) = insert(offeringId, "OFFERING_CLOSED", actorUserId, occurredAt, reason, correlationId)

    private fun insert(
        offeringId: OfferingId,
        eventType: String,
        actorUserId: UUID?,
        occurredAt: Instant,
        reason: String?,
        correlationId: UUID?,
    ) {
        dsl
            .insertInto(DIVERS_OFFERING_AUDIT_EVENT)
            .set(DIVERS_OFFERING_AUDIT_EVENT.ID, UUID.randomUUID())
            .set(DIVERS_OFFERING_AUDIT_EVENT.OFFERING_ID, offeringId.value)
            .set(DIVERS_OFFERING_AUDIT_EVENT.OCCURRED_AT, OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
            .set(DIVERS_OFFERING_AUDIT_EVENT.EVENT_TYPE, eventType)
            .set(DIVERS_OFFERING_AUDIT_EVENT.ACTOR_USER_ID, actorUserId)
            .set(DIVERS_OFFERING_AUDIT_EVENT.REASON, reason)
            .set(DIVERS_OFFERING_AUDIT_EVENT.CORRELATION_ID, correlationId)
            .execute()
    }
}
