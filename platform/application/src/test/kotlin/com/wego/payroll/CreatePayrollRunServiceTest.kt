package com.wego.payroll

import com.wego.payroll.application.CreatePayrollRunResult
import com.wego.payroll.application.CreatePayrollRunService
import com.wego.payroll.application.PayrollEmployeeLookup
import com.wego.payroll.application.PayrollEmployeeSnapshot
import com.wego.payroll.application.PayrollRunRepository
import com.wego.payroll.domain.PayrollRun
import com.wego.payroll.domain.PayrollRunId
import com.wego.payroll.domain.PayrollRunStatus
import com.wego.transaction.TransactionRunner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

/**
 * Fast, isolated tests for the two negative-result branches that are
 * awkward to reach reliably over real HTTP in `PayrollHttpTest` — that
 * suite shares one Postgres/employee pool across test methods with no
 * guaranteed execution order, but these branches each need a very
 * specific, exclusive employee-pool shape (empty, or deliberately mixed
 * currencies) to trigger. In-memory fakes, matching this codebase's
 * established `DiversTestFakes.kt`/`IdentityTestFakes.kt` pattern.
 */
class CreatePayrollRunServiceTest {
    private val fixedClock = Clock.fixed(Instant.parse("2026-08-31T00:00:00Z"), ZoneOffset.UTC)
    private val passthroughTransactionRunner =
        object : TransactionRunner {
            override fun <T> runInTransaction(block: () -> T): T = block()
        }

    private class FakePayrollRunRepository : PayrollRunRepository {
        val saved = mutableListOf<PayrollRun>()

        override fun findById(id: PayrollRunId): PayrollRun? = saved.find { it.id == id }

        override fun findByIdForUpdate(id: PayrollRunId): PayrollRun? = findById(id)

        override fun findAll(
            status: PayrollRunStatus?,
            limit: Int,
            offset: Int,
        ): List<PayrollRun> = saved.filter { status == null || it.status == status }

        override fun findOverlapping(
            payPeriodStart: LocalDate,
            payPeriodEnd: LocalDate,
        ): List<PayrollRun> = saved.filter { !it.payPeriodStart.isAfter(payPeriodEnd) && !payPeriodStart.isAfter(it.payPeriodEnd) }

        override fun save(payrollRun: PayrollRun) {
            saved.removeIf { it.id == payrollRun.id }
            saved.add(payrollRun)
        }

        override fun delete(id: PayrollRunId) {
            saved.removeIf { it.id == id }
        }
    }

    private class FakePayrollEmployeeLookup(
        private val employees: List<PayrollEmployeeSnapshot>,
    ) : PayrollEmployeeLookup {
        override fun listActiveEmployeesWithSalary(): List<PayrollEmployeeSnapshot> = employees
    }

    @Test
    fun `rejects creating a run when no active employee has a salary set`() {
        val service =
            CreatePayrollRunService(
                FakePayrollEmployeeLookup(emptyList()),
                FakePayrollRunRepository(),
                passthroughTransactionRunner,
                fixedClock,
            )

        val result = service.create(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"), null)

        assertThat(result).isEqualTo(CreatePayrollRunResult.NoEligibleEmployees)
    }

    @Test
    fun `rejects creating a run when eligible employees span more than one currency`() {
        val employees =
            listOf(
                PayrollEmployeeSnapshot(UUID.randomUUID(), "Egyptian Pound Employee", BigDecimal("15000.00"), "EGP"),
                PayrollEmployeeSnapshot(UUID.randomUUID(), "Euro Employee", BigDecimal("1200.00"), "EUR"),
            )
        val service =
            CreatePayrollRunService(
                FakePayrollEmployeeLookup(employees),
                FakePayrollRunRepository(),
                passthroughTransactionRunner,
                fixedClock,
            )

        val result = service.create(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"), null)

        assertThat(result).isInstanceOf(CreatePayrollRunResult.MixedCurrencies::class.java)
        assertThat((result as CreatePayrollRunResult.MixedCurrencies).currencyCodes).containsExactlyInAnyOrder("EGP", "EUR")
    }

    @Test
    fun `creates a real draft run with one line per eligible employee, all sharing the same currency`() {
        val employees =
            listOf(
                PayrollEmployeeSnapshot(UUID.randomUUID(), "Employee One", BigDecimal("15000.00"), "EGP"),
                PayrollEmployeeSnapshot(UUID.randomUUID(), "Employee Two", BigDecimal("12000.00"), "EGP"),
            )
        val service =
            CreatePayrollRunService(
                FakePayrollEmployeeLookup(employees),
                FakePayrollRunRepository(),
                passthroughTransactionRunner,
                fixedClock,
            )

        val result = service.create(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"), null)

        assertThat(result).isInstanceOf(CreatePayrollRunResult.Created::class.java)
        val run = (result as CreatePayrollRunResult.Created).payrollRun
        assertThat(run.status).isEqualTo(PayrollRunStatus.DRAFT)
        assertThat(run.currencyCode).isEqualTo("EGP")
        assertThat(run.lines).hasSize(2)
        assertThat(run.totalAmount).isEqualByComparingTo(BigDecimal("27000.00"))
    }

    @Test
    fun `rejects creating a run whose period overlaps an existing one`() {
        val employees = listOf(PayrollEmployeeSnapshot(UUID.randomUUID(), "Employee One", BigDecimal("15000.00"), "EGP"))
        val repository = FakePayrollRunRepository()
        val service = CreatePayrollRunService(FakePayrollEmployeeLookup(employees), repository, passthroughTransactionRunner, fixedClock)
        service.create(LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-31"), null)

        val result = service.create(LocalDate.parse("2026-08-15"), LocalDate.parse("2026-09-15"), null)

        assertThat(result).isEqualTo(CreatePayrollRunResult.OverlapsExistingRun)
    }
}
