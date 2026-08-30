package com.wego.identity.infrastructure

import com.wego.generated.jooq.tables.IdentityRole.IDENTITY_ROLE
import com.wego.generated.jooq.tables.IdentityRolePermission.IDENTITY_ROLE_PERMISSION
import com.wego.generated.jooq.tables.records.IdentityRoleRecord
import com.wego.identity.application.RoleRepository
import com.wego.identity.domain.Role
import com.wego.identity.domain.RoleCode
import com.wego.security.PermissionCode
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class JooqRoleRepository(
    private val dsl: DSLContext,
) : RoleRepository {
    @Transactional(readOnly = true)
    override fun findAll(): List<Role> =
        dsl
            .selectFrom(IDENTITY_ROLE)
            .orderBy(IDENTITY_ROLE.CODE)
            .fetch()
            .map(::toDomain)

    @Transactional(readOnly = true)
    override fun findByCode(code: RoleCode): Role? {
        val record = dsl.selectFrom(IDENTITY_ROLE).where(IDENTITY_ROLE.CODE.eq(code.value)).fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional
    override fun findByCodeForUpdate(code: RoleCode): Role? {
        val record =
            dsl
                .selectFrom(IDENTITY_ROLE)
                .where(IDENTITY_ROLE.CODE.eq(code.value))
                .forUpdate()
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional(readOnly = true)
    override fun existsByCode(code: RoleCode): Boolean =
        dsl.fetchExists(dsl.selectFrom(IDENTITY_ROLE).where(IDENTITY_ROLE.CODE.eq(code.value)))

    @Transactional
    override fun save(role: Role) {
        dsl
            .insertInto(IDENTITY_ROLE)
            .set(IDENTITY_ROLE.CODE, role.code.value)
            .set(IDENTITY_ROLE.DESCRIPTION, role.description)
            .onConflict(IDENTITY_ROLE.CODE)
            .doUpdate()
            .set(IDENTITY_ROLE.DESCRIPTION, role.description)
            .execute()

        dsl
            .deleteFrom(IDENTITY_ROLE_PERMISSION)
            .where(IDENTITY_ROLE_PERMISSION.ROLE_CODE.eq(role.code.value))
            .execute()

        if (role.permissions.isNotEmpty()) {
            val insert =
                dsl.insertInto(
                    IDENTITY_ROLE_PERMISSION,
                    IDENTITY_ROLE_PERMISSION.ROLE_CODE,
                    IDENTITY_ROLE_PERMISSION.PERMISSION_CODE,
                )
            role.permissions.forEach { permission -> insert.values(role.code.value, permission.value) }
            insert.execute()
        }
    }

    private fun toDomain(record: IdentityRoleRecord): Role {
        val permissions =
            dsl
                .select(IDENTITY_ROLE_PERMISSION.PERMISSION_CODE)
                .from(IDENTITY_ROLE_PERMISSION)
                .where(IDENTITY_ROLE_PERMISSION.ROLE_CODE.eq(record.code))
                .fetch(IDENTITY_ROLE_PERMISSION.PERMISSION_CODE)
                .map { PermissionCode.of(it) }
                .toSet()

        return Role(
            code = RoleCode.of(record.code),
            description = record.description,
            permissions = permissions,
        )
    }
}
