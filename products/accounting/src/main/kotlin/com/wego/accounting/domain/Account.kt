package com.wego.accounting.domain

import java.time.Instant
import java.util.UUID

/**
 * A line in the chart of accounts. Deactivated, never deleted, once
 * journal history might reference it — see [isActive]. No dedicated
 * audit-event table: `updatedAt` plus the journal entries themselves (each
 * permanently timestamped and actor-stamped) are the real audit trail for
 * a low-risk configuration entity like this one, the same reasoning
 * `AttendanceRecord` (products/hr) already applies.
 */
class Account(
    val id: AccountId,
    val code: String,
    name: String,
    val accountType: AccountType,
    val parentAccountId: AccountId?,
    description: String?,
    isActive: Boolean,
    val createdByUserId: UUID?,
    val createdAt: Instant,
    updatedAt: Instant,
) {
    var name: String = name
        private set

    var description: String? = description
        private set

    var isActive: Boolean = isActive
        private set

    var updatedAt: Instant = updatedAt
        private set

    init {
        require(code.isNotBlank()) { "Account code must not be blank" }
        require(name.isNotBlank()) { "Account name must not be blank" }
    }

    fun deactivate(now: Instant) {
        require(isActive) { "Account is already inactive" }
        isActive = false
        updatedAt = now
    }

    fun reactivate(now: Instant) {
        require(!isActive) { "Account is already active" }
        isActive = true
        updatedAt = now
    }

    /** Code and account type are never editable here — changing either is a real structural/reporting decision, not a plain detail correction. */
    fun withUpdatedDetails(
        name: String,
        description: String?,
        now: Instant,
    ): Account =
        Account(
            id = id,
            code = code,
            name = name,
            accountType = accountType,
            parentAccountId = parentAccountId,
            description = description,
            isActive = isActive,
            createdByUserId = createdByUserId,
            createdAt = createdAt,
            updatedAt = now,
        )

    companion object {
        fun create(
            id: AccountId,
            code: String,
            name: String,
            accountType: AccountType,
            parentAccountId: AccountId?,
            description: String?,
            createdByUserId: UUID?,
            now: Instant,
        ): Account =
            Account(
                id = id,
                code = code,
                name = name,
                accountType = accountType,
                parentAccountId = parentAccountId,
                description = description,
                isActive = true,
                createdByUserId = createdByUserId,
                createdAt = now,
                updatedAt = now,
            )
    }
}
