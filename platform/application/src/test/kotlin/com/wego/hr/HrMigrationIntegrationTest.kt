package com.wego.hr

import com.wego.generated.jooq.tables.HrEmployee.HR_EMPLOYEE
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
class HrMigrationIntegrationTest(
    @Autowired private val flyway: Flyway,
) {
    @Test
    fun `boot auto migrates the hr schema and generated jooq types honor its constraints`() {
        assertThat(flyway.info().applied().map { it.version.toString() }).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10")

        postgres.createConnection("").use { connection ->
            val dsl = DSL.using(connection, SQLDialect.POSTGRES)
            val now = OffsetDateTime.of(2026, 8, 30, 0, 0, 0, 0, ZoneOffset.UTC)

            val inserted =
                dsl
                    .insertInto(HR_EMPLOYEE)
                    .set(HR_EMPLOYEE.ID, UUID.randomUUID())
                    .set(HR_EMPLOYEE.FULL_NAME, "Constraint Test Employee")
                    .set(HR_EMPLOYEE.POSITION, "Dive Instructor")
                    .set(HR_EMPLOYEE.HIRE_DATE, LocalDate.of(2026, 1, 1))
                    .set(HR_EMPLOYEE.STATUS, "ACTIVE")
                    .set(HR_EMPLOYEE.CREATED_AT, now)
                    .execute()
            assertThat(inserted).isEqualTo(1)

            // Real constraint: a salary amount without a currency code (or vice versa) must be rejected.
            assertThatThrownBy {
                dsl
                    .insertInto(HR_EMPLOYEE)
                    .set(HR_EMPLOYEE.ID, UUID.randomUUID())
                    .set(HR_EMPLOYEE.FULL_NAME, "Mismatched Salary Employee")
                    .set(HR_EMPLOYEE.POSITION, "Dive Instructor")
                    .set(HR_EMPLOYEE.HIRE_DATE, LocalDate.of(2026, 1, 1))
                    .set(HR_EMPLOYEE.STATUS, "ACTIVE")
                    .set(HR_EMPLOYEE.CREATED_AT, now)
                    .set(HR_EMPLOYEE.BASE_SALARY_AMOUNT, BigDecimal("15000.00"))
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("hr_employee_base_salary_pair")

            // Real constraint: a TERMINATED row must carry a terminated_at timestamp.
            assertThatThrownBy {
                dsl
                    .insertInto(HR_EMPLOYEE)
                    .set(HR_EMPLOYEE.ID, UUID.randomUUID())
                    .set(HR_EMPLOYEE.FULL_NAME, "Wrongly Terminated Employee")
                    .set(HR_EMPLOYEE.POSITION, "Dive Instructor")
                    .set(HR_EMPLOYEE.HIRE_DATE, LocalDate.of(2026, 1, 1))
                    .set(HR_EMPLOYEE.STATUS, "TERMINATED")
                    .set(HR_EMPLOYEE.CREATED_AT, now)
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("hr_employee_terminated_at_matches_status")
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
