package com.wego.payroll.infrastructure

import com.wego.generated.jooq.tables.PayrollLine.PAYROLL_LINE
import com.wego.generated.jooq.tables.PayrollRun.PAYROLL_RUN
import com.wego.generated.jooq.tables.records.PayrollLineRecord
import com.wego.generated.jooq.tables.records.PayrollRunRecord
import com.wego.payroll.application.PayrollRunRepository
import com.wego.payroll.domain.PayrollLine
import com.wego.payroll.domain.PayrollLineId
import com.wego.payroll.domain.PayrollRun
import com.wego.payroll.domain.PayrollRunId
import com.wego.payroll.domain.PayrollRunStatus
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
class JooqPayrollRunRepository(
    private val dsl: DSLContext,
) : PayrollRunRepository {
    @Transactional(readOnly = true)
    override fun findById(id: PayrollRunId): PayrollRun? {
        val record = dsl.selectFrom(PAYROLL_RUN).where(PAYROLL_RUN.ID.eq(id.value)).fetchOne() ?: return null
        return toDomain(record, linesFor(listOf(id.value))[id.value].orEmpty())
    }

    @Transactional
    override fun findByIdForUpdate(id: PayrollRunId): PayrollRun? {
        val record =
            dsl
                .selectFrom(PAYROLL_RUN)
                .where(PAYROLL_RUN.ID.eq(id.value))
                .forUpdate()
                .fetchOne() ?: return null
        return toDomain(record, linesFor(listOf(id.value))[id.value].orEmpty())
    }

    @Transactional(readOnly = true)
    override fun findAll(
        status: PayrollRunStatus?,
        limit: Int,
        offset: Int,
    ): List<PayrollRun> {
        var condition = DSL.noCondition()
        if (status != null) condition = condition.and(PAYROLL_RUN.STATUS.eq(status.name))
        val runRecords =
            dsl
                .selectFrom(PAYROLL_RUN)
                .where(condition)
                .orderBy(PAYROLL_RUN.PAY_PERIOD_START.desc(), PAYROLL_RUN.ID)
                .limit(limit)
                .offset(offset)
                .fetch()
        val lines = linesFor(runRecords.map { it.id })
        return runRecords.map { toDomain(it, lines[it.id].orEmpty()) }
    }

    @Transactional(readOnly = true)
    override fun findOverlapping(
        payPeriodStart: LocalDate,
        payPeriodEnd: LocalDate,
    ): List<PayrollRun> {
        val runRecords =
            dsl
                .selectFrom(PAYROLL_RUN)
                .where(PAYROLL_RUN.PAY_PERIOD_START.le(payPeriodEnd))
                .and(PAYROLL_RUN.PAY_PERIOD_END.ge(payPeriodStart))
                .fetch()
        val lines = linesFor(runRecords.map { it.id })
        return runRecords.map { toDomain(it, lines[it.id].orEmpty()) }
    }

    @Transactional
    override fun save(payrollRun: PayrollRun) {
        dsl
            .insertInto(PAYROLL_RUN)
            .set(PAYROLL_RUN.ID, payrollRun.id.value)
            .set(PAYROLL_RUN.PAY_PERIOD_START, payrollRun.payPeriodStart)
            .set(PAYROLL_RUN.PAY_PERIOD_END, payrollRun.payPeriodEnd)
            .set(PAYROLL_RUN.CURRENCY_CODE, payrollRun.currencyCode)
            .set(PAYROLL_RUN.STATUS, payrollRun.status.name)
            .set(PAYROLL_RUN.CREATED_BY_USER_ID, payrollRun.createdByUserId)
            .set(PAYROLL_RUN.CREATED_AT, toOffset(payrollRun.createdAt))
            .set(PAYROLL_RUN.POSTED_BY_USER_ID, payrollRun.postedByUserId)
            .set(PAYROLL_RUN.POSTED_AT, payrollRun.postedAt?.let(::toOffset))
            .set(PAYROLL_RUN.JOURNAL_ENTRY_ID, payrollRun.journalEntryId)
            .onConflict(PAYROLL_RUN.ID)
            .doUpdate()
            .set(PAYROLL_RUN.STATUS, payrollRun.status.name)
            .set(PAYROLL_RUN.POSTED_BY_USER_ID, payrollRun.postedByUserId)
            .set(PAYROLL_RUN.POSTED_AT, payrollRun.postedAt?.let(::toOffset))
            .set(PAYROLL_RUN.JOURNAL_ENTRY_ID, payrollRun.journalEntryId)
            .execute()

        // Lines are written once, at creation, and never change afterward
        // (a DRAFT run is discarded wholesale, not edited; a POSTED run is
        // permanent) — no upsert/delete-then-reinsert dance needed here.
        if (dsl.fetchCount(PAYROLL_LINE, PAYROLL_LINE.PAYROLL_RUN_ID.eq(payrollRun.id.value)) == 0) {
            payrollRun.lines.forEach { line ->
                dsl
                    .insertInto(PAYROLL_LINE)
                    .set(PAYROLL_LINE.ID, line.id.value)
                    .set(PAYROLL_LINE.PAYROLL_RUN_ID, payrollRun.id.value)
                    .set(PAYROLL_LINE.EMPLOYEE_ID, line.employeeId)
                    .set(PAYROLL_LINE.AMOUNT, line.amount)
                    .execute()
            }
        }
    }

    @Transactional
    override fun delete(id: PayrollRunId) {
        dsl.deleteFrom(PAYROLL_RUN).where(PAYROLL_RUN.ID.eq(id.value)).execute()
    }

    private fun linesFor(runIds: List<UUID>): Map<UUID, List<PayrollLine>> {
        if (runIds.isEmpty()) return emptyMap()
        return dsl
            .selectFrom(PAYROLL_LINE)
            .where(PAYROLL_LINE.PAYROLL_RUN_ID.`in`(runIds))
            .fetch()
            .groupBy({ it.payrollRunId }, { toLineDomain(it) })
    }

    private fun toLineDomain(record: PayrollLineRecord): PayrollLine =
        PayrollLine(PayrollLineId(record.id), record.employeeId, record.amount)

    private fun toDomain(
        record: PayrollRunRecord,
        lines: List<PayrollLine>,
    ): PayrollRun =
        PayrollRun(
            id = PayrollRunId(record.id),
            payPeriodStart = record.payPeriodStart,
            payPeriodEnd = record.payPeriodEnd,
            lines = lines,
            currencyCode = record.currencyCode,
            status = PayrollRunStatus.valueOf(record.status),
            createdByUserId = record.createdByUserId,
            createdAt = record.createdAt.toInstant(),
            postedByUserId = record.postedByUserId,
            postedAt = record.postedAt?.toInstant(),
            journalEntryId = record.journalEntryId,
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
