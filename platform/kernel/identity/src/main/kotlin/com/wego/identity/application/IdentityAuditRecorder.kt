package com.wego.identity.application

import com.wego.identity.domain.UserId
import java.time.Instant
import java.util.UUID

interface IdentityAuditRecorder {
    fun recordLoginSuccess(
        userId: UserId,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordLoginFailure(
        email: String,
        reason: String,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordLogout(
        userId: UserId,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordPermissionDenied(
        userId: UserId,
        permission: String,
        occurredAt: Instant,
        correlationId: UUID?,
    )
}
