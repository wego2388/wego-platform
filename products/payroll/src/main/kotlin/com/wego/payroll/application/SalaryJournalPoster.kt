package com.wego.payroll.application

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

sealed interface PostSalaryJournalResult {
    data class Posted(
        val journalEntryId: UUID,
    ) : PostSalaryJournalResult

    data object SalariesExpenseAccountNotFound : PostSalaryJournalResult

    data object SalariesExpenseAccountInactive : PostSalaryJournalResult

    data object WagesPayableAccountNotFound : PostSalaryJournalResult

    data object WagesPayableAccountInactive : PostSalaryJournalResult
}

/**
 * The minimal cross-module write this module needs against
 * `com.wego.accounting`: post the one, always-balanced-by-construction
 * shape payroll ever produces — DEBIT the real "Salaries Expense" account
 * (code `5000`, seeded by V12) for the total, CREDIT the real "Wages
 * Payable" account (code `2100`, also seeded by V12) for the same total.
 *
 * A module-local port, not a direct import of `com.wego.accounting.application`
 * — Modulith's own boundary verification rejects that (proven empirically;
 * see the WEGO-012 Phase 6 board entry). This intentionally does not
 * expose a generic "post any journal entry" capability — only this one,
 * real, payroll-shaped posting, so the account codes and the DEBIT/CREDIT
 * shape stay this module's own concern, not a leaked accounting API.
 *
 * A cash-disbursement entry (Wages Payable DEBIT / Cash CREDIT, once
 * wages are actually transferred) is deliberately out of scope here — the
 * accountant posts that manually through Accounting's own journal-entries
 * screen once the money actually moves, the same "don't auto-integrate
 * every real-world workflow permutation" boundary this packet has drawn
 * elsewhere.
 */
interface SalaryJournalPoster {
    fun post(
        entryDate: LocalDate,
        description: String,
        reference: String?,
        currencyCode: String,
        totalAmount: BigDecimal,
        postedByUserId: UUID?,
        correlationId: UUID?,
    ): PostSalaryJournalResult
}
