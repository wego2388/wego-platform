package com.wego.accounting.application

import com.wego.accounting.domain.Account
import com.wego.accounting.domain.AccountType
import com.wego.accounting.domain.JournalLineDirection
import java.math.BigDecimal
import java.time.LocalDate

class ReportingQueryService(
    private val accountRepository: AccountRepository,
    private val journalEntryRepository: JournalEntryRepository,
) {
    fun trialBalance(asOfDate: LocalDate): TrialBalance {
        val accounts = accountRepository.findAllAccounts()
        val totalsByAccount = journalEntryRepository.sumLinesAsOf(asOfDate).groupBy { it.accountId }

        val lines =
            accounts.map { account ->
                val debitTotal =
                    totalsByAccount[account.id]?.firstOrNull { it.direction == JournalLineDirection.DEBIT }?.total ?: BigDecimal.ZERO
                val creditTotal =
                    totalsByAccount[account.id]?.firstOrNull { it.direction == JournalLineDirection.CREDIT }?.total ?: BigDecimal.ZERO
                val net = debitTotal - creditTotal
                TrialBalanceLine(
                    accountId = account.id,
                    code = account.code,
                    name = account.name,
                    accountType = account.accountType,
                    debitBalance = if (net > BigDecimal.ZERO) net else BigDecimal.ZERO,
                    creditBalance = if (net < BigDecimal.ZERO) net.negate() else BigDecimal.ZERO,
                )
            }
        return TrialBalance(asOfDate, lines)
    }

    fun incomeStatement(
        from: LocalDate,
        to: LocalDate,
    ): IncomeStatement {
        val accounts = accountRepository.findAllAccounts().associateBy { it.id }
        val totalsByAccount = journalEntryRepository.sumLinesBetween(from, to).groupBy { it.accountId }

        val revenueLines = mutableListOf<IncomeStatementLine>()
        val expenseLines = mutableListOf<IncomeStatementLine>()
        accounts.values.forEach { account ->
            if (account.accountType != AccountType.REVENUE && account.accountType != AccountType.EXPENSE) return@forEach
            val debitTotal =
                totalsByAccount[account.id]?.firstOrNull { it.direction == JournalLineDirection.DEBIT }?.total ?: BigDecimal.ZERO
            val creditTotal =
                totalsByAccount[account.id]?.firstOrNull { it.direction == JournalLineDirection.CREDIT }?.total ?: BigDecimal.ZERO
            when (account.accountType) {
                AccountType.REVENUE -> {
                    val amount = creditTotal - debitTotal
                    if (amount != BigDecimal.ZERO) revenueLines.add(IncomeStatementLine(account.id, account.code, account.name, amount))
                }
                AccountType.EXPENSE -> {
                    val amount = debitTotal - creditTotal
                    if (amount != BigDecimal.ZERO) expenseLines.add(IncomeStatementLine(account.id, account.code, account.name, amount))
                }
            }
        }
        return IncomeStatement(from, to, revenueLines.sortedBy { it.code }, expenseLines.sortedBy { it.code })
    }

    fun balanceSheet(asOfDate: LocalDate): BalanceSheet {
        val accounts = accountRepository.findAllAccounts()
        val totalsByAccount = journalEntryRepository.sumLinesAsOf(asOfDate).groupBy { it.accountId }

        fun netFor(
            account: Account,
            debitPositive: Boolean,
        ): BigDecimal {
            val debitTotal =
                totalsByAccount[account.id]?.firstOrNull { it.direction == JournalLineDirection.DEBIT }?.total ?: BigDecimal.ZERO
            val creditTotal =
                totalsByAccount[account.id]?.firstOrNull { it.direction == JournalLineDirection.CREDIT }?.total ?: BigDecimal.ZERO
            return if (debitPositive) debitTotal - creditTotal else creditTotal - debitTotal
        }

        val assetLines =
            accounts
                .filter { it.accountType == AccountType.ASSET }
                .map { BalanceSheetLine(it.id, it.code, it.name, netFor(it, debitPositive = true)) }
                .sortedBy { it.code }
        val liabilityLines =
            accounts
                .filter { it.accountType == AccountType.LIABILITY }
                .map { BalanceSheetLine(it.id, it.code, it.name, netFor(it, debitPositive = false)) }
                .sortedBy { it.code }
        val realEquityLines =
            accounts
                .filter { it.accountType == AccountType.EQUITY }
                .map { BalanceSheetLine(it.id, it.code, it.name, netFor(it, debitPositive = false)) }
                .sortedBy { it.code }

        // Retained earnings, computed on the fly — see BalanceSheet's own
        // doc comment for why this is the correct technique for a system
        // with no formal period-closing step, not a shortcut.
        //
        // (creditTotal - debitTotal) is each account's contribution to net
        // income for *both* types, not two different formulas: a revenue
        // account's normal credit balance adds straight to income, and an
        // expense account's normal debit balance reduces it — which is
        // exactly -(debitTotal - creditTotal), i.e. the same
        // (creditTotal - debitTotal) expression either way.
        val retainedEarnings =
            accounts
                .filter { it.accountType == AccountType.REVENUE || it.accountType == AccountType.EXPENSE }
                .sumOf { netFor(it, debitPositive = false) }
        val equityLines = realEquityLines + BalanceSheetLine(null, null, "Retained Earnings (accumulated)", retainedEarnings)

        return BalanceSheet(asOfDate, assetLines, liabilityLines, equityLines)
    }
}
