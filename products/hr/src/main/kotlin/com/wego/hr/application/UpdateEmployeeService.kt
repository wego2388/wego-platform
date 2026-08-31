package com.wego.hr.application

import com.wego.hr.domain.Employee
import com.wego.hr.domain.EmployeeId
import com.wego.hr.domain.Money
import com.wego.transaction.TransactionRunner
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface UpdateEmployeeResult {
    data class Updated(
        val employee: Employee,
    ) : UpdateEmployeeResult

    data object NotFound : UpdateEmployeeResult

    data object LinkedUserNotActiveStaff : UpdateEmployeeResult
}

class UpdateEmployeeService(
    private val employeeRepository: EmployeeRepository,
    private val staffUserLookup: StaffUserLookup,
    private val auditRecorder: EmployeeAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun update(
        id: EmployeeId,
        fullName: String,
        position: String,
        department: String?,
        email: String?,
        phone: String?,
        baseSalary: Money?,
        linkedUserId: UUID?,
        actorUserId: UUID?,
        correlationId: UUID?,
    ): UpdateEmployeeResult =
        transactionRunner.runInTransaction {
            // Row-locked: withUpdatedDetails carries every other field (status, terminatedAt) unchanged
            // from whatever was read — an unlocked read here could race TerminateEmployeeService the
            // same way UpdateEquipmentService once did (see WEGO-012's own board history), silently
            // reviving a terminated record's status via a plain detail edit.
            val existing = employeeRepository.findByIdForUpdate(id) ?: return@runInTransaction UpdateEmployeeResult.NotFound
            if (linkedUserId != null && !staffUserLookup.isActiveStaffUser(linkedUserId)) {
                return@runInTransaction UpdateEmployeeResult.LinkedUserNotActiveStaff
            }

            val updated = existing.withUpdatedDetails(fullName, position, department, email, phone, baseSalary, linkedUserId)
            employeeRepository.save(updated)
            auditRecorder.recordEmployeeUpdated(updated.id, actorUserId, Instant.now(clock), correlationId)
            UpdateEmployeeResult.Updated(updated)
        }
}
