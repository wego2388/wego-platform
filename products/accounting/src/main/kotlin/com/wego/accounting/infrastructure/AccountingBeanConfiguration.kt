package com.wego.accounting.infrastructure

import com.wego.accounting.application.AccountQueryService
import com.wego.accounting.application.AccountRepository
import com.wego.accounting.application.CreateAccountService
import com.wego.accounting.application.DeactivateAccountService
import com.wego.accounting.application.JournalEntryQueryService
import com.wego.accounting.application.JournalEntryRepository
import com.wego.accounting.application.PostJournalEntryService
import com.wego.accounting.application.ReactivateAccountService
import com.wego.accounting.application.ReverseJournalEntryService
import com.wego.accounting.application.UpdateAccountService
import com.wego.transaction.TransactionRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class AccountingBeanConfiguration {
    @Bean
    fun createAccountService(
        accountRepository: AccountRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): CreateAccountService = CreateAccountService(accountRepository, transactionRunner, clock)

    @Bean
    fun updateAccountService(
        accountRepository: AccountRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): UpdateAccountService = UpdateAccountService(accountRepository, transactionRunner, clock)

    @Bean
    fun deactivateAccountService(
        accountRepository: AccountRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): DeactivateAccountService = DeactivateAccountService(accountRepository, transactionRunner, clock)

    @Bean
    fun reactivateAccountService(
        accountRepository: AccountRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): ReactivateAccountService = ReactivateAccountService(accountRepository, transactionRunner, clock)

    @Bean
    fun accountQueryService(accountRepository: AccountRepository): AccountQueryService = AccountQueryService(accountRepository)

    @Bean
    fun postJournalEntryService(
        accountRepository: AccountRepository,
        journalEntryRepository: JournalEntryRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): PostJournalEntryService = PostJournalEntryService(accountRepository, journalEntryRepository, transactionRunner, clock)

    @Bean
    fun reverseJournalEntryService(
        journalEntryRepository: JournalEntryRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): ReverseJournalEntryService = ReverseJournalEntryService(journalEntryRepository, transactionRunner, clock)

    @Bean
    fun journalEntryQueryService(journalEntryRepository: JournalEntryRepository): JournalEntryQueryService =
        JournalEntryQueryService(journalEntryRepository)
}
