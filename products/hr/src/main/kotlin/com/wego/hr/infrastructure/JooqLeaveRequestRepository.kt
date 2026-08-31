package com.wego.hr.infrastructure

import com.wego.generated.jooq.tables.HrLeaveRequest.HR_LEAVE_REQUEST
import com.wego.generated.jooq.tables.records.HrLeaveRequestRecord
import com.wego.hr.application.LeaveRequestRepository
import com.wego.hr.domain.EmployeeId
import com.wego.hr.domain.LeaveRequest
import com.wego.hr.domain.LeaveRequestId
import com.wego.hr.domain.LeaveRequestStatus
import com.wego.hr.domain.LeaveType
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqLeaveRequestRepository(
    private val dsl: DSLContext,
) : LeaveRequestRepository {
    @Transactional(readOnly = true)
    override fun findById(id: LeaveRequestId): LeaveRequest? {
        val record = dsl.selectFrom(HR_LEAVE_REQUEST).where(HR_LEAVE_REQUEST.ID.eq(id.value)).fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional
    override fun findByIdForUpdate(id: LeaveRequestId): LeaveRequest? {
        val record =
            dsl
                .selectFrom(HR_LEAVE_REQUEST)
                .where(HR_LEAVE_REQUEST.ID.eq(id.value))
                .forUpdate()
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional(readOnly = true)
    override fun findAll(
        employeeId: EmployeeId?,
        status: LeaveRequestStatus?,
        limit: Int,
        offset: Int,
    ): List<LeaveRequest> {
        var condition = DSL.noCondition()
        if (employeeId != null) condition = condition.and(HR_LEAVE_REQUEST.EMPLOYEE_ID.eq(employeeId.value))
        if (status != null) condition = condition.and(HR_LEAVE_REQUEST.STATUS.eq(status.name))
        return dsl
            .selectFrom(HR_LEAVE_REQUEST)
            .where(condition)
            .orderBy(HR_LEAVE_REQUEST.REQUESTED_AT.desc(), HR_LEAVE_REQUEST.ID)
            .limit(limit)
            .offset(offset)
            .fetch()
            .map(::toDomain)
    }

    @Transactional(readOnly = true)
    override fun findApprovedByEmployee(employeeId: EmployeeId): List<LeaveRequest> =
        dsl
            .selectFrom(HR_LEAVE_REQUEST)
            .where(HR_LEAVE_REQUEST.EMPLOYEE_ID.eq(employeeId.value))
            .and(HR_LEAVE_REQUEST.STATUS.eq(LeaveRequestStatus.APPROVED.name))
            .fetch()
            .map(::toDomain)

    @Transactional
    override fun save(leaveRequest: LeaveRequest) {
        dsl
            .insertInto(HR_LEAVE_REQUEST)
            .set(HR_LEAVE_REQUEST.ID, leaveRequest.id.value)
            .set(HR_LEAVE_REQUEST.EMPLOYEE_ID, leaveRequest.employeeId.value)
            .set(HR_LEAVE_REQUEST.LEAVE_TYPE, leaveRequest.leaveType.name)
            .set(HR_LEAVE_REQUEST.START_DATE, leaveRequest.startDate)
            .set(HR_LEAVE_REQUEST.END_DATE, leaveRequest.endDate)
            .set(HR_LEAVE_REQUEST.REASON, leaveRequest.reason)
            .set(HR_LEAVE_REQUEST.STATUS, leaveRequest.status.name)
            .set(HR_LEAVE_REQUEST.REQUESTED_BY_USER_ID, leaveRequest.requestedByUserId)
            .set(HR_LEAVE_REQUEST.REQUESTED_AT, toOffset(leaveRequest.requestedAt))
            .set(HR_LEAVE_REQUEST.DECIDED_BY_USER_ID, leaveRequest.decidedByUserId)
            .set(HR_LEAVE_REQUEST.DECIDED_AT, leaveRequest.decidedAt?.let(::toOffset))
            .set(HR_LEAVE_REQUEST.DECISION_NOTES, leaveRequest.decisionNotes)
            .set(HR_LEAVE_REQUEST.CANCELLED_AT, leaveRequest.cancelledAt?.let(::toOffset))
            .onConflict(HR_LEAVE_REQUEST.ID)
            .doUpdate()
            .set(HR_LEAVE_REQUEST.STATUS, leaveRequest.status.name)
            .set(HR_LEAVE_REQUEST.DECIDED_BY_USER_ID, leaveRequest.decidedByUserId)
            .set(HR_LEAVE_REQUEST.DECIDED_AT, leaveRequest.decidedAt?.let(::toOffset))
            .set(HR_LEAVE_REQUEST.DECISION_NOTES, leaveRequest.decisionNotes)
            .set(HR_LEAVE_REQUEST.CANCELLED_AT, leaveRequest.cancelledAt?.let(::toOffset))
            .execute()
    }

    private fun toDomain(record: HrLeaveRequestRecord): LeaveRequest =
        LeaveRequest(
            id = LeaveRequestId(record.id),
            employeeId = EmployeeId(record.employeeId),
            leaveType = LeaveType.valueOf(record.leaveType),
            startDate = record.startDate,
            endDate = record.endDate,
            reason = record.reason,
            status = LeaveRequestStatus.valueOf(record.status),
            requestedByUserId = record.requestedByUserId,
            requestedAt = record.requestedAt.toInstant(),
            decidedByUserId = record.decidedByUserId,
            decidedAt = record.decidedAt?.toInstant(),
            decisionNotes = record.decisionNotes,
            cancelledAt = record.cancelledAt?.toInstant(),
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
