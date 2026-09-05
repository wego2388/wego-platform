package com.wego.hr.application

import com.wego.hr.domain.EmployeeId
import com.wego.hr.domain.LeaveRequest
import com.wego.hr.domain.LeaveRequestId
import com.wego.hr.domain.LeaveRequestStatus

class LeaveRequestQueryService(
    private val leaveRequestRepository: LeaveRequestRepository,
) {
    fun findById(id: LeaveRequestId): LeaveRequest? = leaveRequestRepository.findById(id)

    fun list(
        employeeId: EmployeeId?,
        status: LeaveRequestStatus?,
        page: Int = 0,
        size: Int = Pagination.DEFAULT_PAGE_SIZE,
    ): List<LeaveRequest> =
        leaveRequestRepository.findAll(
            employeeId,
            status,
            limit = Pagination.boundedSize(size),
            offset = Pagination.offsetFor(page, size),
        )
}
