package com.wego.identity.application

import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.User
import com.wego.identity.domain.UserId

interface UserRepository {
    fun findByEmail(email: EmailAddress): User?

    /**
     * Same lookup as [findByEmail], but takes a row lock (`SELECT ... FOR
     * UPDATE`) for the duration of the caller's transaction. Login must use
     * this: two concurrent attempts against the same account otherwise both
     * read the same failure count, both increment it once, and one
     * increment is lost — undercounting toward the lockout threshold.
     */
    fun findByEmailForUpdate(email: EmailAddress): User?

    fun findById(id: UserId): User?

    /** Row-locked read for a read-modify-write cycle — disable/enable/password-reset/role-assign all need this to avoid losing a concurrent change. */
    fun findByIdForUpdate(id: UserId): User?

    fun findAll(): List<User>

    fun existsAny(): Boolean

    /**
     * Takes an exclusive, transaction-scoped lock so only one caller can be
     * inside a bootstrap check-then-create sequence at a time. Without this,
     * two concurrent `AdminBootstrapService.bootstrap()` calls can both
     * observe `existsAny() == false` before either commits and both create
     * an admin.
     */
    fun lockBootstrap()

    fun save(user: User)
}
