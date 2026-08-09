package com.wego.identity.domain

import java.time.Instant
import java.util.UUID

@JvmInline
value class SessionId(
    val value: UUID,
) {
    companion object {
        fun generate(): SessionId = SessionId(UUID.randomUUID())
    }
}

class Session(
    val id: SessionId,
    val userId: UserId,
    val tokenHash: String,
    val issuedAt: Instant,
    val expiresAt: Instant,
    revokedAt: Instant?,
) {
    var revokedAt: Instant? = revokedAt
        private set

    init {
        require(tokenHash.isNotBlank()) { "Token hash must not be blank" }
        require(expiresAt.isAfter(issuedAt)) { "Session must expire after it is issued" }
    }

    fun isActive(asOf: Instant): Boolean = revokedAt == null && expiresAt.isAfter(asOf)

    fun revoke(asOf: Instant) {
        if (revokedAt == null) {
            revokedAt = asOf
        }
    }
}
