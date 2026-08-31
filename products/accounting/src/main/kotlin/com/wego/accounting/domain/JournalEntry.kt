package com.wego.accounting.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A permanent, immutable double-entry posting — there is deliberately no
 * edit or delete; a mistake is corrected with a real reversing entry (see
 * [reverse]), matching standard accounting practice and this codebase's
 * existing preference for append-only financial/audit history.
 *
 * Whether every referenced account exists and is active, and whether
 * amounts came from a real posting request, is the posting service's job
 * (it needs repository access this class doesn't have) — this class only
 * enforces the one invariant it fully owns: the entry actually balances.
 */
class JournalEntry(
    val id: JournalEntryId,
    val entryDate: LocalDate,
    val description: String,
    val reference: String?,
    val currencyCode: String,
    val lines: List<JournalLine>,
    val reversalOfEntryId: JournalEntryId?,
    val postedByUserId: UUID?,
    val postedAt: Instant,
    val correlationId: UUID?,
) {
    init {
        require(description.isNotBlank()) { "Journal entry description must not be blank" }
        require(currencyCode.matches(Regex("^[A-Z]{3}$"))) { "currencyCode must be a 3-letter uppercase ISO 4217 code" }
        require(lines.size >= 2) { "A journal entry needs at least 2 lines" }
        require(debitTotal > BigDecimal.ZERO && creditTotal > BigDecimal.ZERO) {
            "A journal entry needs at least one debit line and at least one credit line"
        }
        require(
            debitTotal.compareTo(creditTotal) == 0,
        ) { "A journal entry must balance: debits ($debitTotal) must equal credits ($creditTotal)" }
    }

    val debitTotal: BigDecimal get() = lines.filter { it.direction == JournalLineDirection.DEBIT }.sumOf { it.amount }
    val creditTotal: BigDecimal get() = lines.filter { it.direction == JournalLineDirection.CREDIT }.sumOf { it.amount }
    val isReversal: Boolean get() = reversalOfEntryId != null

    companion object {
        fun post(
            id: JournalEntryId,
            entryDate: LocalDate,
            description: String,
            reference: String?,
            currencyCode: String,
            lines: List<JournalLine>,
            postedByUserId: UUID?,
            correlationId: UUID?,
            now: Instant,
        ): JournalEntry =
            JournalEntry(
                id = id,
                entryDate = entryDate,
                description = description,
                reference = reference,
                currencyCode = currencyCode,
                lines = lines,
                reversalOfEntryId = null,
                postedByUserId = postedByUserId,
                postedAt = now,
                correlationId = correlationId,
            )

        /** Every line's direction flipped, same accounts/amounts/currency as [original] — the standard reversing-entry shape. */
        fun reverse(
            newId: JournalEntryId,
            original: JournalEntry,
            entryDate: LocalDate,
            reason: String,
            postedByUserId: UUID?,
            correlationId: UUID?,
            now: Instant,
        ): JournalEntry {
            val flippedLines =
                original.lines.map { line ->
                    line.copy(
                        id = JournalLineId.generate(),
                        direction =
                            if (line.direction ==
                                JournalLineDirection.DEBIT
                            ) {
                                JournalLineDirection.CREDIT
                            } else {
                                JournalLineDirection.DEBIT
                            },
                    )
                }
            return JournalEntry(
                id = newId,
                entryDate = entryDate,
                description = "Reversal: $reason",
                reference = original.reference,
                currencyCode = original.currencyCode,
                lines = flippedLines,
                reversalOfEntryId = original.id,
                postedByUserId = postedByUserId,
                postedAt = now,
                correlationId = correlationId,
            )
        }
    }
}
