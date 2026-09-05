package com.wego.accounting.application

import com.wego.accounting.domain.Account
import com.wego.accounting.domain.AccountId
import com.wego.accounting.domain.AccountType
import com.wego.transaction.TransactionRunner
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface CreateAccountResult {
    data class Created(
        val account: Account,
    ) : CreateAccountResult

    data object CodeAlreadyInUse : CreateAccountResult

    data object ParentAccountNotFound : CreateAccountResult
}

class CreateAccountService(
    private val accountRepository: AccountRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun create(
        code: String,
        name: String,
        accountType: AccountType,
        parentAccountId: AccountId?,
        description: String?,
        createdByUserId: UUID?,
    ): CreateAccountResult =
        transactionRunner.runInTransaction {
            if (accountRepository.findByCode(code) != null) return@runInTransaction CreateAccountResult.CodeAlreadyInUse
            if (parentAccountId != null && accountRepository.findById(parentAccountId) == null) {
                return@runInTransaction CreateAccountResult.ParentAccountNotFound
            }

            val account =
                Account.create(
                    id = AccountId.generate(),
                    code = code,
                    name = name,
                    accountType = accountType,
                    parentAccountId = parentAccountId,
                    description = description,
                    createdByUserId = createdByUserId,
                    now = Instant.now(clock),
                )
            accountRepository.save(account)
            CreateAccountResult.Created(account)
        }
}
