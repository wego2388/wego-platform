package com.wego.divers.infrastructure

import com.wego.divers.application.DiverAuditRecorder
import com.wego.divers.domain.DiverId
import com.wego.generated.jooq.tables.DiversDiverAuditEvent.DIVERS_DIVER_AUDIT_EVENT
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Component
class JooqDiverAuditRecorder(
    private val dsl: DSLContext,
) : DiverAuditRecorder {
    @Transactional
    override fun recordDiverCreated(
        diverId: DiverId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    ) = insert(diverId, "DIVER_CREATED", actorUserId, occurredAt, correlationId)

    @Transactional
    override fun recordDiverUpdated(
        diverId: DiverId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    ) = insert(diverId, "DIVER_UPDATED", actorUserId, occurredAt, correlationId)

    @Transactional
    override fun recordDiverArchived(
        diverId: DiverId,
        actorUserId: UUID,
        occurredAt: Instant,
        correlationId: UUID?,
    ) = insert(diverId, "DIVER_ARCHIVED", actorUserId, occurredAt, correlationId)

    private fun insert(
        diverId: DiverId,
        eventType: String,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    ) {
        dsl
            .insertInto(DIVERS_DIVER_AUDIT_EVENT)
            .set(DIVERS_DIVER_AUDIT_EVENT.ID, UUID.randomUUID())
            .set(DIVERS_DIVER_AUDIT_EVENT.DIVER_ID, diverId.value)
            .set(DIVERS_DIVER_AUDIT_EVENT.OCCURRED_AT, OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
            .set(DIVERS_DIVER_AUDIT_EVENT.EVENT_TYPE, eventType)
            .set(DIVERS_DIVER_AUDIT_EVENT.ACTOR_USER_ID, actorUserId)
            .set(DIVERS_DIVER_AUDIT_EVENT.CORRELATION_ID, correlationId)
            .execute()
    }
}
