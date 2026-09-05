package com.wego.accounting.application

import com.wego.accounting.domain.AccountId
import com.wego.accounting.domain.JournalEntry
import com.wego.accounting.domain.JournalEntryId
import java.time.LocalDate

class JournalEntryQueryService(
    private val journalEntryRepository: JournalEntryRepository,
) {
    fun findById(id: JournalEntryId): JournalEntry? = journalEntryRepository.findById(id)

    fun list(
        from: LocalDate?,
        to: LocalDate?,
        accountId: AccountId?,
        reference: String?,
        page: Int = 0,
        size: Int = Pagination.DEFAULT_PAGE_SIZE,
    ): List<JournalEntry> =
        journalEntryRepository.findAll(
            from,
            to,
            accountId,
            reference,
            limit = Pagination.boundedSize(size),
            offset = Pagination.offsetFor(page, size),
        )
}
