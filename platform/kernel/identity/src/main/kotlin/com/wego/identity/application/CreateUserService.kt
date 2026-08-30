package com.wego.identity.application

import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.User
import com.wego.identity.domain.UserId
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface CreateUserResult {
    data class Created(
        val user: User,
    ) : CreateUserResult

    data object EmailAlreadyInUse : CreateUserResult

    /** Every role code the caller listed must already exist — a typo'd or made-up role must not silently create an account with no real permissions. */
    data class UnknownRole(
        val roleCode: String,
    ) : CreateUserResult
}

class CreateUserService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val passwordHasher: PasswordHasher,
    private val auditRecorder: IdentityAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun create(
        actorUserId: UserId,
        rawEmail: String,
        rawPassword: String,
        roleCodes: Set<String>,
        correlationId: UUID?,
    ): CreateUserResult =
        transactionRunner.runInTransaction {
            require(rawPassword.length >= MIN_PASSWORD_LENGTH) {
                "Password must be at least $MIN_PASSWORD_LENGTH characters"
            }
            val email = EmailAddress.of(rawEmail)
            if (userRepository.findByEmail(email) != null) return@runInTransaction CreateUserResult.EmailAlreadyInUse

            val roles =
                roleCodes
                    .map { raw ->
                        val code = RoleCode.of(raw)
                        if (!roleRepository.existsByCode(code)) return@runInTransaction CreateUserResult.UnknownRole(raw)
                        code
                    }.toSet()

            val now = Instant.now(clock)
            val user = User.create(email, passwordHasher.hash(rawPassword), roles, now)
            userRepository.save(user)
            auditRecorder.recordUserCreated(actorUserId, user.id, now, correlationId)
            CreateUserResult.Created(user)
        }

    companion object {
        const val MIN_PASSWORD_LENGTH = AdminBootstrapService.MIN_PASSWORD_LENGTH
    }
}
