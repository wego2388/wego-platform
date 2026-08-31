package com.wego.accounting.application

import com.wego.accounting.domain.Account
import com.wego.accounting.domain.AccountId
import com.wego.transaction.TransactionRunner
import java.time.Clock
import java.time.Instant

sealed interface UpdateAccountResult {
    data class Updated(
        val account: Account,
    ) : UpdateAccountResult

    data object NotFound : UpdateAccountResult
}

class UpdateAccountService(
    private val accountRepository: AccountRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun update(
        id: AccountId,
        name: String,
        description: String?,
    ): UpdateAccountResult =
        transactionRunner.runInTransaction {
            val existing = accountRepository.findByIdForUpdate(id) ?: return@runInTransaction UpdateAccountResult.NotFound
            val updated = existing.withUpdatedDetails(name, description, Instant.now(clock))
            accountRepository.save(updated)
            UpdateAccountResult.Updated(updated)
        }
}
