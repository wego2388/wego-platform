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
}
