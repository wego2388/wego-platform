package com.wego.hr

import com.wego.hr.domain.Employee
import com.wego.hr.domain.EmployeeId
import com.wego.hr.domain.EmployeeStatus
import com.wego.hr.domain.Money
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class EmployeeTest {
    private val now = Instant.parse("2026-08-30T00:00:00Z")
    private val hireDate = LocalDate.parse("2026-01-15")

    private fun create(baseSalary: Money? = Money(BigDecimal("15000.00"), "EGP")): Employee =
        Employee.create(
            id = EmployeeId.generate(),
            fullName = "Ada Lovelace",
            position = "Dive Instructor",
            department = "Operations",
            hireDate = hireDate,
            email = "ada@example.com",
            phone = "+201066461010",
            baseSalary = baseSalary,
            linkedUserId = null,
            createdByUserId = null,
            now = now,
        )

    @Test
    fun `rejects a blank full name`() {
        assertThatIllegalArgumentException().isThrownBy {
            Employee.create(
                id = EmployeeId.generate(),
                fullName = "   ",
                position = "Dive Instructor",
                department = null,
                hireDate = hireDate,
                email = null,
                phone = null,
                baseSalary = null,
                linkedUserId = null,
                createdByUserId = null,
                now = now,
            )
        }
    }

    @Test
    fun `rejects a blank position`() {
        assertThatIllegalArgumentException().isThrownBy {
            Employee.create(
                id = EmployeeId.generate(),
                fullName = "Ada Lovelace",
                position = "   ",
                department = null,
                hireDate = hireDate,
                email = null,
                phone = null,
                baseSalary = null,
                linkedUserId = null,
                createdByUserId = null,
                now = now,
            )
        }
    }

    @Test
    fun `starts active with no termination timestamp`() {
        val employee = create()
        assertThat(employee.status).isEqualTo(EmployeeStatus.ACTIVE)
        assertThat(employee.isActive).isTrue()
        assertThat(employee.terminatedAt).isNull()
    }

    @Test
    fun `accepts no base salary at all`() {
        val employee = create(baseSalary = null)
        assertThat(employee.baseSalary).isNull()
    }

    @Test
    fun `terminating sets status and timestamp together`() {
        val employee = create()
        val terminatedAt = Instant.parse("2026-09-01T00:00:00Z")

        employee.terminate(terminatedAt)

        assertThat(employee.status).isEqualTo(EmployeeStatus.TERMINATED)
        assertThat(employee.isActive).isFalse()
        assertThat(employee.terminatedAt).isEqualTo(terminatedAt)
    }

    @Test
    fun `an already-terminated employee cannot be terminated again`() {
        val employee = create()
        employee.terminate(Instant.parse("2026-09-01T00:00:00Z"))

        assertThatIllegalArgumentException().isThrownBy { employee.terminate(Instant.parse("2026-09-02T00:00:00Z")) }
    }

    @Test
    fun `terminating does not redact salary or contact details, unlike a diver's archive`() {
        val employee = create()
        employee.terminate(Instant.parse("2026-09-01T00:00:00Z"))

        assertThat(employee.baseSalary).isEqualTo(Money(BigDecimal("15000.00"), "EGP"))
        assertThat(employee.email).isEqualTo("ada@example.com")
        assertThat(employee.phone).isEqualTo("+201066461010")
    }

    @Test
    fun `updating details preserves identity, status, and creation metadata`() {
        val employee = create()
        val updated =
            employee.withUpdatedDetails(
                fullName = "Ada K. Lovelace",
                position = "Senior Dive Instructor",
                department = "Operations",
                email = "ada.k@example.com",
                phone = "+201066461011",
                baseSalary = Money(BigDecimal("18000.00"), "EGP"),
                linkedUserId = UUID.randomUUID(),
            )

        assertThat(updated.id).isEqualTo(employee.id)
        assertThat(updated.status).isEqualTo(employee.status)
        assertThat(updated.createdAt).isEqualTo(employee.createdAt)
        assertThat(updated.fullName).isEqualTo("Ada K. Lovelace")
        assertThat(updated.position).isEqualTo("Senior Dive Instructor")
        assertThat(updated.baseSalary).isEqualTo(Money(BigDecimal("18000.00"), "EGP"))
    }
}
