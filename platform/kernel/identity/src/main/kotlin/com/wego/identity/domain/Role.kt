package com.wego.identity.domain

import com.wego.security.PermissionCode

/** A named bundle of permissions — permissions attach to a role, never directly to a user, matching identity_role_permission's own shape. */
data class Role(
    val code: RoleCode,
    val description: String,
    val permissions: Set<PermissionCode>,
) {
    init {
        require(description.isNotBlank()) { "Role description must not be blank" }
    }
}
