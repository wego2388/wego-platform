package com.wego.identity.application

import com.wego.identity.domain.Session
import com.wego.identity.domain.SessionId
import com.wego.identity.domain.UserId
import java.time.Instant

interface SessionRepository {
    fun save(session: Session)

    fun findActiveByTokenHash(
        tokenHash: String,
        asOf: Instant,
    ): Session?

    fun revoke(
        id: SessionId,
        asOf: Instant,
    )

    /** Revokes every still-active session for a user in one database write. */
    fun revokeAllForUser(
        userId: UserId,
        asOf: Instant,
    )
}
