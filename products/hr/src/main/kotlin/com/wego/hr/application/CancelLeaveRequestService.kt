package com.wego.hr.application

import com.wego.hr.domain.LeaveRequest
import com.wego.hr.domain.LeaveRequestId
import com.wego.transaction.TransactionRunner
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface CancelLeaveRequestResult {
    data class Cancelled(
        val leaveRequest: LeaveRequest,
    ) : CancelLeaveRequestResult

    data object NotFound : CancelLeaveRequestResult

    data object NotPending : CancelLeaveRequestResult
}

class CancelLeaveRequestService(
    private val leaveRequestRepository: LeaveRequestRepository,
    private val auditRecorder: LeaveRequestAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun cancel(
        id: LeaveRequestId,
        actorUserId: UUID?,
        correlationId: UUID?,
    ): CancelLeaveRequestResult =
        transactionRunner.runInTransaction {
            val existing = leaveRequestRepository.findByIdForUpdate(id) ?: return@runInTransaction CancelLeaveRequestResult.NotFound
            if (!existing.isPending) return@runInTransaction CancelLeaveRequestResult.NotPending

            val now = Instant.now(clock)
            val cancelled = existing.cancel(now)
            leaveRequestRepository.save(cancelled)
            auditRecorder.recordLeaveCancelled(cancelled.id, actorUserId, now, correlationId)
            CancelLeaveRequestResult.Cancelled(cancelled)
        }
}
