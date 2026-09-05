package com.wego.identity.infrastructure

import com.wego.generated.jooq.tables.IdentityPermission.IDENTITY_PERMISSION
import com.wego.identity.application.PermissionCatalogRepository
import com.wego.identity.domain.Permission
import com.wego.security.PermissionCode
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class JooqPermissionCatalogRepository(
    private val dsl: DSLContext,
) : PermissionCatalogRepository {
    @Transactional(readOnly = true)
    override fun findAll(): List<Permission> =
        dsl
            .selectFrom(IDENTITY_PERMISSION)
            .orderBy(IDENTITY_PERMISSION.CODE)
            .fetch()
            .map { record -> Permission(PermissionCode.of(record.code), record.description) }
}
