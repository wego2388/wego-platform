package com.wego.accounting.application

import com.wego.accounting.domain.Account
import com.wego.accounting.domain.AccountId
import com.wego.transaction.TransactionRunner
import java.time.Clock
import java.time.Instant

sealed interface ReactivateAccountResult {
    data class Reactivated(
        val account: Account,
    ) : ReactivateAccountResult

    data object NotFound : ReactivateAccountResult

    data object AlreadyActive : ReactivateAccountResult
}

class ReactivateAccountService(
    private val accountRepository: AccountRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun reactivate(id: AccountId): ReactivateAccountResult =
        transactionRunner.runInTransaction {
            val existing = accountRepository.findByIdForUpdate(id) ?: return@runInTransaction ReactivateAccountResult.NotFound
            if (existing.isActive) return@runInTransaction ReactivateAccountResult.AlreadyActive

            existing.reactivate(Instant.now(clock))
            accountRepository.save(existing)
            ReactivateAccountResult.Reactivated(existing)
        }
}
