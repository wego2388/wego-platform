package com.wego.accounting.application

import com.wego.accounting.domain.AccountId
import com.wego.accounting.domain.JournalEntry
import com.wego.accounting.domain.JournalEntryId
import com.wego.accounting.domain.JournalLine
import com.wego.accounting.domain.JournalLineDirection
import com.wego.accounting.domain.JournalLineId
import com.wego.transaction.TransactionRunner
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PostedLineInput(
    val accountId: AccountId,
    val direction: JournalLineDirection,
    val amount: BigDecimal,
)

sealed interface PostJournalEntryResult {
    data class Posted(
        val journalEntry: JournalEntry,
    ) : PostJournalEntryResult

    data object TooFewLines : PostJournalEntryResult

    data object MissingDebitOrCredit : PostJournalEntryResult

    data object Unbalanced : PostJournalEntryResult

    data class AccountNotFound(
        val accountId: AccountId,
    ) : PostJournalEntryResult

    data class AccountInactive(
        val accountId: AccountId,
    ) : PostJournalEntryResult
}

/**
 * Validated explicitly, before any domain object is constructed, rather
 * than relying on `JournalEntry`'s own `require(...)` invariants to
 * surface as a caught `IllegalArgumentException`: whether an entry
 * balances is real business validation of user-submitted amounts, not a
 * structural invariant a caller could only violate through a bug — it
 * deserves its own specific, documented error code, the same way every
 * other business-rule rejection in this codebase does.
 */
class PostJournalEntryService(
    private val accountRepository: AccountRepository,
    private val journalEntryRepository: JournalEntryRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun post(
        entryDate: LocalDate,
        description: String,
        reference: String?,
        currencyCode: String,
        lines: List<PostedLineInput>,
        postedByUserId: UUID?,
        correlationId: UUID?,
    ): PostJournalEntryResult =
        transactionRunner.runInTransaction {
            if (lines.size < 2) return@runInTransaction PostJournalEntryResult.TooFewLines

            val debitTotal = lines.filter { it.direction == JournalLineDirection.DEBIT }.sumOf { it.amount }
            val creditTotal = lines.filter { it.direction == JournalLineDirection.CREDIT }.sumOf { it.amount }
            if (debitTotal <= BigDecimal.ZERO ||
                creditTotal <= BigDecimal.ZERO
            ) {
                return@runInTransaction PostJournalEntryResult.MissingDebitOrCredit
            }
            if (debitTotal.compareTo(creditTotal) != 0) return@runInTransaction PostJournalEntryResult.Unbalanced

            for (line in lines) {
                val account =
                    accountRepository.findById(line.accountId)
                        ?: return@runInTransaction PostJournalEntryResult.AccountNotFound(line.accountId)
                if (!account.isActive) return@runInTransaction PostJournalEntryResult.AccountInactive(line.accountId)
            }

            val journalEntry =
                JournalEntry.post(
                    id = JournalEntryId.generate(),
                    entryDate = entryDate,
                    description = description,
                    reference = reference,
                    currencyCode = currencyCode,
                    lines =
                        lines.mapIndexed { index, input ->
                            JournalLine(JournalLineId.generate(), input.accountId, input.direction, input.amount, index)
                        },
                    postedByUserId = postedByUserId,
                    correlationId = correlationId,
                    now = Instant.now(clock),
                )
            journalEntryRepository.save(journalEntry)
            PostJournalEntryResult.Posted(journalEntry)
        }
}
