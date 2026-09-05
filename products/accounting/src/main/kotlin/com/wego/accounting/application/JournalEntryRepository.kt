package com.wego.accounting.application

import com.wego.accounting.domain.AccountId
import com.wego.accounting.domain.JournalEntry
import com.wego.accounting.domain.JournalEntryId
import java.time.LocalDate

interface JournalEntryRepository {
    fun findById(id: JournalEntryId): JournalEntry?

    fun findAll(
        from: LocalDate?,
        to: LocalDate?,
        accountId: AccountId?,
        reference: String?,
        limit: Int,
        offset: Int,
    ): List<JournalEntry>

    /** The entry (if any) whose `reversalOfEntryId` points at [id] — an original entry can have at most one, enforced by the DB's own unique partial index. */
    fun findReversalOf(id: JournalEntryId): JournalEntry?

    fun save(journalEntry: JournalEntry)

    /** Every account's DEBIT/CREDIT totals across all entries dated on or before [asOfDate] — the raw material for a trial balance or a balance sheet, both cumulative-to-date reports. */
    fun sumLinesAsOf(asOfDate: LocalDate): List<AccountDirectionTotal>

    /** Every account's DEBIT/CREDIT totals across entries dated within [from]..[to] inclusive — the raw material for an income statement, a period report, not a cumulative one. */
    fun sumLinesBetween(
        from: LocalDate,
        to: LocalDate,
    ): List<AccountDirectionTotal>
}
