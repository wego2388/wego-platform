package com.wego.accounting.api

import com.wego.accounting.domain.Account
import com.wego.accounting.domain.AccountType
import com.wego.accounting.domain.JournalLineDirection
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

private const val MAX_CODE_LENGTH = 20
private const val MAX_NAME_LENGTH = 200
private const val MAX_DESCRIPTION_LENGTH = 2000

data class CreateAccountRequest(
    @field:NotBlank
    @field:Size(max = MAX_CODE_LENGTH)
    val code: String,
    @field:NotBlank
    @field:Size(max = MAX_NAME_LENGTH)
    val name: String,
    @field:NotNull
    val accountType: AccountType,
    val parentAccountId: UUID?,
    @field:Size(max = MAX_DESCRIPTION_LENGTH)
    val description: String?,
)

data class UpdateAccountRequest(
    @field:NotBlank
    @field:Size(max = MAX_NAME_LENGTH)
    val name: String,
    @field:Size(max = MAX_DESCRIPTION_LENGTH)
    val description: String?,
)

data class AccountResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val accountType: AccountType,
    /** Derived from `accountType`, not independently stored — see `AccountType.normalBalance`. */
    val normalBalance: JournalLineDirection,
    val parentAccountId: UUID?,
    val description: String?,
    // Named `active`, not `isActive` — Kotlin's `Boolean` getter for a
    // property named `isActive` compiles to `isActive()`, and Jackson's
    // default bean-property naming strips a getter's "is" prefix, so the
    // wire field would silently become "active" anyway; naming it that
    // from the start matches this codebase's existing convention of
    // avoiding "is"-prefixed API field names for exactly this reason.
    val active: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun Account.toResponse(): AccountResponse =
    AccountResponse(
        id = id.value,
        code = code,
        name = name,
        accountType = accountType,
        normalBalance = accountType.normalBalance,
        parentAccountId = parentAccountId?.value,
        description = description,
        active = isActive,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

data class AccountErrorResponse(
    val error: String,
)
