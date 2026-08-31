package com.wego.identity.application
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.User
import com.wego.identity.domain.UserId
import com.wego.transaction.TransactionRunner
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface AssignUserRolesResult {
    data class Assigned(
        val user: User,
    ) : AssignUserRolesResult

    data object NotFound : AssignUserRolesResult

    data class UnknownRole(
        val roleCode: String,
    ) : AssignUserRolesResult

    /** Self-service role escalation (or accidental self-demotion) is a real privilege-boundary risk a different admin must always be the one to change. */
    data object CannotChangeOwnRoles : AssignUserRolesResult
}

class AssignUserRolesService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val auditRecorder: IdentityAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun assign(
        actorUserId: UserId,
        targetUserId: UserId,
        roleCodes: Set<String>,
        correlationId: UUID?,
    ): AssignUserRolesResult =
        transactionRunner.runInTransaction {
            if (actorUserId == targetUserId) return@runInTransaction AssignUserRolesResult.CannotChangeOwnRoles
            val user = userRepository.findByIdForUpdate(targetUserId) ?: return@runInTransaction AssignUserRolesResult.NotFound

            val roles =
                roleCodes
                    .map { raw ->
                        val code = RoleCode.of(raw)
                        if (!roleRepository.existsByCode(code)) return@runInTransaction AssignUserRolesResult.UnknownRole(raw)
                        code
                    }.toSet()

            user.assignRoles(roles)
            userRepository.save(user)
            val now = Instant.now(clock)
            auditRecorder.recordUserRolesChanged(actorUserId, user.id, roleCodes, now, correlationId)
            AssignUserRolesResult.Assigned(user)
        }
}
