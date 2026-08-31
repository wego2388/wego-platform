package com.wego.hr.application

import com.wego.hr.domain.Employee
import com.wego.hr.domain.EmployeeId
import com.wego.transaction.TransactionRunner
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface TerminateEmployeeResult {
    data class Terminated(
        val employee: Employee,
    ) : TerminateEmployeeResult

    data object NotFound : TerminateEmployeeResult

    data object AlreadyTerminated : TerminateEmployeeResult
}

class TerminateEmployeeService(
    private val employeeRepository: EmployeeRepository,
    private val auditRecorder: EmployeeAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun terminate(
        id: EmployeeId,
        actorUserId: UUID?,
        reason: String?,
        correlationId: UUID?,
    ): TerminateEmployeeResult =
        transactionRunner.runInTransaction {
            val existing = employeeRepository.findByIdForUpdate(id) ?: return@runInTransaction TerminateEmployeeResult.NotFound
            if (!existing.isActive) return@runInTransaction TerminateEmployeeResult.AlreadyTerminated

            val now = Instant.now(clock)
            existing.terminate(now)
            employeeRepository.save(existing)
            auditRecorder.recordEmployeeTerminated(existing.id, actorUserId, now, reason, correlationId)
            TerminateEmployeeResult.Terminated(existing)
        }
}
