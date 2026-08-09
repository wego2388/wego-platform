package com.wego.identity.infrastructure

import com.wego.generated.jooq.tables.IdentitySession.IDENTITY_SESSION
import com.wego.identity.application.SessionRepository
import com.wego.identity.domain.Session
import com.wego.identity.domain.SessionId
import com.wego.identity.domain.UserId
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqSessionRepository(
    private val dsl: DSLContext,
) : SessionRepository {
    @Transactional
    override fun save(session: Session) {
        dsl
            .insertInto(IDENTITY_SESSION)
            .set(IDENTITY_SESSION.ID, session.id.value)
            .set(IDENTITY_SESSION.USER_ID, session.userId.value)
            .set(IDENTITY_SESSION.TOKEN_HASH, session.tokenHash)
            .set(IDENTITY_SESSION.ISSUED_AT, toOffset(session.issuedAt))
            .set(IDENTITY_SESSION.EXPIRES_AT, toOffset(session.expiresAt))
            .set(IDENTITY_SESSION.REVOKED_AT, session.revokedAt?.let(::toOffset))
            .execute()
    }

    @Transactional(readOnly = true)
    override fun findActiveByTokenHash(
        tokenHash: String,
        asOf: Instant,
    ): Session? {
        val record =
            dsl
                .selectFrom(IDENTITY_SESSION)
                .where(IDENTITY_SESSION.TOKEN_HASH.eq(tokenHash))
                .and(IDENTITY_SESSION.REVOKED_AT.isNull())
                .and(IDENTITY_SESSION.EXPIRES_AT.gt(toOffset(asOf)))
                .fetchOne() ?: return null

        return Session(
            id = SessionId(record.id),
            userId = UserId(record.userId),
            tokenHash = record.tokenHash,
            issuedAt = record.issuedAt.toInstant(),
            expiresAt = record.expiresAt.toInstant(),
            revokedAt = record.revokedAt?.toInstant(),
        )
    }

    @Transactional
    override fun revoke(
        id: SessionId,
        asOf: Instant,
    ) {
        dsl
            .update(IDENTITY_SESSION)
            .set(IDENTITY_SESSION.REVOKED_AT, toOffset(asOf))
            .where(IDENTITY_SESSION.ID.eq(id.value))
            .and(IDENTITY_SESSION.REVOKED_AT.isNull())
            .execute()
    }

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
