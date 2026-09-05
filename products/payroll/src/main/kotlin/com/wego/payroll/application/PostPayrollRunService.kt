package com.wego.payroll.application

import com.wego.payroll.domain.PayrollRun
import com.wego.payroll.domain.PayrollRunId
import com.wego.transaction.TransactionRunner
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface PostPayrollRunResult {
    data class Posted(
        val payrollRun: PayrollRun,
    ) : PostPayrollRunResult

    data object NotFound : PostPayrollRunResult

    data object NotDraft : PostPayrollRunResult

    data object SalariesExpenseAccountNotFound : PostPayrollRunResult

    data object SalariesExpenseAccountInactive : PostPayrollRunResult

    data object WagesPayableAccountNotFound : PostPayrollRunResult

    data object WagesPayableAccountInactive : PostPayrollRunResult
}

class PostPayrollRunService(
    private val payrollRunRepository: PayrollRunRepository,
    private val salaryJournalPoster: SalaryJournalPoster,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun post(
        id: PayrollRunId,
        actorUserId: UUID?,
        correlationId: UUID?,
    ): PostPayrollRunResult =
        transactionRunner.runInTransaction {
            val run = payrollRunRepository.findByIdForUpdate(id) ?: return@runInTransaction PostPayrollRunResult.NotFound
            if (!run.isDraft) return@runInTransaction PostPayrollRunResult.NotDraft

            val now = Instant.now(clock)
            val journalResult =
                salaryJournalPoster.post(
                    entryDate = run.payPeriodEnd,
                    description = "Payroll for ${run.payPeriodStart} to ${run.payPeriodEnd}",
                    reference = run.id.value.toString(),
                    currencyCode = run.currencyCode,
                    totalAmount = run.totalAmount,
                    postedByUserId = actorUserId,
                    correlationId = correlationId,
                )
            when (journalResult) {
                is PostSalaryJournalResult.Posted -> {
                    run.post(actorUserId, journalResult.journalEntryId, now)
                    payrollRunRepository.save(run)
                    PostPayrollRunResult.Posted(run)
                }
                PostSalaryJournalResult.SalariesExpenseAccountNotFound -> PostPayrollRunResult.SalariesExpenseAccountNotFound
                PostSalaryJournalResult.SalariesExpenseAccountInactive -> PostPayrollRunResult.SalariesExpenseAccountInactive
                PostSalaryJournalResult.WagesPayableAccountNotFound -> PostPayrollRunResult.WagesPayableAccountNotFound
                PostSalaryJournalResult.WagesPayableAccountInactive -> PostPayrollRunResult.WagesPayableAccountInactive
            }
        }
}
