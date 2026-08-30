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

    fun recordUserCreated(
        actorUserId: UserId,
        targetUserId: UserId,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordUserDisabled(
        actorUserId: UserId,
        targetUserId: UserId,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordUserEnabled(
        actorUserId: UserId,
        targetUserId: UserId,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordUserPasswordReset(
        actorUserId: UserId,
        targetUserId: UserId,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordUserRolesChanged(
        actorUserId: UserId,
        targetUserId: UserId,
        newRoles: Set<String>,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordRoleCreated(
        actorUserId: UserId,
        roleCode: String,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordRolePermissionsChanged(
        actorUserId: UserId,
        roleCode: String,
        newPermissions: Set<String>,
        occurredAt: Instant,
        correlationId: UUID?,
    )
}
