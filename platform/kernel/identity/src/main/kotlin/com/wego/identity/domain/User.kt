package com.wego.identity.domain

import java.time.Duration
import java.time.Instant

class User(
    val id: UserId,
    val email: EmailAddress,
    passwordHash: HashedPassword,
    status: UserStatus,
    roles: Set<RoleCode>,
    val createdAt: Instant,
    failedLoginCount: Int,
    lockedUntil: Instant?,
) {
    var passwordHash: HashedPassword = passwordHash
        private set

    var status: UserStatus = status
        private set

    var roles: Set<RoleCode> = roles
        private set

    var failedLoginCount: Int = failedLoginCount
        private set

    var lockedUntil: Instant? = lockedUntil
        private set

    init {
        require(failedLoginCount >= 0) { "Failed login count must not be negative" }
    }

    fun isLocked(asOf: Instant): Boolean = lockedUntil?.isAfter(asOf) == true

    fun canAuthenticate(asOf: Instant): Boolean = status == UserStatus.ACTIVE && !isLocked(asOf)

    fun registerSuccessfulLogin() {
        failedLoginCount = 0
        lockedUntil = null
    }

    fun registerFailedLogin(
        asOf: Instant,
        maxAttempts: Int,
        lockoutDuration: Duration,
    ) {
        failedLoginCount += 1
        if (failedLoginCount >= maxAttempts) {
            lockedUntil = asOf.plus(lockoutDuration)
        }
    }

    /** Terminal from the caller's perspective, but reversible via [enable] — unlike Diver's archive, a disabled account is expected to come back. */
    fun disable() {
        require(status == UserStatus.ACTIVE) { "Only an active account can be disabled" }
        status = UserStatus.DISABLED
    }

    // Re-enabling deliberately does not reset failedLoginCount/lockedUntil — those track real
    // login-attempt history and expire on their own via isLocked(asOf); an admin toggling
    // status is not the same event as a real successful login.
    fun enable() {
        require(status == UserStatus.DISABLED) { "Only a disabled account can be re-enabled" }
        status = UserStatus.ACTIVE
    }

    fun changePassword(newPasswordHash: HashedPassword) {
        passwordHash = newPasswordHash
    }

    /** Every account must always belong to at least one role — a role-less account could authenticate but never do anything, a state with no real use and only failure modes. */
    fun assignRoles(newRoles: Set<RoleCode>) {
        require(newRoles.isNotEmpty()) { "An account must hold at least one role" }
        roles = newRoles
    }

    companion object {
        fun bootstrap(
            email: EmailAddress,
            passwordHash: HashedPassword,
            role: RoleCode,
            now: Instant,
        ): User =
            User(
                id = UserId.generate(),
                email = email,
                passwordHash = passwordHash,
                status = UserStatus.ACTIVE,
                roles = setOf(role),
                createdAt = now,
                failedLoginCount = 0,
                lockedUntil = null,
            )

        /** Same shape as [bootstrap] — admin-created accounts and the very first bootstrapped account share one construction path, just gated differently by their respective services. */
        fun create(
            email: EmailAddress,
            passwordHash: HashedPassword,
            roles: Set<RoleCode>,
            now: Instant,
        ): User {
            require(roles.isNotEmpty()) { "An account must hold at least one role" }
            return User(
                id = UserId.generate(),
                email = email,
                passwordHash = passwordHash,
                status = UserStatus.ACTIVE,
                roles = roles,
                createdAt = now,
                failedLoginCount = 0,
                lockedUntil = null,
            )
        }
    }
}
