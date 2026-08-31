package com.wego.payroll.domain

import java.math.BigDecimal
import java.util.UUID

/**
 * `employeeId` is a raw [UUID], not `com.wego.hr.domain.EmployeeId` —
 * Spring Modulith's own boundary verification rejects a product module
 * depending on another product module's domain/application types (proven
 * empirically before writing this module; see the WEGO-012 Phase 6 board
 * entry), the same reasoning `com.wego.hr.application.StaffUserLookup`
 * already established for reading `com.wego.identity`.
 *
 * `amount` is a snapshot of the employee's base salary at the moment this
 * run was created — never a live reference. A later salary change must
 * never rewrite payroll history.
 */
data class PayrollLine(
    val id: PayrollLineId,
    val employeeId: UUID,
    val amount: BigDecimal,
) {
    init {
        require(amount > BigDecimal.ZERO) { "Payroll line amount must be positive" }
    }
}
