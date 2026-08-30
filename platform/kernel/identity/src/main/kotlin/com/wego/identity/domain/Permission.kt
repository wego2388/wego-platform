package com.wego.identity.domain

import com.wego.security.PermissionCode

/** One row of the permission catalog (identity_permission) — the full set of codes a role can be granted. */
data class Permission(
    val code: PermissionCode,
    val description: String,
) {
    init {
        require(description.isNotBlank()) { "Permission description must not be blank" }
    }
}
