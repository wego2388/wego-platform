package com.wego.hr.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A real approval workflow, not a plain status field: PENDING moves to
 * either APPROVED/REJECTED (a decision, [decidedByUserId]/[decidedAt]) or
 * CANCELLED (a withdrawal, [cancelledAt]) — the two are never conflated,
 * matching the DB's own `hr_leave_request_lifecycle_fields_match_status`
 * constraint.
 */
class LeaveRequest(
    val id: LeaveRequestId,
    val employeeId: EmployeeId,
    val leaveType: LeaveType,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val reason: String?,
    status: LeaveRequestStatus,
    val requestedByUserId: UUID?,
    val requestedAt: Instant,
    decidedByUserId: UUID?,
    decidedAt: Instant?,
    decisionNotes: String?,
    cancelledAt: Instant?,
) {
    var status: LeaveRequestStatus = status
        private set

    var decidedByUserId: UUID? = decidedByUserId
        private set

    var decidedAt: Instant? = decidedAt
        private set

    var decisionNotes: String? = decisionNotes
        private set

    var cancelledAt: Instant? = cancelledAt
        private set

    init {
        require(!endDate.isBefore(startDate)) { "endDate must not be before startDate" }
        when (status) {
            LeaveRequestStatus.APPROVED, LeaveRequestStatus.REJECTED ->
                require(decidedAt != null && decidedByUserId != null && cancelledAt == null) {
                    "An approved/rejected request must carry a decision and no cancellation"
                }
            LeaveRequestStatus.PENDING ->
                require(decidedAt == null && cancelledAt == null) { "A pending request carries neither a decision nor a cancellation" }
            LeaveRequestStatus.CANCELLED ->
                require(cancelledAt != null && decidedAt == null) { "A cancelled request carries a cancellation, never a decision" }
        }
    }

    val isPending: Boolean get() = status == LeaveRequestStatus.PENDING

    fun approve(
        actorUserId: UUID?,
        now: Instant,
        notes: String?,
    ): LeaveRequest {
        require(isPending) { "Only a pending leave request can be approved" }
        return LeaveRequest(
            id = id,
            employeeId = employeeId,
            leaveType = leaveType,
            startDate = startDate,
            endDate = endDate,
            reason = reason,
            status = LeaveRequestStatus.APPROVED,
            requestedByUserId = requestedByUserId,
            requestedAt = requestedAt,
            decidedByUserId = actorUserId,
            decidedAt = now,
            decisionNotes = notes,
            cancelledAt = null,
        )
    }

    fun reject(
        actorUserId: UUID?,
        now: Instant,
        notes: String?,
    ): LeaveRequest {
        require(isPending) { "Only a pending leave request can be rejected" }
        return LeaveRequest(
            id = id,
            employeeId = employeeId,
            leaveType = leaveType,
            startDate = startDate,
            endDate = endDate,
            reason = reason,
            status = LeaveRequestStatus.REJECTED,
            requestedByUserId = requestedByUserId,
            requestedAt = requestedAt,
            decidedByUserId = actorUserId,
            decidedAt = now,
            decisionNotes = notes,
            cancelledAt = null,
        )
    }

    fun cancel(now: Instant): LeaveRequest {
        require(isPending) { "Only a pending leave request can be cancelled" }
        return LeaveRequest(
            id = id,
            employeeId = employeeId,
            leaveType = leaveType,
            startDate = startDate,
            endDate = endDate,
            reason = reason,
            status = LeaveRequestStatus.CANCELLED,
            requestedByUserId = requestedByUserId,
            requestedAt = requestedAt,
            decidedByUserId = null,
            decidedAt = null,
            decisionNotes = null,
            cancelledAt = now,
        )
    }

    /** Whether this request's date range overlaps another — used to block approving two overlapping APPROVED leaves for the same employee. */
    fun overlaps(other: LeaveRequest): Boolean = !startDate.isAfter(other.endDate) && !other.startDate.isAfter(endDate)

    companion object {
        fun submit(
            id: LeaveRequestId,
            employeeId: EmployeeId,
            leaveType: LeaveType,
            startDate: LocalDate,
            endDate: LocalDate,
            reason: String?,
            requestedByUserId: UUID?,
            now: Instant,
        ): LeaveRequest =
            LeaveRequest(
                id = id,
                employeeId = employeeId,
                leaveType = leaveType,
                startDate = startDate,
                endDate = endDate,
                reason = reason,
                status = LeaveRequestStatus.PENDING,
                requestedByUserId = requestedByUserId,
                requestedAt = now,
                decidedByUserId = null,
                decidedAt = null,
                decisionNotes = null,
                cancelledAt = null,
            )
    }
}
