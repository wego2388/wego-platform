package com.wego.identity.infrastructure

import com.wego.generated.jooq.tables.IdentityAuditEvent.IDENTITY_AUDIT_EVENT
import com.wego.identity.application.IdentityAuditRecorder
import com.wego.identity.domain.UserId
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Component
class JooqIdentityAuditRecorder(
    private val dsl: DSLContext,
) : IdentityAuditRecorder {
    @Transactional
    override fun recordLoginSuccess(
        userId: UserId,
        occurredAt: Instant,
        correlationId: UUID?,
    ) {
        insert(
            eventType = "LOGIN_SUCCESS",
            occurredAt = occurredAt,
            actorUserId = userId.value,
            actorEmail = null,
            detail = null,
            correlationId = correlationId,
        )
    }

    @Transactional
    override fun recordLoginFailure(
        email: String,
        reason: String,
        occurredAt: Instant,
        correlationId: UUID?,
    ) {
        insert(
            eventType = "LOGIN_FAILURE",
            occurredAt = occurredAt,
            actorUserId = null,
            actorEmail = email,
            detail = reason,
            correlationId = correlationId,
        )
    }

    @Transactional
    override fun recordLogout(
        userId: UserId,
        occurredAt: Instant,
        correlationId: UUID?,
    ) {
        insert(
            eventType = "LOGOUT",
            occurredAt = occurredAt,
            actorUserId = userId.value,
            actorEmail = null,
            detail = null,
            correlationId = correlationId,
        )
    }

    @Transactional
    override fun recordPermissionDenied(
        userId: UserId,
        permission: String,
        occurredAt: Instant,
        correlationId: UUID?,
    ) {
        insert(
            eventType = "PERMISSION_DENIED",
            occurredAt = occurredAt,
            actorUserId = userId.value,
            actorEmail = null,
            detail = permission,
            correlationId = correlationId,
        )
    }

    private fun insert(
        eventType: String,
        occurredAt: Instant,
        actorUserId: UUID?,
        actorEmail: String?,
        detail: String?,
        correlationId: UUID?,
    ) {
        dsl
            .insertInto(IDENTITY_AUDIT_EVENT)
            .set(IDENTITY_AUDIT_EVENT.ID, UUID.randomUUID())
            .set(IDENTITY_AUDIT_EVENT.OCCURRED_AT, OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
            .set(IDENTITY_AUDIT_EVENT.EVENT_TYPE, eventType)
            .set(IDENTITY_AUDIT_EVENT.ACTOR_USER_ID, actorUserId)
            // Defensively bounded here too, independent of what any caller
            // already did: this column is varchar(320), and an untruncated
            // over-length value would fail the insert outright.
            .set(IDENTITY_AUDIT_EVENT.ACTOR_EMAIL, actorEmail?.take(MAX_ACTOR_EMAIL_LENGTH))
            .set(IDENTITY_AUDIT_EVENT.DETAIL, detail)
            .set(IDENTITY_AUDIT_EVENT.CORRELATION_ID, correlationId)
            .execute()
    }

    companion object {
        private const val MAX_ACTOR_EMAIL_LENGTH = 320
    }
}
