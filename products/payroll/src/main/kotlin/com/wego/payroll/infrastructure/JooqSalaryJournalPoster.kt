package com.wego.payroll.infrastructure

import com.wego.generated.jooq.tables.AccountingAccount.ACCOUNTING_ACCOUNT
import com.wego.generated.jooq.tables.AccountingJournalEntry.ACCOUNTING_JOURNAL_ENTRY
import com.wego.generated.jooq.tables.AccountingJournalLine.ACCOUNTING_JOURNAL_LINE
import com.wego.payroll.application.PostSalaryJournalResult
import com.wego.payroll.application.SalaryJournalPoster
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

private const val SALARIES_EXPENSE_ACCOUNT_CODE = "5000"
private const val WAGES_PAYABLE_ACCOUNT_CODE = "2100"

/**
 * Writes directly to `accounting_journal_entry`/`accounting_journal_line`
 * via jOOQ rather than constructing a real `com.wego.accounting.domain.JournalEntry`
 * — Modulith's boundary verification forbids importing that type from
 * this module (see `SalaryJournalPoster`'s own doc comment). This is safe
 * without replicating `JournalEntry`'s balance-check invariant: the
 * DEBIT/CREDIT pair posted here is balanced by construction (same
 * `totalAmount` on both lines), and every other constraint
 * (`amount > 0`, a real `currency_code` format, a real account reference)
 * is still enforced by the DB's own CHECK/FK constraints either way.
 */
@Component
class JooqSalaryJournalPoster(
    private val dsl: DSLContext,
) : SalaryJournalPoster {
    @Transactional
    override fun post(
        entryDate: LocalDate,
        description: String,
        reference: String?,
        currencyCode: String,
        totalAmount: BigDecimal,
        postedByUserId: UUID?,
        correlationId: UUID?,
    ): PostSalaryJournalResult {
        val salariesExpense =
            dsl
                .select(ACCOUNTING_ACCOUNT.ID, ACCOUNTING_ACCOUNT.IS_ACTIVE)
                .from(ACCOUNTING_ACCOUNT)
                .where(ACCOUNTING_ACCOUNT.CODE.eq(SALARIES_EXPENSE_ACCOUNT_CODE))
                .fetchOne() ?: return PostSalaryJournalResult.SalariesExpenseAccountNotFound
        if (!salariesExpense.value2()) return PostSalaryJournalResult.SalariesExpenseAccountInactive

        val wagesPayable =
            dsl
                .select(ACCOUNTING_ACCOUNT.ID, ACCOUNTING_ACCOUNT.IS_ACTIVE)
                .from(ACCOUNTING_ACCOUNT)
                .where(ACCOUNTING_ACCOUNT.CODE.eq(WAGES_PAYABLE_ACCOUNT_CODE))
                .fetchOne() ?: return PostSalaryJournalResult.WagesPayableAccountNotFound
        if (!wagesPayable.value2()) return PostSalaryJournalResult.WagesPayableAccountInactive

        val entryId = UUID.randomUUID()
        val now = OffsetDateTime.ofInstant(java.time.Instant.now(), ZoneOffset.UTC)
        dsl
            .insertInto(ACCOUNTING_JOURNAL_ENTRY)
            .set(ACCOUNTING_JOURNAL_ENTRY.ID, entryId)
            .set(ACCOUNTING_JOURNAL_ENTRY.ENTRY_DATE, entryDate)
            .set(ACCOUNTING_JOURNAL_ENTRY.DESCRIPTION, description)
            .set(ACCOUNTING_JOURNAL_ENTRY.REFERENCE, reference)
            .set(ACCOUNTING_JOURNAL_ENTRY.CURRENCY_CODE, currencyCode)
            .set(ACCOUNTING_JOURNAL_ENTRY.POSTED_BY_USER_ID, postedByUserId)
            .set(ACCOUNTING_JOURNAL_ENTRY.POSTED_AT, now)
            .set(ACCOUNTING_JOURNAL_ENTRY.CORRELATION_ID, correlationId)
            .execute()

        dsl
            .insertInto(ACCOUNTING_JOURNAL_LINE)
            .set(ACCOUNTING_JOURNAL_LINE.ID, UUID.randomUUID())
            .set(ACCOUNTING_JOURNAL_LINE.JOURNAL_ENTRY_ID, entryId)
            .set(ACCOUNTING_JOURNAL_LINE.ACCOUNT_ID, salariesExpense.value1())
            .set(ACCOUNTING_JOURNAL_LINE.DIRECTION, "DEBIT")
            .set(ACCOUNTING_JOURNAL_LINE.AMOUNT, totalAmount)
            .set(ACCOUNTING_JOURNAL_LINE.LINE_ORDER, 0)
            .execute()

        dsl
            .insertInto(ACCOUNTING_JOURNAL_LINE)
            .set(ACCOUNTING_JOURNAL_LINE.ID, UUID.randomUUID())
            .set(ACCOUNTING_JOURNAL_LINE.JOURNAL_ENTRY_ID, entryId)
            .set(ACCOUNTING_JOURNAL_LINE.ACCOUNT_ID, wagesPayable.value1())
            .set(ACCOUNTING_JOURNAL_LINE.DIRECTION, "CREDIT")
            .set(ACCOUNTING_JOURNAL_LINE.AMOUNT, totalAmount)
            .set(ACCOUNTING_JOURNAL_LINE.LINE_ORDER, 1)
            .execute()

        return PostSalaryJournalResult.Posted(entryId)
    }
}
