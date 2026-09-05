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

sealed interface UpdateRolePermissionsResult {
    data class Updated(
        val role: Role,
    ) : UpdateRolePermissionsResult

    data object NotFound : UpdateRolePermissionsResult

    data class UnknownPermission(
        val permissionCode: String,
    ) : UpdateRolePermissionsResult
}

class UpdateRolePermissionsService(
    private val roleRepository: RoleRepository,
    private val permissionCatalogRepository: PermissionCatalogRepository,
    private val auditRecorder: IdentityAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun update(
        actorUserId: UserId,
        rawCode: String,
        permissionCodes: Set<String>,
        correlationId: UUID?,
    ): UpdateRolePermissionsResult =
        transactionRunner.runInTransaction {
            val code = RoleCode.of(rawCode)
            val existing = roleRepository.findByCodeForUpdate(code) ?: return@runInTransaction UpdateRolePermissionsResult.NotFound

            val catalog: Set<Permission> = permissionCatalogRepository.findAll().toSet()
            val catalogCodes = catalog.map { it.code.value }.toSet()
            val permissions =
                permissionCodes
                    .map { raw ->
                        if (raw !in catalogCodes) return@runInTransaction UpdateRolePermissionsResult.UnknownPermission(raw)
                        PermissionCode.of(raw)
                    }.toSet()

            val updated = existing.copy(permissions = permissions)
            roleRepository.save(updated)
            auditRecorder.recordRolePermissionsChanged(actorUserId, code.value, permissionCodes, Instant.now(clock), correlationId)
            UpdateRolePermissionsResult.Updated(updated)
        }
}
