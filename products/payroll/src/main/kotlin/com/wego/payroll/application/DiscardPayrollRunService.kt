package com.wego.payroll.application

import com.wego.payroll.domain.PayrollRunId
import com.wego.transaction.TransactionRunner

sealed interface DiscardPayrollRunResult {
    data object Discarded : DiscardPayrollRunResult

    data object NotFound : DiscardPayrollRunResult

    data object NotDraft : DiscardPayrollRunResult
}

class DiscardPayrollRunService(
    private val payrollRunRepository: PayrollRunRepository,
    private val transactionRunner: TransactionRunner,
) {
    fun discard(id: PayrollRunId): DiscardPayrollRunResult =
        transactionRunner.runInTransaction {
            val run = payrollRunRepository.findByIdForUpdate(id) ?: return@runInTransaction DiscardPayrollRunResult.NotFound
            if (!run.isDraft) return@runInTransaction DiscardPayrollRunResult.NotDraft

            payrollRunRepository.delete(id)
            DiscardPayrollRunResult.Discarded
        }
}
