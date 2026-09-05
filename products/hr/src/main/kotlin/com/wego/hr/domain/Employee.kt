package com.wego.hr.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class Employee(
    val id: EmployeeId,
    val fullName: String,
    val position: String,
    val department: String?,
    val hireDate: LocalDate,
    val email: String?,
    val phone: String?,
    val baseSalary: Money?,
    /**
     * Optional: not every employee needs a platform login (e.g. a
     * front-of-house instructor who only ever touches equipment/course
     * screens through a colleague). When present, this is a real
     * `identity_user` account, validated to exist and be active at write
     * time — see [com.wego.hr.application.StaffUserLookup] — the same
     * cross-module read pattern `com.wego.divers`'s course-instructor
     * assignment already established.
     */
    val linkedUserId: UUID?,
    status: EmployeeStatus,
    val createdByUserId: UUID?,
    val createdAt: Instant,
    terminatedAt: Instant?,
) {
    var status: EmployeeStatus = status
        private set

    var terminatedAt: Instant? = terminatedAt
        private set

    init {
        require(fullName.isNotBlank()) { "Employee full name must not be blank" }
        require(position.isNotBlank()) { "Employee position must not be blank" }
        require((status == EmployeeStatus.TERMINATED) == (terminatedAt != null)) {
            "terminatedAt must be set if and only if the employee is terminated"
        }
    }

    val isActive: Boolean get() = status == EmployeeStatus.ACTIVE

    /**
     * Terminal: an already-terminated record cannot be terminated again,
     * and this platform has no rehire/reinstate path — a real rehire gets
     * a new employee record, a deliberate simplification that avoids a
     * whole back-pay/rehire-date state machine this scope does not need.
     *
     * Unlike [com.wego.divers.domain.Diver.archive], nothing here is
     * redacted: salary and contact history remain a real, ongoing
     * accounting/audit need after the employment relationship ends (see
     * Phase 5/6's payroll and ledger), unlike a diver's medical notes,
     * which have no legitimate use once the relationship is over.
     */
    fun terminate(now: Instant) {
        require(status == EmployeeStatus.ACTIVE) { "Only an active employee can be terminated" }
        status = EmployeeStatus.TERMINATED
        terminatedAt = now
    }

    /** Every field but identity/status/creation metadata is otherwise immutable — an edit is a new value carrying those forward. */
    fun withUpdatedDetails(
        fullName: String,
        position: String,
        department: String?,
        email: String?,
        phone: String?,
        baseSalary: Money?,
        linkedUserId: UUID?,
    ): Employee =
        Employee(
            id = id,
            fullName = fullName,
            position = position,
            department = department,
            hireDate = hireDate,
            email = email,
            phone = phone,
            baseSalary = baseSalary,
            linkedUserId = linkedUserId,
            status = status,
            createdByUserId = createdByUserId,
            createdAt = createdAt,
            terminatedAt = terminatedAt,
        )

    companion object {
        fun create(
            id: EmployeeId,
            fullName: String,
            position: String,
            department: String?,
            hireDate: LocalDate,
            email: String?,
            phone: String?,
            baseSalary: Money?,
            linkedUserId: UUID?,
            createdByUserId: UUID?,
            now: Instant,
        ): Employee =
            Employee(
                id = id,
                fullName = fullName,
                position = position,
                department = department,
                hireDate = hireDate,
                email = email,
                phone = phone,
                baseSalary = baseSalary,
                linkedUserId = linkedUserId,
                status = EmployeeStatus.ACTIVE,
                createdByUserId = createdByUserId,
                createdAt = now,
                terminatedAt = null,
            )
    }
}
