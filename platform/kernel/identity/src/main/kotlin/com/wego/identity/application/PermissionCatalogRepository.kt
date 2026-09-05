package com.wego.identity.application

import com.wego.identity.domain.Permission

/** Read-only: the permission catalog is seeded by migration, not created through the API — see V9's identity_permission. */
interface PermissionCatalogRepository {
    fun findAll(): List<Permission>
}
