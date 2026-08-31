package com.wego.hr.application

import com.wego.hr.domain.Employee
import com.wego.hr.domain.EmployeeId
import com.wego.hr.domain.EmployeeStatus

class EmployeeQueryService(
    private val employeeRepository: EmployeeRepository,
) {
    fun findById(id: EmployeeId): Employee? = employeeRepository.findById(id)

    fun list(
        status: EmployeeStatus?,
        search: String?,
        page: Int = 0,
        size: Int = Pagination.DEFAULT_PAGE_SIZE,
    ): List<Employee> =
        employeeRepository.findAll(
            status,
            search,
            limit = Pagination.boundedSize(size),
            offset = Pagination.offsetFor(page, size),
        )

    fun countActive(): Int = employeeRepository.countByStatus(EmployeeStatus.ACTIVE)
}
