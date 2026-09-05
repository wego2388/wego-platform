package com.wego.payroll.infrastructure

import com.wego.generated.jooq.tables.HrEmployee.HR_EMPLOYEE
import com.wego.payroll.application.PayrollEmployeeLookup
import com.wego.payroll.application.PayrollEmployeeSnapshot
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class JooqPayrollEmployeeLookup(
    private val dsl: DSLContext,
) : PayrollEmployeeLookup {
    @Transactional(readOnly = true)
    override fun listActiveEmployeesWithSalary(): List<PayrollEmployeeSnapshot> =
        dsl
            .select(HR_EMPLOYEE.ID, HR_EMPLOYEE.FULL_NAME, HR_EMPLOYEE.BASE_SALARY_AMOUNT, HR_EMPLOYEE.BASE_SALARY_CURRENCY_CODE)
            .from(HR_EMPLOYEE)
            .where(HR_EMPLOYEE.STATUS.eq("ACTIVE"))
            .and(HR_EMPLOYEE.BASE_SALARY_AMOUNT.isNotNull)
            .orderBy(HR_EMPLOYEE.FULL_NAME)
            .fetch {
                PayrollEmployeeSnapshot(
                    employeeId = it.value1(),
                    fullName = it.value2(),
                    salaryAmount = it.value3(),
                    // base_salary_currency_code is non-null whenever
                    // base_salary_amount is — hr_employee_base_salary_pair
                    // guarantees both-or-neither at the DB level.
                    currencyCode = it.value4()!!,
                )
            }
}
