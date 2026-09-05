package com.wego.identity.api

import com.wego.identity.domain.Permission
import com.wego.identity.domain.Role
import com.wego.identity.domain.User
import java.time.Instant
import java.util.UUID

data class CreateUserRequest(
    val email: String,
    val password: String,
    val roleCodes: List<String>,
)

data class ResetUserPasswordRequest(
    val newPassword: String,
)

data class AssignUserRolesRequest(
    val roleCodes: List<String>,
)

data class CreateRoleRequest(
    val code: String,
    val description: String,
    val permissionCodes: List<String>,
)

data class UpdateRolePermissionsRequest(
    val permissionCodes: List<String>,
)

data class UserResponse(
    val id: UUID,
    val email: String,
    val status: String,
    val roles: List<String>,
    val createdAt: Instant,
)

data class RoleResponse(
    val code: String,
    val description: String,
    val permissions: List<String>,
)

data class PermissionResponse(
    val code: String,
    val description: String,
)

data class IdentityAdminErrorResponse(
    val error: String,
)

fun User.toResponse(): UserResponse =
    UserResponse(
        id = id.value,
        email = email.value,
        status = status.name,
        roles = roles.map { it.value }.sorted(),
        createdAt = createdAt,
    )

fun Role.toResponse(): RoleResponse =
    RoleResponse(
        code = code.value,
        description = description,
        permissions = permissions.map { it.value }.sorted(),
    )

fun Permission.toResponse(): PermissionResponse = PermissionResponse(code = code.value, description = description)
