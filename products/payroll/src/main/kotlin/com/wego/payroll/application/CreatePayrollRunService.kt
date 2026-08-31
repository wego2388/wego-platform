package com.wego.payroll.application

import com.wego.payroll.domain.PayrollLine
import com.wego.payroll.domain.PayrollLineId
import com.wego.payroll.domain.PayrollRun
import com.wego.payroll.domain.PayrollRunId
import com.wego.transaction.TransactionRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

sealed interface CreatePayrollRunResult {
    data class Created(
        val payrollRun: PayrollRun,
    ) : CreatePayrollRunResult

    data object NoEligibleEmployees : CreatePayrollRunResult

    /** Every line in one payroll run must share a currency, the same rule `JournalEntry` (products/accounting) already enforces — a business with mixed-currency salaries runs payroll separately per currency. */
    data class MixedCurrencies(
        val currencyCodes: Set<String>,
    ) : CreatePayrollRunResult

    /** A real conflict-prevention rule: this pay period already overlaps an existing run (DRAFT or POSTED), the same double-payment guard boat-capacity/leave-overlap checks already established elsewhere in this project. */
    data object OverlapsExistingRun : CreatePayrollRunResult
}

class CreatePayrollRunService(
    private val payrollEmployeeLookup: PayrollEmployeeLookup,
    private val payrollRunRepository: PayrollRunRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun create(
        payPeriodStart: LocalDate,
        payPeriodEnd: LocalDate,
        createdByUserId: UUID?,
    ): CreatePayrollRunResult =
        transactionRunner.runInTransaction {
            if (payrollRunRepository.findOverlapping(payPeriodStart, payPeriodEnd).isNotEmpty()) {
                return@runInTransaction CreatePayrollRunResult.OverlapsExistingRun
            }

            val eligible = payrollEmployeeLookup.listActiveEmployeesWithSalary()
            if (eligible.isEmpty()) return@runInTransaction CreatePayrollRunResult.NoEligibleEmployees

            val currencies = eligible.map { it.currencyCode }.toSet()
            if (currencies.size > 1) return@runInTransaction CreatePayrollRunResult.MixedCurrencies(currencies)

            val now = Instant.now(clock)
            val lines = eligible.map { PayrollLine(PayrollLineId.generate(), it.employeeId, it.salaryAmount) }
            val payrollRun =
                PayrollRun.create(
                    id = PayrollRunId.generate(),
                    payPeriodStart = payPeriodStart,
                    payPeriodEnd = payPeriodEnd,
                    lines = lines,
                    currencyCode = currencies.first(),
                    createdByUserId = createdByUserId,
                    now = now,
                )
            payrollRunRepository.save(payrollRun)
            CreatePayrollRunResult.Created(payrollRun)
        }
}
