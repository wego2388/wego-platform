package com.wego.identity.application

import com.wego.identity.domain.Session
import com.wego.identity.domain.SessionId
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
}
