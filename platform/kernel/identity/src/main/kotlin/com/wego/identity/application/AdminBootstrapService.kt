package com.wego.identity.application
import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.User
import com.wego.transaction.TransactionRunner
import java.time.Clock
import java.time.Instant

/**
 * Creates exactly the first platform user. Refuses outright once any user
 * exists, so this can never be used as a repeatable privilege-escalation
 * path once real accounts are in place.
 */
class AdminBootstrapService(
    private val userRepository: UserRepository,
    private val passwordHasher: PasswordHasher,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun bootstrap(
        rawEmail: String,
        rawPassword: String,
        role: RoleCode = RoleCode.of("platform-admin"),
    ): User =
        transactionRunner.runInTransaction {
            // Exclusive for the rest of this transaction: without it, two
            // concurrent bootstrap calls can both pass the exists-check
            // before either commits and both create an admin.
            userRepository.lockBootstrap()

            check(!userRepository.existsAny()) {
                "Bootstrap refused: a user already exists. Use a later authorized administration path instead."
            }
            require(rawPassword.length >= MIN_PASSWORD_LENGTH) {
                "Password must be at least $MIN_PASSWORD_LENGTH characters"
            }

            val email = EmailAddress.of(rawEmail)
            val user = User.bootstrap(email, passwordHasher.hash(rawPassword), role, Instant.now(clock))
            userRepository.save(user)
            user
        }

    companion object {
        const val MIN_PASSWORD_LENGTH = 12
    }
}
