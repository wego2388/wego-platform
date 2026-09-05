package com.wego.hr.application

import com.wego.hr.domain.EmployeeId
import java.time.Instant
import java.util.UUID

interface EmployeeAuditRecorder {
    fun recordEmployeeCreated(
        employeeId: EmployeeId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordEmployeeUpdated(
        employeeId: EmployeeId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordEmployeeTerminated(
        employeeId: EmployeeId,
        actorUserId: UUID?,
        occurredAt: Instant,
        reason: String?,
        correlationId: UUID?,
    )
}
