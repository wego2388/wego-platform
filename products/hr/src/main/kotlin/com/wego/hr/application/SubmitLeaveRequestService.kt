package com.wego.hr.application

import com.wego.hr.domain.EmployeeId
import com.wego.hr.domain.LeaveRequest
import com.wego.hr.domain.LeaveRequestId
import com.wego.hr.domain.LeaveType
import com.wego.transaction.TransactionRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

sealed interface SubmitLeaveRequestResult {
    data class Submitted(
        val leaveRequest: LeaveRequest,
    ) : SubmitLeaveRequestResult

    data object EmployeeNotFound : SubmitLeaveRequestResult

    /** A terminated employee cannot have a new leave request opened on their behalf. */
    data object EmployeeNotActive : SubmitLeaveRequestResult
}

class SubmitLeaveRequestService(
    private val employeeRepository: EmployeeRepository,
    private val leaveRequestRepository: LeaveRequestRepository,
    private val auditRecorder: LeaveRequestAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun submit(
        employeeId: EmployeeId,
        leaveType: LeaveType,
        startDate: LocalDate,
        endDate: LocalDate,
        reason: String?,
        requestedByUserId: UUID?,
        correlationId: UUID?,
    ): SubmitLeaveRequestResult =
        transactionRunner.runInTransaction {
            val employee = employeeRepository.findById(employeeId) ?: return@runInTransaction SubmitLeaveRequestResult.EmployeeNotFound
            if (!employee.isActive) return@runInTransaction SubmitLeaveRequestResult.EmployeeNotActive

            val now = Instant.now(clock)
            val leaveRequest =
                LeaveRequest.submit(
                    id = LeaveRequestId.generate(),
                    employeeId = employeeId,
                    leaveType = leaveType,
                    startDate = startDate,
                    endDate = endDate,
                    reason = reason,
                    requestedByUserId = requestedByUserId,
                    now = now,
                )
            leaveRequestRepository.save(leaveRequest)
            auditRecorder.recordLeaveRequested(leaveRequest.id, requestedByUserId, now, correlationId)
            SubmitLeaveRequestResult.Submitted(leaveRequest)
        }
}
