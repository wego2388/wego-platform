package com.wego.hr.application

import com.wego.hr.domain.Employee
import com.wego.hr.domain.EmployeeId
import com.wego.hr.domain.EmployeeStatus

interface EmployeeRepository {
    fun findById(id: EmployeeId): Employee?

    /** Row-locked read for a read-modify-write cycle — see JooqOfferingRepository.findByIdForUpdate (products/divers) for the established pattern. */
    fun findByIdForUpdate(id: EmployeeId): Employee?

    fun findAll(
        status: EmployeeStatus?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<Employee>

    fun countByStatus(status: EmployeeStatus): Int

    fun save(employee: Employee)
}
