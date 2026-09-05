package com.wego.hr.application

import com.wego.hr.domain.Employee
import com.wego.hr.domain.EmployeeId
import com.wego.hr.domain.Money
import com.wego.transaction.TransactionRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

sealed interface CreateEmployeeResult {
    data class Created(
        val employee: Employee,
    ) : CreateEmployeeResult

    /** The given `linkedUserId` does not resolve to a real, active staff account. */
    data object LinkedUserNotActiveStaff : CreateEmployeeResult
}

class CreateEmployeeService(
    private val employeeRepository: EmployeeRepository,
    private val staffUserLookup: StaffUserLookup,
    private val auditRecorder: EmployeeAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun create(
        fullName: String,
        position: String,
        department: String?,
        hireDate: LocalDate,
        email: String?,
        phone: String?,
        baseSalary: Money?,
        linkedUserId: UUID?,
        createdByUserId: UUID?,
        correlationId: UUID?,
    ): CreateEmployeeResult =
        transactionRunner.runInTransaction {
            if (linkedUserId != null && !staffUserLookup.isActiveStaffUser(linkedUserId)) {
                return@runInTransaction CreateEmployeeResult.LinkedUserNotActiveStaff
            }
            val now = Instant.now(clock)
            val employee =
                Employee.create(
                    id = EmployeeId.generate(),
                    fullName = fullName,
                    position = position,
                    department = department,
                    hireDate = hireDate,
                    email = email,
                    phone = phone,
                    baseSalary = baseSalary,
                    linkedUserId = linkedUserId,
                    createdByUserId = createdByUserId,
                    now = now,
                )
            employeeRepository.save(employee)
            auditRecorder.recordEmployeeCreated(employee.id, createdByUserId, now, correlationId)
            CreateEmployeeResult.Created(employee)
        }
}
