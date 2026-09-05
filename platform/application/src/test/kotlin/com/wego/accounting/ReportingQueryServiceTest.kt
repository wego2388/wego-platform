package com.wego.accounting

import com.wego.accounting.application.AccountDirectionTotal
import com.wego.accounting.application.AccountRepository
import com.wego.accounting.application.JournalEntryRepository
import com.wego.accounting.application.ReportingQueryService
import com.wego.accounting.domain.Account
import com.wego.accounting.domain.AccountId
import com.wego.accounting.domain.AccountType
import com.wego.accounting.domain.JournalEntry
import com.wego.accounting.domain.JournalEntryId
import com.wego.accounting.domain.JournalLineDirection
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Fast, isolated tests for the real accounting math these reports exist
 * to get right — in-memory fakes, matching this codebase's established
 * `DiversTestFakes.kt`-style pattern, verifying the actual formulas
 * (trial-balance net direction, retained-earnings synthesis, the
 * fundamental Assets == Liabilities + Equity invariant) rather than just
 * that the endpoints respond.
 */
class ReportingQueryServiceTest {
    private val cash = Account.create(AccountId.generate(), "1000", "Cash", AccountType.ASSET, null, null, null, Instant.EPOCH)
    private val payable =
        Account.create(
            AccountId.generate(),
            "2000",
            "Accounts Payable",
            AccountType.LIABILITY,
            null,
            null,
            null,
            Instant.EPOCH,
        )
    private val equity = Account.create(AccountId.generate(), "3000", "Owner's Equity", AccountType.EQUITY, null, null, null, Instant.EPOCH)
    private val revenue =
        Account.create(
            AccountId.generate(),
            "4000",
            "Service Revenue",
            AccountType.REVENUE,
            null,
            null,
            null,
            Instant.EPOCH,
        )
    private val expense = Account.create(AccountId.generate(), "5000", "Rent Expense", AccountType.EXPENSE, null, null, null, Instant.EPOCH)
    private val allAccounts = listOf(cash, payable, equity, revenue, expense)

    private class FakeAccountRepository(
        private val accounts: List<Account>,
    ) : AccountRepository {
        override fun findById(id: AccountId): Account? = accounts.find { it.id == id }

        override fun findByIdForUpdate(id: AccountId): Account? = findById(id)

        override fun findByCode(code: String): Account? = accounts.find { it.code == code }

        override fun findAll(
            accountType: AccountType?,
            activeOnly: Boolean,
            search: String?,
            limit: Int,
            offset: Int,
        ): List<Account> = accounts

        override fun save(account: Account) = Unit

        override fun findAllAccounts(): List<Account> = accounts
    }

    private class FakeJournalEntryRepository(
        private val totals: List<AccountDirectionTotal>,
    ) : JournalEntryRepository {
        override fun findById(id: JournalEntryId): JournalEntry? = null

        override fun findAll(
            from: LocalDate?,
            to: LocalDate?,
            accountId: AccountId?,
            reference: String?,
            limit: Int,
            offset: Int,
        ): List<JournalEntry> = emptyList()

        override fun findReversalOf(id: JournalEntryId): JournalEntry? = null

        override fun save(journalEntry: JournalEntry) = Unit

        override fun sumLinesAsOf(asOfDate: LocalDate): List<AccountDirectionTotal> = totals

        override fun sumLinesBetween(
            from: LocalDate,
            to: LocalDate,
        ): List<AccountDirectionTotal> = totals
    }

    @Test
    fun `trial balance nets each account to whichever column it really sits on`() {
        // Cash: 1000 debit, 200 credit -> nets to a 800 debit balance.
        val totals =
            listOf(
                AccountDirectionTotal(cash.id, JournalLineDirection.DEBIT, BigDecimal("1000.00")),
                AccountDirectionTotal(cash.id, JournalLineDirection.CREDIT, BigDecimal("200.00")),
                AccountDirectionTotal(payable.id, JournalLineDirection.CREDIT, BigDecimal("800.00")),
            )
        val service = ReportingQueryService(FakeAccountRepository(allAccounts), FakeJournalEntryRepository(totals))

        val trialBalance = service.trialBalance(LocalDate.parse("2026-12-31"))

        val cashLine = trialBalance.lines.first { it.accountId == cash.id }
        assertThat(cashLine.debitBalance).isEqualByComparingTo(BigDecimal("800.00"))
        assertThat(cashLine.creditBalance).isEqualByComparingTo(BigDecimal.ZERO)
        val payableLine = trialBalance.lines.first { it.accountId == payable.id }
        assertThat(payableLine.creditBalance).isEqualByComparingTo(BigDecimal("800.00"))
        assertThat(payableLine.debitBalance).isEqualByComparingTo(BigDecimal.ZERO)
        // The real point of a trial balance: total debits equal total credits.
        assertThat(trialBalance.totalDebits).isEqualByComparingTo(trialBalance.totalCredits)
    }

    @Test
    fun `income statement computes net income as revenue minus expenses within the period`() {
        val totals =
            listOf(
                AccountDirectionTotal(revenue.id, JournalLineDirection.CREDIT, BigDecimal("5000.00")),
                AccountDirectionTotal(expense.id, JournalLineDirection.DEBIT, BigDecimal("1200.00")),
            )
        val service = ReportingQueryService(FakeAccountRepository(allAccounts), FakeJournalEntryRepository(totals))

        val statement = service.incomeStatement(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"))

        assertThat(statement.totalRevenue).isEqualByComparingTo(BigDecimal("5000.00"))
        assertThat(statement.totalExpenses).isEqualByComparingTo(BigDecimal("1200.00"))
        assertThat(statement.netIncome).isEqualByComparingTo(BigDecimal("3800.00"))
    }

    @Test
    fun `balance sheet includes a real synthesized retained earnings line and the whole thing actually balances`() {
        // A real, small, internally consistent set of postings: owner
        // contributed 10000 cash as equity; the business earned 5000
        // revenue in cash and paid 1200 rent in cash — net income 3800,
        // ending cash 10000 + 5000 - 1200 = 13800.
        val totals =
            listOf(
                AccountDirectionTotal(cash.id, JournalLineDirection.DEBIT, BigDecimal("15000.00")),
                AccountDirectionTotal(cash.id, JournalLineDirection.CREDIT, BigDecimal("1200.00")),
                AccountDirectionTotal(equity.id, JournalLineDirection.CREDIT, BigDecimal("10000.00")),
                AccountDirectionTotal(revenue.id, JournalLineDirection.CREDIT, BigDecimal("5000.00")),
                AccountDirectionTotal(expense.id, JournalLineDirection.DEBIT, BigDecimal("1200.00")),
            )
        val service = ReportingQueryService(FakeAccountRepository(allAccounts), FakeJournalEntryRepository(totals))

        val balanceSheet = service.balanceSheet(LocalDate.parse("2026-12-31"))

        assertThat(balanceSheet.totalAssets).isEqualByComparingTo(BigDecimal("13800.00"))
        val retainedEarningsLine = balanceSheet.equityLines.first { it.accountId == null }
        assertThat(retainedEarningsLine.name).isEqualTo("Retained Earnings (accumulated)")
        assertThat(retainedEarningsLine.amount).isEqualByComparingTo(BigDecimal("3800.00"))
        assertThat(balanceSheet.totalEquity).isEqualByComparingTo(BigDecimal("13800.00"))
        // The fundamental accounting equation.
        assertThat(balanceSheet.totalAssets).isEqualByComparingTo(balanceSheet.totalLiabilities + balanceSheet.totalEquity)
    }
}
