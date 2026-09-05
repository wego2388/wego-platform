package com.wego.payroll.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A DRAFT run has no external consequence — nothing else references it,
 * so it can be freely discarded. Posting is terminal: it creates one real
 * journal entry (see [journalEntryId]) and the run itself is never edited
 * again, matching `com.wego.accounting.domain.JournalEntry`'s own
 * permanent-once-posted discipline.
 */
class PayrollRun(
    val id: PayrollRunId,
    val payPeriodStart: LocalDate,
    val payPeriodEnd: LocalDate,
    val lines: List<PayrollLine>,
    val currencyCode: String,
    status: PayrollRunStatus,
    val createdByUserId: UUID?,
    val createdAt: Instant,
    postedByUserId: UUID?,
    postedAt: Instant?,
    journalEntryId: UUID?,
) {
    var status: PayrollRunStatus = status
        private set

    var postedByUserId: UUID? = postedByUserId
        private set

    var postedAt: Instant? = postedAt
        private set

    var journalEntryId: UUID? = journalEntryId
        private set

    init {
        require(!payPeriodEnd.isBefore(payPeriodStart)) { "payPeriodEnd must not be before payPeriodStart" }
        require(lines.isNotEmpty()) { "A payroll run needs at least one employee" }
        require(currencyCode.matches(Regex("^[A-Z]{3}$"))) { "currencyCode must be a 3-letter uppercase ISO 4217 code" }
        require((status == PayrollRunStatus.POSTED) == (postedAt != null && journalEntryId != null)) {
            "postedAt and journalEntryId must be set if and only if the run is posted"
        }
    }

    val totalAmount: BigDecimal get() = lines.sumOf { it.amount }
    val isDraft: Boolean get() = status == PayrollRunStatus.DRAFT

    fun post(
        actorUserId: UUID?,
        journalEntryId: UUID,
        now: Instant,
    ) {
        require(isDraft) { "Only a draft payroll run can be posted" }
        status = PayrollRunStatus.POSTED
        postedByUserId = actorUserId
        this.postedAt = now
        this.journalEntryId = journalEntryId
    }

    companion object {
        fun create(
            id: PayrollRunId,
            payPeriodStart: LocalDate,
            payPeriodEnd: LocalDate,
            lines: List<PayrollLine>,
            currencyCode: String,
            createdByUserId: UUID?,
            now: Instant,
        ): PayrollRun =
            PayrollRun(
                id = id,
                payPeriodStart = payPeriodStart,
                payPeriodEnd = payPeriodEnd,
                lines = lines,
                currencyCode = currencyCode,
                status = PayrollRunStatus.DRAFT,
                createdByUserId = createdByUserId,
                createdAt = now,
                postedByUserId = null,
                postedAt = null,
                journalEntryId = null,
            )
    }
}
