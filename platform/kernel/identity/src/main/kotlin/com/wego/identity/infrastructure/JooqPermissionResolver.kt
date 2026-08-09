package com.wego.identity.infrastructure

import com.wego.generated.jooq.tables.IdentityRolePermission.IDENTITY_ROLE_PERMISSION
import com.wego.identity.application.PermissionResolver
import com.wego.identity.domain.RoleCode
import com.wego.security.PermissionCode
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class JooqPermissionResolver(
    private val dsl: DSLContext,
) : PermissionResolver {
    @Transactional(readOnly = true)
    override fun resolve(roles: Set<RoleCode>): Set<PermissionCode> {
        if (roles.isEmpty()) return emptySet()

        return dsl
            .selectDistinct(IDENTITY_ROLE_PERMISSION.PERMISSION_CODE)
            .from(IDENTITY_ROLE_PERMISSION)
            .where(IDENTITY_ROLE_PERMISSION.ROLE_CODE.`in`(roles.map { it.value }))
            .fetch(IDENTITY_ROLE_PERMISSION.PERMISSION_CODE)
            .map { PermissionCode.of(it) }
            .toSet()
    }
}
