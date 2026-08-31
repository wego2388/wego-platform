package com.wego.accounting.infrastructure

import com.wego.accounting.application.JournalEntryRepository
import com.wego.accounting.domain.AccountId
import com.wego.accounting.domain.JournalEntry
import com.wego.accounting.domain.JournalEntryId
import com.wego.accounting.domain.JournalLine
import com.wego.accounting.domain.JournalLineDirection
import com.wego.accounting.domain.JournalLineId
import com.wego.generated.jooq.tables.AccountingJournalEntry.ACCOUNTING_JOURNAL_ENTRY
import com.wego.generated.jooq.tables.AccountingJournalLine.ACCOUNTING_JOURNAL_LINE
import com.wego.generated.jooq.tables.records.AccountingJournalEntryRecord
import com.wego.generated.jooq.tables.records.AccountingJournalLineRecord
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class JooqJournalEntryRepository(
    private val dsl: DSLContext,
) : JournalEntryRepository {
    @Transactional(readOnly = true)
    override fun findById(id: JournalEntryId): JournalEntry? {
        val record = dsl.selectFrom(ACCOUNTING_JOURNAL_ENTRY).where(ACCOUNTING_JOURNAL_ENTRY.ID.eq(id.value)).fetchOne() ?: return null
        return toDomain(record, linesFor(listOf(id.value))[id.value].orEmpty())
    }

    @Transactional(readOnly = true)
    override fun findAll(
        from: LocalDate?,
        to: LocalDate?,
        accountId: AccountId?,
        reference: String?,
        limit: Int,
        offset: Int,
    ): List<JournalEntry> {
        var condition = DSL.noCondition()
        if (from != null) condition = condition.and(ACCOUNTING_JOURNAL_ENTRY.ENTRY_DATE.ge(from))
        if (to != null) condition = condition.and(ACCOUNTING_JOURNAL_ENTRY.ENTRY_DATE.le(to))
        if (!reference.isNullOrBlank()) condition = condition.and(ACCOUNTING_JOURNAL_ENTRY.REFERENCE.containsIgnoreCase(reference.trim()))
        if (accountId != null) {
            condition =
                condition.and(
                    ACCOUNTING_JOURNAL_ENTRY.ID.`in`(
                        dsl.select(ACCOUNTING_JOURNAL_LINE.JOURNAL_ENTRY_ID).from(ACCOUNTING_JOURNAL_LINE).where(
                            ACCOUNTING_JOURNAL_LINE.ACCOUNT_ID.eq(accountId.value),
                        ),
                    ),
                )
        }
        val entryRecords =
            dsl
                .selectFrom(ACCOUNTING_JOURNAL_ENTRY)
                .where(condition)
                .orderBy(ACCOUNTING_JOURNAL_ENTRY.ENTRY_DATE.desc(), ACCOUNTING_JOURNAL_ENTRY.POSTED_AT.desc(), ACCOUNTING_JOURNAL_ENTRY.ID)
                .limit(limit)
                .offset(offset)
                .fetch()
        val lines = linesFor(entryRecords.map { it.id })
        return entryRecords.map { toDomain(it, lines[it.id].orEmpty()) }
    }

    @Transactional(readOnly = true)
    override fun findReversalOf(id: JournalEntryId): JournalEntry? {
        val record =
            dsl
                .selectFrom(ACCOUNTING_JOURNAL_ENTRY)
                .where(ACCOUNTING_JOURNAL_ENTRY.REVERSAL_OF_ENTRY_ID.eq(id.value))
                .fetchOne() ?: return null
        return toDomain(record, linesFor(listOf(record.id))[record.id].orEmpty())
    }

    @Transactional
    override fun save(journalEntry: JournalEntry) {
        dsl
            .insertInto(ACCOUNTING_JOURNAL_ENTRY)
            .set(ACCOUNTING_JOURNAL_ENTRY.ID, journalEntry.id.value)
            .set(ACCOUNTING_JOURNAL_ENTRY.ENTRY_DATE, journalEntry.entryDate)
            .set(ACCOUNTING_JOURNAL_ENTRY.DESCRIPTION, journalEntry.description)
            .set(ACCOUNTING_JOURNAL_ENTRY.REFERENCE, journalEntry.reference)
            .set(ACCOUNTING_JOURNAL_ENTRY.CURRENCY_CODE, journalEntry.currencyCode)
            .set(ACCOUNTING_JOURNAL_ENTRY.REVERSAL_OF_ENTRY_ID, journalEntry.reversalOfEntryId?.value)
            .set(ACCOUNTING_JOURNAL_ENTRY.POSTED_BY_USER_ID, journalEntry.postedByUserId)
            .set(ACCOUNTING_JOURNAL_ENTRY.POSTED_AT, toOffset(journalEntry.postedAt))
            .set(ACCOUNTING_JOURNAL_ENTRY.CORRELATION_ID, journalEntry.correlationId)
            .execute()

        journalEntry.lines.forEach { line ->
            dsl
                .insertInto(ACCOUNTING_JOURNAL_LINE)
                .set(ACCOUNTING_JOURNAL_LINE.ID, line.id.value)
                .set(ACCOUNTING_JOURNAL_LINE.JOURNAL_ENTRY_ID, journalEntry.id.value)
                .set(ACCOUNTING_JOURNAL_LINE.ACCOUNT_ID, line.accountId.value)
                .set(ACCOUNTING_JOURNAL_LINE.DIRECTION, line.direction.name)
                .set(ACCOUNTING_JOURNAL_LINE.AMOUNT, line.amount)
                .set(ACCOUNTING_JOURNAL_LINE.LINE_ORDER, line.lineOrder)
                .execute()
        }
    }

    private fun linesFor(entryIds: List<UUID>): Map<UUID, List<JournalLine>> {
        if (entryIds.isEmpty()) return emptyMap()
        return dsl
            .selectFrom(ACCOUNTING_JOURNAL_LINE)
            .where(ACCOUNTING_JOURNAL_LINE.JOURNAL_ENTRY_ID.`in`(entryIds))
            .orderBy(ACCOUNTING_JOURNAL_LINE.LINE_ORDER)
            .fetch()
            .groupBy({ it.journalEntryId }, { toLineDomain(it) })
    }

    private fun toLineDomain(record: AccountingJournalLineRecord): JournalLine =
        JournalLine(
            id = JournalLineId(record.id),
            accountId = AccountId(record.accountId),
            direction = JournalLineDirection.valueOf(record.direction),
            amount = record.amount,
            lineOrder = record.lineOrder,
        )

    private fun toDomain(
        record: AccountingJournalEntryRecord,
        lines: List<JournalLine>,
    ): JournalEntry =
        JournalEntry(
            id = JournalEntryId(record.id),
            entryDate = record.entryDate,
            description = record.description,
            reference = record.reference,
            currencyCode = record.currencyCode,
            lines = lines,
            reversalOfEntryId = record.reversalOfEntryId?.let(::JournalEntryId),
            postedByUserId = record.postedByUserId,
            postedAt = record.postedAt.toInstant(),
            correlationId = record.correlationId,
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
