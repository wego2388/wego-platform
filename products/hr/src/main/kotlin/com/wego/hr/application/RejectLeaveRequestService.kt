package com.wego.hr.application

import com.wego.hr.domain.LeaveRequest
import com.wego.hr.domain.LeaveRequestId
import com.wego.transaction.TransactionRunner
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface RejectLeaveRequestResult {
    data class Rejected(
        val leaveRequest: LeaveRequest,
    ) : RejectLeaveRequestResult

    data object NotFound : RejectLeaveRequestResult

    data object NotPending : RejectLeaveRequestResult
}

class RejectLeaveRequestService(
    private val leaveRequestRepository: LeaveRequestRepository,
    private val auditRecorder: LeaveRequestAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun reject(
        id: LeaveRequestId,
        actorUserId: UUID?,
        notes: String?,
        correlationId: UUID?,
    ): RejectLeaveRequestResult =
        transactionRunner.runInTransaction {
            val existing = leaveRequestRepository.findByIdForUpdate(id) ?: return@runInTransaction RejectLeaveRequestResult.NotFound
            if (!existing.isPending) return@runInTransaction RejectLeaveRequestResult.NotPending

            val now = Instant.now(clock)
            val rejected = existing.reject(actorUserId, now, notes)
            leaveRequestRepository.save(rejected)
            auditRecorder.recordLeaveRejected(rejected.id, actorUserId, now, notes, correlationId)
            RejectLeaveRequestResult.Rejected(rejected)
        }
}
