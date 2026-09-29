package com.wego.payroll

import com.wego.generated.jooq.tables.HrEmployee.HR_EMPLOYEE
import com.wego.generated.jooq.tables.PayrollLine.PAYROLL_LINE
import com.wego.generated.jooq.tables.PayrollRun.PAYROLL_RUN
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class PayrollMigrationIntegrationTest(
    @Autowired private val flyway: Flyway,
) {
    @Test
    fun `boot auto migrates the payroll schema and jooq types honor its constraints`() {
        assertThat(
            flyway.info().applied().map { it.version.toString() },
        ).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13")

        postgres.createConnection("").use { connection ->
            val dsl = DSL.using(connection, SQLDialect.POSTGRES)
            val now = OffsetDateTime.of(2026, 8, 31, 0, 0, 0, 0, ZoneOffset.UTC)

            val employeeId = UUID.randomUUID()
            dsl
                .insertInto(HR_EMPLOYEE)
                .set(HR_EMPLOYEE.ID, employeeId)
                .set(HR_EMPLOYEE.FULL_NAME, "Payroll Constraint Test Employee")
                .set(HR_EMPLOYEE.POSITION, "Dive Instructor")
                .set(HR_EMPLOYEE.HIRE_DATE, LocalDate.of(2026, 1, 1))
                .set(HR_EMPLOYEE.STATUS, "ACTIVE")
                .set(HR_EMPLOYEE.CREATED_AT, now)
                .execute()

            // Real constraint: a pay period's end must not precede its start.
            assertThatThrownBy {
                dsl
                    .insertInto(PAYROLL_RUN)
                    .set(PAYROLL_RUN.ID, UUID.randomUUID())
                    .set(PAYROLL_RUN.PAY_PERIOD_START, LocalDate.of(2026, 8, 31))
                    .set(PAYROLL_RUN.PAY_PERIOD_END, LocalDate.of(2026, 8, 1))
                    .set(PAYROLL_RUN.CURRENCY_CODE, "EGP")
                    .set(PAYROLL_RUN.STATUS, "DRAFT")
                    .set(PAYROLL_RUN.CREATED_AT, now)
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("payroll_run_period_valid")

            // Real constraint: a POSTED run must carry real posting fields.
            assertThatThrownBy {
                dsl
                    .insertInto(PAYROLL_RUN)
                    .set(PAYROLL_RUN.ID, UUID.randomUUID())
                    .set(PAYROLL_RUN.PAY_PERIOD_START, LocalDate.of(2026, 8, 1))
                    .set(PAYROLL_RUN.PAY_PERIOD_END, LocalDate.of(2026, 8, 31))
                    .set(PAYROLL_RUN.CURRENCY_CODE, "EGP")
                    .set(PAYROLL_RUN.STATUS, "POSTED")
                    .set(PAYROLL_RUN.CREATED_AT, now)
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("payroll_run_posted_fields_match_status")

            val runId = UUID.randomUUID()
            dsl
                .insertInto(PAYROLL_RUN)
                .set(PAYROLL_RUN.ID, runId)
                .set(PAYROLL_RUN.PAY_PERIOD_START, LocalDate.of(2026, 8, 1))
                .set(PAYROLL_RUN.PAY_PERIOD_END, LocalDate.of(2026, 8, 31))
                .set(PAYROLL_RUN.CURRENCY_CODE, "EGP")
                .set(PAYROLL_RUN.STATUS, "DRAFT")
                .set(PAYROLL_RUN.CREATED_AT, now)
                .execute()

            // Real constraint: a payroll line's amount must be positive.
            assertThatThrownBy {
                dsl
                    .insertInto(PAYROLL_LINE)
                    .set(PAYROLL_LINE.ID, UUID.randomUUID())
                    .set(PAYROLL_LINE.PAYROLL_RUN_ID, runId)
                    .set(PAYROLL_LINE.EMPLOYEE_ID, employeeId)
                    .set(PAYROLL_LINE.AMOUNT, BigDecimal.ZERO)
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("payroll_line_amount_positive")

            dsl
                .insertInto(PAYROLL_LINE)
                .set(PAYROLL_LINE.ID, UUID.randomUUID())
                .set(PAYROLL_LINE.PAYROLL_RUN_ID, runId)
                .set(PAYROLL_LINE.EMPLOYEE_ID, employeeId)
                .set(PAYROLL_LINE.AMOUNT, BigDecimal("15000.00"))
                .execute()

            // Real constraint: one employee can only appear once per run.
            assertThatThrownBy {
                dsl
                    .insertInto(PAYROLL_LINE)
                    .set(PAYROLL_LINE.ID, UUID.randomUUID())
                    .set(PAYROLL_LINE.PAYROLL_RUN_ID, runId)
                    .set(PAYROLL_LINE.EMPLOYEE_ID, employeeId)
                    .set(PAYROLL_LINE.AMOUNT, BigDecimal("5000.00"))
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("payroll_line_employee_unique_per_run")
        }
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("wego_test")
                .withUsername("wego_test")
                .withPassword("wego_test")

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.enabled") { true }
        }
    }
}
