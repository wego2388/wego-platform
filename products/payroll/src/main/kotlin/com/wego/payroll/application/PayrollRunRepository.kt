package com.wego.payroll.application

import com.wego.payroll.domain.PayrollRun
import com.wego.payroll.domain.PayrollRunId
import com.wego.payroll.domain.PayrollRunStatus
import java.time.LocalDate

interface PayrollRunRepository {
    fun findById(id: PayrollRunId): PayrollRun?

    /** Row-locked read for a read-modify-write cycle — see EmployeeRepository.findByIdForUpdate (products/hr) for the established pattern. */
    fun findByIdForUpdate(id: PayrollRunId): PayrollRun?

    fun findAll(
        status: PayrollRunStatus?,
        limit: Int,
        offset: Int,
    ): List<PayrollRun>

    /** Any run (DRAFT or POSTED) whose pay period overlaps the given range — used to reject creating a second run for a period that's already covered. */
    fun findOverlapping(
        payPeriodStart: LocalDate,
        payPeriodEnd: LocalDate,
    ): List<PayrollRun>

    fun save(payrollRun: PayrollRun)

    /** Only ever called on a DRAFT run — a POSTED run is permanent, matching accounting_journal_entry's own no-delete discipline. */
    fun delete(id: PayrollRunId)
}
