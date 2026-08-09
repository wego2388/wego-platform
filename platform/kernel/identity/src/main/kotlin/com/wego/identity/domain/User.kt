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
    }
}
