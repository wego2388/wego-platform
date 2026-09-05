package com.wego.accounting.application

import com.wego.accounting.domain.JournalEntry
import com.wego.accounting.domain.JournalEntryId
import com.wego.transaction.TransactionRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

sealed interface ReverseJournalEntryResult {
    data class Reversed(
        val reversalEntry: JournalEntry,
    ) : ReverseJournalEntryResult

    data object NotFound : ReverseJournalEntryResult

    data object AlreadyReversed : ReverseJournalEntryResult
}

class ReverseJournalEntryService(
    private val journalEntryRepository: JournalEntryRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun reverse(
        id: JournalEntryId,
        reason: String,
        actorUserId: UUID?,
        correlationId: UUID?,
    ): ReverseJournalEntryResult =
        transactionRunner.runInTransaction {
            val original = journalEntryRepository.findById(id) ?: return@runInTransaction ReverseJournalEntryResult.NotFound
            // A pre-check for the common case — the DB's own unique partial
            // index on reversal_of_entry_id is the real backstop against a
            // genuine race between two concurrent reversal requests (see
            // AccountingExceptionHandler's DataIntegrityViolationException
            // handler for that path).
            if (journalEntryRepository.findReversalOf(id) != null) return@runInTransaction ReverseJournalEntryResult.AlreadyReversed

            val reversal =
                JournalEntry.reverse(
                    newId = JournalEntryId.generate(),
                    original = original,
                    entryDate = LocalDate.now(clock),
                    reason = reason,
                    postedByUserId = actorUserId,
                    correlationId = correlationId,
                    now = Instant.now(clock),
                )
            journalEntryRepository.save(reversal)
            ReverseJournalEntryResult.Reversed(reversal)
        }
}
