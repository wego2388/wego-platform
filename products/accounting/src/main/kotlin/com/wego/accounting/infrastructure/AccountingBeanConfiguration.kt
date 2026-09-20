package com.wego.accounting.infrastructure

import com.wego.accounting.application.AccountQueryService
import com.wego.accounting.application.AccountRepository
import com.wego.accounting.application.CreateAccountService
import com.wego.accounting.application.DeactivateAccountService
import com.wego.accounting.application.JournalEntryQueryService
import com.wego.accounting.application.JournalEntryRepository
import com.wego.accounting.application.PostJournalEntryService
import com.wego.accounting.application.ReactivateAccountService
import com.wego.accounting.application.ReportingQueryService
import com.wego.accounting.application.ReverseJournalEntryService
import com.wego.accounting.application.UpdateAccountService
import com.wego.identity.AuthenticatedApiPrefix
import com.wego.transaction.TransactionRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class AccountingBeanConfiguration {
    // Declares this product's own API surface to kernel security — see
    // AuthenticatedApiPrefix's doc comment and DiversBeanConfiguration's
    // identical pattern. This bean contribution replaces the hardcoded
    // "/api/v1/accounting/**" rule SecurityConfiguration carried before the
    // generalized-prefix mechanism existed.
    @Bean
    fun accountingAuthenticatedApiPrefix(): AuthenticatedApiPrefix = AuthenticatedApiPrefix("/api/v1/accounting/**")

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

    @Bean
    fun reportingQueryService(
        accountRepository: AccountRepository,
        journalEntryRepository: JournalEntryRepository,
    ): ReportingQueryService = ReportingQueryService(accountRepository, journalEntryRepository)
}
