package com.wego.hr.infrastructure

import com.wego.generated.jooq.tables.HrLeaveRequestAuditEvent.HR_LEAVE_REQUEST_AUDIT_EVENT
import com.wego.hr.application.LeaveRequestAuditRecorder
import com.wego.hr.domain.LeaveRequestId
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Component
class JooqLeaveRequestAuditRecorder(
    private val dsl: DSLContext,
) : LeaveRequestAuditRecorder {
    @Transactional
    override fun recordLeaveRequested(
        leaveRequestId: LeaveRequestId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    ) = insert(leaveRequestId, "LEAVE_REQUESTED", occurredAt, actorUserId, null, correlationId)

    @Transactional
    override fun recordLeaveApproved(
        leaveRequestId: LeaveRequestId,
        actorUserId: UUID?,
        occurredAt: Instant,
        reason: String?,
        correlationId: UUID?,
    ) = insert(leaveRequestId, "LEAVE_APPROVED", occurredAt, actorUserId, reason, correlationId)

    @Transactional
    override fun recordLeaveRejected(
        leaveRequestId: LeaveRequestId,
        actorUserId: UUID?,
        occurredAt: Instant,
        reason: String?,
        correlationId: UUID?,
    ) = insert(leaveRequestId, "LEAVE_REJECTED", occurredAt, actorUserId, reason, correlationId)

    @Transactional
    override fun recordLeaveCancelled(
        leaveRequestId: LeaveRequestId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    ) = insert(leaveRequestId, "LEAVE_CANCELLED", occurredAt, actorUserId, null, correlationId)

    private fun insert(
        leaveRequestId: LeaveRequestId,
        eventType: String,
        occurredAt: Instant,
        actorUserId: UUID?,
        reason: String?,
        correlationId: UUID?,
    ) {
        dsl
            .insertInto(HR_LEAVE_REQUEST_AUDIT_EVENT)
            .set(HR_LEAVE_REQUEST_AUDIT_EVENT.ID, UUID.randomUUID())
            .set(HR_LEAVE_REQUEST_AUDIT_EVENT.LEAVE_REQUEST_ID, leaveRequestId.value)
            .set(HR_LEAVE_REQUEST_AUDIT_EVENT.OCCURRED_AT, OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
            .set(HR_LEAVE_REQUEST_AUDIT_EVENT.EVENT_TYPE, eventType)
            .set(HR_LEAVE_REQUEST_AUDIT_EVENT.ACTOR_USER_ID, actorUserId)
            .set(HR_LEAVE_REQUEST_AUDIT_EVENT.REASON, reason)
            .set(HR_LEAVE_REQUEST_AUDIT_EVENT.CORRELATION_ID, correlationId)
            .execute()
    }
}
