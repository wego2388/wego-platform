package com.wego.identity.application

import com.wego.identity.domain.User
import com.wego.identity.domain.UserId
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface ResetUserPasswordResult {
    data class Reset(
        val user: User,
    ) : ResetUserPasswordResult

    data object NotFound : ResetUserPasswordResult
}

/**
 * Admin-initiated reset with no email/token flow — the admin sets the new
 * password directly and tells it to the employee. Right-sized for a small
 * staff where "forgot password" is handled in person, matching how the very
 * first account is bootstrapped ([AdminBootstrapService]) rather than
 * building a mail-delivery-dependent self-service reset flow this platform
 * doesn't have (WEGO-004, customer communications, is not authorized yet).
 */
class ResetUserPasswordService(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher,
    private val auditRecorder: IdentityAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun reset(
        actorUserId: UserId,
        targetUserId: UserId,
        rawNewPassword: String,
        correlationId: UUID?,
    ): ResetUserPasswordResult =
        transactionRunner.runInTransaction {
            require(rawNewPassword.length >= CreateUserService.MIN_PASSWORD_LENGTH) {
                "Password must be at least ${CreateUserService.MIN_PASSWORD_LENGTH} characters"
            }
            val user = userRepository.findByIdForUpdate(targetUserId) ?: return@runInTransaction ResetUserPasswordResult.NotFound

            user.changePassword(passwordHasher.hash(rawNewPassword))
            userRepository.save(user)
            val now = Instant.now(clock)
            auditRecorder.recordUserPasswordReset(actorUserId, user.id, now, correlationId)
            ResetUserPasswordResult.Reset(user)
        }
}
