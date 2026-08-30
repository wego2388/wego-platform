package com.wego.identity.application

import com.wego.identity.domain.Role
import com.wego.identity.domain.RoleCode

interface RoleRepository {
    fun findAll(): List<Role>

    fun findByCode(code: RoleCode): Role?

    /** Row-locked read — updating a role's permission set is a read-modify-write against identity_role_permission. */
    fun findByCodeForUpdate(code: RoleCode): Role?

    fun existsByCode(code: RoleCode): Boolean

    /** Insert-or-update by code; always replaces the full permission set (matches JooqUserRepository.save's role-sync shape). */
    fun save(role: Role)
}
