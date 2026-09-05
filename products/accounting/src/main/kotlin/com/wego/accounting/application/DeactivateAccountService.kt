package com.wego.accounting.application

import com.wego.accounting.domain.Account
import com.wego.accounting.domain.AccountId
import com.wego.transaction.TransactionRunner
import java.time.Clock
import java.time.Instant

sealed interface DeactivateAccountResult {
    data class Deactivated(
        val account: Account,
    ) : DeactivateAccountResult

    data object NotFound : DeactivateAccountResult

    data object AlreadyInactive : DeactivateAccountResult
}

class DeactivateAccountService(
    private val accountRepository: AccountRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun deactivate(id: AccountId): DeactivateAccountResult =
        transactionRunner.runInTransaction {
            val existing = accountRepository.findByIdForUpdate(id) ?: return@runInTransaction DeactivateAccountResult.NotFound
            if (!existing.isActive) return@runInTransaction DeactivateAccountResult.AlreadyInactive

            existing.deactivate(Instant.now(clock))
            accountRepository.save(existing)
            DeactivateAccountResult.Deactivated(existing)
        }
}
