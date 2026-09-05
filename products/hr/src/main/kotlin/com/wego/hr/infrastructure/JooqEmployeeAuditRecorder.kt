package com.wego.hr.infrastructure

import com.wego.generated.jooq.tables.HrEmployeeAuditEvent.HR_EMPLOYEE_AUDIT_EVENT
import com.wego.hr.application.EmployeeAuditRecorder
import com.wego.hr.domain.EmployeeId
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Component
class JooqEmployeeAuditRecorder(
    private val dsl: DSLContext,
) : EmployeeAuditRecorder {
    @Transactional
    override fun recordEmployeeCreated(
        employeeId: EmployeeId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    ) = insert(employeeId, "EMPLOYEE_CREATED", occurredAt, actorUserId, null, correlationId)

    @Transactional
    override fun recordEmployeeUpdated(
        employeeId: EmployeeId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    ) = insert(employeeId, "EMPLOYEE_UPDATED", occurredAt, actorUserId, null, correlationId)

    @Transactional
    override fun recordEmployeeTerminated(
        employeeId: EmployeeId,
        actorUserId: UUID?,
        occurredAt: Instant,
        reason: String?,
        correlationId: UUID?,
    ) = insert(employeeId, "EMPLOYEE_TERMINATED", occurredAt, actorUserId, reason, correlationId)

    private fun insert(
        employeeId: EmployeeId,
        eventType: String,
        occurredAt: Instant,
        actorUserId: UUID?,
        reason: String?,
        correlationId: UUID?,
    ) {
        dsl
            .insertInto(HR_EMPLOYEE_AUDIT_EVENT)
            .set(HR_EMPLOYEE_AUDIT_EVENT.ID, UUID.randomUUID())
            .set(HR_EMPLOYEE_AUDIT_EVENT.EMPLOYEE_ID, employeeId.value)
            .set(HR_EMPLOYEE_AUDIT_EVENT.OCCURRED_AT, OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
            .set(HR_EMPLOYEE_AUDIT_EVENT.EVENT_TYPE, eventType)
            .set(HR_EMPLOYEE_AUDIT_EVENT.ACTOR_USER_ID, actorUserId)
            .set(HR_EMPLOYEE_AUDIT_EVENT.REASON, reason)
            .set(HR_EMPLOYEE_AUDIT_EVENT.CORRELATION_ID, correlationId)
            .execute()
    }
}
