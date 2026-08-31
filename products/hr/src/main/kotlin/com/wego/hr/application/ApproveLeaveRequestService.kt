package com.wego.hr.application

import com.wego.hr.domain.LeaveRequest
import com.wego.hr.domain.LeaveRequestId
import com.wego.transaction.TransactionRunner
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface ApproveLeaveRequestResult {
    data class Approved(
        val leaveRequest: LeaveRequest,
    ) : ApproveLeaveRequestResult

    data object NotFound : ApproveLeaveRequestResult

    data object NotPending : ApproveLeaveRequestResult

    /** A real conflict-prevention rule, the same shape as WEGO-011 Phase 3's boat-capacity guardrail: this employee already has another APPROVED leave overlapping this date range. */
    data object OverlapsApprovedLeave : ApproveLeaveRequestResult
}

class ApproveLeaveRequestService(
    private val leaveRequestRepository: LeaveRequestRepository,
    private val auditRecorder: LeaveRequestAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun approve(
        id: LeaveRequestId,
        actorUserId: UUID?,
        notes: String?,
        correlationId: UUID?,
    ): ApproveLeaveRequestResult =
        transactionRunner.runInTransaction {
            val existing = leaveRequestRepository.findByIdForUpdate(id) ?: return@runInTransaction ApproveLeaveRequestResult.NotFound
            if (!existing.isPending) return@runInTransaction ApproveLeaveRequestResult.NotPending

            val overlapsAnother =
                leaveRequestRepository
                    .findApprovedByEmployee(existing.employeeId)
                    .any { it.id != existing.id && it.overlaps(existing) }
            if (overlapsAnother) return@runInTransaction ApproveLeaveRequestResult.OverlapsApprovedLeave

            val now = Instant.now(clock)
            val approved = existing.approve(actorUserId, now, notes)
            leaveRequestRepository.save(approved)
            auditRecorder.recordLeaveApproved(approved.id, actorUserId, now, notes, correlationId)
            ApproveLeaveRequestResult.Approved(approved)
        }
}
