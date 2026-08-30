package com.wego.identity.application

import com.wego.identity.domain.User
import com.wego.identity.domain.UserId
import com.wego.identity.domain.UserStatus
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface EnableUserResult {
    data class Enabled(
        val user: User,
    ) : EnableUserResult

    data object NotFound : EnableUserResult

    data object AlreadyActive : EnableUserResult
}

class EnableUserService(
    private val userRepository: UserRepository,
    private val auditRecorder: IdentityAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun enable(
        actorUserId: UserId,
        targetUserId: UserId,
        correlationId: UUID?,
    ): EnableUserResult =
        transactionRunner.runInTransaction {
            val user = userRepository.findByIdForUpdate(targetUserId) ?: return@runInTransaction EnableUserResult.NotFound
            if (user.status == UserStatus.ACTIVE) return@runInTransaction EnableUserResult.AlreadyActive

            user.enable()
            userRepository.save(user)
            val now = Instant.now(clock)
            auditRecorder.recordUserEnabled(actorUserId, user.id, now, correlationId)
            EnableUserResult.Enabled(user)
        }
}
