package com.wego.hr.application

import com.wego.hr.domain.LeaveRequestId
import java.time.Instant
import java.util.UUID

interface LeaveRequestAuditRecorder {
    fun recordLeaveRequested(
        leaveRequestId: LeaveRequestId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordLeaveApproved(
        leaveRequestId: LeaveRequestId,
        actorUserId: UUID?,
        occurredAt: Instant,
        reason: String?,
        correlationId: UUID?,
    )

    fun recordLeaveRejected(
        leaveRequestId: LeaveRequestId,
        actorUserId: UUID?,
        occurredAt: Instant,
        reason: String?,
        correlationId: UUID?,
    )

    fun recordLeaveCancelled(
        leaveRequestId: LeaveRequestId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    )
}
