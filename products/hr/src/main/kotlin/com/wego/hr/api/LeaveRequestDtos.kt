package com.wego.hr.api

import com.wego.hr.domain.LeaveRequest
import com.wego.hr.domain.LeaveRequestStatus
import com.wego.hr.domain.LeaveType
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val MAX_REASON_LENGTH = 1000
private const val MAX_NOTES_LENGTH = 1000

data class SubmitLeaveRequestRequest(
    @field:NotNull
    val employeeId: UUID,
    @field:NotNull
    val leaveType: LeaveType,
    @field:NotNull
    val startDate: LocalDate,
    @field:NotNull
    val endDate: LocalDate,
    @field:Size(max = MAX_REASON_LENGTH)
    val reason: String?,
)

data class LeaveDecisionRequest(
    @field:Size(max = MAX_NOTES_LENGTH)
    val notes: String?,
)

data class LeaveRequestResponse(
    val id: UUID,
    val employeeId: UUID,
    val leaveType: LeaveType,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val reason: String?,
    val status: LeaveRequestStatus,
    val requestedByUserId: UUID?,
    val requestedAt: Instant,
    val decidedByUserId: UUID?,
    val decidedAt: Instant?,
    val decisionNotes: String?,
    val cancelledAt: Instant?,
)

fun LeaveRequest.toResponse(): LeaveRequestResponse =
    LeaveRequestResponse(
        id = id.value,
        employeeId = employeeId.value,
        leaveType = leaveType,
        startDate = startDate,
        endDate = endDate,
        reason = reason,
        status = status,
        requestedByUserId = requestedByUserId,
        requestedAt = requestedAt,
        decidedByUserId = decidedByUserId,
        decidedAt = decidedAt,
        decisionNotes = decisionNotes,
        cancelledAt = cancelledAt,
    )

data class LeaveRequestErrorResponse(
    val error: String,
)
