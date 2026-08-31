package com.wego.payroll.application

import java.math.BigDecimal
import java.util.UUID

/** A minimal snapshot of one active, salaried employee — enough to build a payroll line, nothing more. */
data class PayrollEmployeeSnapshot(
    val employeeId: UUID,
    val fullName: String,
    val salaryAmount: BigDecimal,
    val currencyCode: String,
)

/**
 * The minimal cross-module read this module needs against `com.wego.hr`:
 * every currently-active employee who has a base salary set. A
 * module-local port, not a direct import of `com.wego.hr.application` —
 * Spring Modulith's own boundary verification rejects that (proven
 * empirically; see the WEGO-012 Phase 6 board entry), the same reasoning
 * `com.wego.hr.application.StaffUserLookup` already established for
 * reading `com.wego.identity`.
 */
interface PayrollEmployeeLookup {
    fun listActiveEmployeesWithSalary(): List<PayrollEmployeeSnapshot>
}
