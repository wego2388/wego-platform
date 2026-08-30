package com.wego.identity.application

import com.wego.identity.domain.User
import com.wego.identity.domain.UserId
import com.wego.identity.domain.UserStatus
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface DisableUserResult {
    data class Disabled(
        val user: User,
    ) : DisableUserResult

    data object NotFound : DisableUserResult

    data object AlreadyDisabled : DisableUserResult

    /** An admin disabling their own account could lock the platform's only administrator out with no way back in — a real, not hypothetical, self-service path forbids it outright. */
    data object CannotDisableSelf : DisableUserResult
}

class DisableUserService(
    private val userRepository: UserRepository,
    private val auditRecorder: IdentityAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun disable(
        actorUserId: UserId,
        targetUserId: UserId,
        correlationId: UUID?,
    ): DisableUserResult =
        transactionRunner.runInTransaction {
            if (actorUserId == targetUserId) return@runInTransaction DisableUserResult.CannotDisableSelf
            val user = userRepository.findByIdForUpdate(targetUserId) ?: return@runInTransaction DisableUserResult.NotFound
            if (user.status == UserStatus.DISABLED) return@runInTransaction DisableUserResult.AlreadyDisabled

            user.disable()
            userRepository.save(user)
            val now = Instant.now(clock)
            auditRecorder.recordUserDisabled(actorUserId, user.id, now, correlationId)
            DisableUserResult.Disabled(user)
        }
}
