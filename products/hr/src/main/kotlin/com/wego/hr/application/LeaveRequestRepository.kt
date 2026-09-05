package com.wego.hr.application

import com.wego.hr.domain.EmployeeId
import com.wego.hr.domain.LeaveRequest
import com.wego.hr.domain.LeaveRequestId
import com.wego.hr.domain.LeaveRequestStatus

interface LeaveRequestRepository {
    fun findById(id: LeaveRequestId): LeaveRequest?

    /** Row-locked read for a read-modify-write cycle — see EmployeeRepository.findByIdForUpdate. */
    fun findByIdForUpdate(id: LeaveRequestId): LeaveRequest?

    fun findAll(
        employeeId: EmployeeId?,
        status: LeaveRequestStatus?,
        limit: Int,
        offset: Int,
    ): List<LeaveRequest>

    /** Every APPROVED request for this employee — small enough per employee to scan in full and check overlap in application code, no dedicated overlap query needed. */
    fun findApprovedByEmployee(employeeId: EmployeeId): List<LeaveRequest>

    fun save(leaveRequest: LeaveRequest)
}
