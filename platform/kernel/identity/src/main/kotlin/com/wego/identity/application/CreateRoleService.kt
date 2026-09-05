package com.wego.identity.application
import com.wego.identity.domain.Permission
import com.wego.identity.domain.Role
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.UserId
import com.wego.security.PermissionCode
import com.wego.transaction.TransactionRunner
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface CreateRoleResult {
    data class Created(
        val role: Role,
    ) : CreateRoleResult

    data object AlreadyExists : CreateRoleResult

    data class UnknownPermission(
        val permissionCode: String,
    ) : CreateRoleResult
}

class CreateRoleService(
    private val roleRepository: RoleRepository,
    private val permissionCatalogRepository: PermissionCatalogRepository,
    private val auditRecorder: IdentityAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun create(
        actorUserId: UserId,
        rawCode: String,
        description: String,
        permissionCodes: Set<String>,
        correlationId: UUID?,
    ): CreateRoleResult =
        transactionRunner.runInTransaction {
            val code = RoleCode.of(rawCode)
            if (roleRepository.existsByCode(code)) return@runInTransaction CreateRoleResult.AlreadyExists

            val catalog: Set<Permission> = permissionCatalogRepository.findAll().toSet()
            val catalogCodes = catalog.map { it.code.value }.toSet()
            val permissions =
                permissionCodes
                    .map { raw ->
                        if (raw !in catalogCodes) return@runInTransaction CreateRoleResult.UnknownPermission(raw)
                        PermissionCode.of(raw)
                    }.toSet()

            val role = Role(code, description, permissions)
            roleRepository.save(role)
            auditRecorder.recordRoleCreated(actorUserId, code.value, Instant.now(clock), correlationId)
            CreateRoleResult.Created(role)
        }
}
