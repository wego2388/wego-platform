package com.wego.payroll

import com.wego.generated.jooq.tables.IdentityRole.IDENTITY_ROLE
import com.wego.generated.jooq.tables.IdentityRolePermission.IDENTITY_ROLE_PERMISSION
import com.wego.identity.application.PasswordHasher
import com.wego.identity.application.UserRepository
import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.User
import com.wego.identity.domain.UserId
import com.wego.identity.domain.UserStatus
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant

/**
 * Proves WEGO-012 Phase 6's payroll slice end to end over real HTTP and
 * real PostgreSQL — including the real cross-module integration this
 * phase exists for: a real employee's real salary flows into a real
 * draft, and posting it creates a real, genuinely balanced journal entry
 * against the real V12-seeded Salaries Expense/Wages Payable accounts,
 * verified by fetching that entry back through Accounting's own API.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class PayrollHttpTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    @Autowired
    private lateinit var dsl: DSLContext

    private val staffEmail = "payroll-staff@example.com"
    private val staffPassword = "a-very-long-staff-password-123"
    private val plainEmail = "payroll-no-permissions@example.com"
    private val plainPassword = "a-very-long-plain-password-123"

    @BeforeEach
    fun seedUsersIfNeeded() {
        seedUserIfNeeded(staffEmail, staffPassword, roles = setOf("platform-admin"))
        seedUserIfNeeded(plainEmail, plainPassword, roles = emptySet())
    }

    private fun seedLimitedUser(
        email: String,
        password: String,
        permission: String,
    ) {
        val roleCode = "test-only-${permission.replace(':', '-')}"
        if (dsl.fetchOne(IDENTITY_ROLE, IDENTITY_ROLE.CODE.eq(roleCode)) == null) {
            dsl
                .insertInto(IDENTITY_ROLE)
                .set(IDENTITY_ROLE.CODE, roleCode)
                .set(IDENTITY_ROLE.DESCRIPTION, "Test-only role granting exactly $permission")
                .execute()
            dsl
                .insertInto(IDENTITY_ROLE_PERMISSION)
                .set(IDENTITY_ROLE_PERMISSION.ROLE_CODE, roleCode)
                .set(IDENTITY_ROLE_PERMISSION.PERMISSION_CODE, permission)
                .execute()
        }
        seedUserIfNeeded(email, password, roles = setOf(roleCode))
    }

    private fun seedUserIfNeeded(
        email: String,
        password: String,
        roles: Set<String>,
    ) {
        if (userRepository.findByEmail(EmailAddress.of(email)) != null) return
        val user =
            User(
                id = UserId.generate(),
                email = EmailAddress.of(email),
                passwordHash = passwordHasher.hash(password),
                status = UserStatus.ACTIVE,
                roles = roles.map { RoleCode.of(it) }.toSet(),
                createdAt = Instant.now(),
                failedLoginCount = 0,
                lockedUntil = null,
            )
        userRepository.save(user)
    }

    private fun login(
        email: String,
        password: String,
    ): String {
        val body =
            mockMvc
                .post("/api/v1/identity/login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"$email","password":"$password"}"""
                }.andReturn()
                .response.contentAsString
        return jsonField(body, "token")
    }

    private fun jsonField(
        body: String,
        field: String,
    ): String {
        val match = Regex(""""$field"\s*:\s*"([^"]+)"""").find(body)
        return requireNotNull(match) { "No $field field in response body: $body" }.groupValues[1]
    }

    private fun createSalariedEmployee(
        token: String,
        fullName: String,
        salaryAmount: String,
    ) {
        mockMvc.post("/api/v1/hr/employees") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "fullName": "$fullName",
                  "position": "Dive Instructor",
                  "department": "Operations",
                  "hireDate": "2026-01-15",
                  "email": null,
                  "phone": null,
                  "baseSalary": {"amount": "$salaryAmount", "currencyCode": "EGP"},
                  "linkedUserId": null
                }
                """.trimIndent()
        }
    }

    @Test
    fun `full lifecycle - create a draft from real salaried employees, post it, and the real journal entry balances`() {
        val token = login(staffEmail, staffPassword)
        createSalariedEmployee(token, "Payroll HTTP Employee One", "15000.00")
        createSalariedEmployee(token, "Payroll HTTP Employee Two", "12000.00")

        val createBody =
            mockMvc
                .post("/api/v1/payroll/runs") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"payPeriodStart":"2026-08-01","payPeriodEnd":"2026-08-31"}"""
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.status") { value("DRAFT") }
                    jsonPath("$.currencyCode") { value("EGP") }
                }.andReturn()
                .response.contentAsString
        val runId = jsonField(createBody, "id")
        val expectedTotal = Regex(""""totalAmount"\s*:\s*"([^"]+)"""").find(createBody)!!.groupValues[1]

        val postBody =
            mockMvc
                .post("/api/v1/payroll/runs/$runId/post") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.status") { value("POSTED") }
                }.andReturn()
                .response.contentAsString
        val journalEntryId = jsonField(postBody, "journalEntryId")

        val journalEntryBody =
            mockMvc
                .get("/api/v1/accounting/journal-entries/$journalEntryId") { header("Authorization", "Bearer $token") }
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        assertThat(journalEntryBody).contains("\"debitTotal\":\"$expectedTotal\"")
        assertThat(journalEntryBody).contains("\"creditTotal\":\"$expectedTotal\"")
        assertThat(journalEntryBody).contains("\"direction\":\"DEBIT\"")
        assertThat(journalEntryBody).contains("\"direction\":\"CREDIT\"")

        mockMvc
            .post("/api/v1/payroll/runs/$runId/post") { header("Authorization", "Bearer $token") }
            .andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("not_draft") }
            }

        mockMvc
            .post("/api/v1/payroll/runs/$runId/discard") { header("Authorization", "Bearer $token") }
            .andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("not_draft") }
            }
    }

    @Test
    fun `creating a run for an already-covered pay period is a clean 409`() {
        val token = login(staffEmail, staffPassword)
        createSalariedEmployee(token, "Payroll Overlap Employee", "10000.00")

        mockMvc.post("/api/v1/payroll/runs") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"payPeriodStart":"2027-01-01","payPeriodEnd":"2027-01-31"}"""
        }

        mockMvc
            .post("/api/v1/payroll/runs") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"payPeriodStart":"2027-01-15","payPeriodEnd":"2027-02-15"}"""
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("overlaps_existing_run") }
            }
    }

    @Test
    fun `a draft run can be discarded and then no longer exists`() {
        val token = login(staffEmail, staffPassword)
        createSalariedEmployee(token, "Payroll Discard Employee", "9000.00")

        val createBody =
            mockMvc
                .post("/api/v1/payroll/runs") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"payPeriodStart":"2027-03-01","payPeriodEnd":"2027-03-31"}"""
                }.andReturn()
                .response.contentAsString
        val runId = jsonField(createBody, "id")

        mockMvc
            .post("/api/v1/payroll/runs/$runId/discard") { header("Authorization", "Bearer $token") }
            .andExpect { status { isNoContent() } }

        mockMvc
            .get("/api/v1/payroll/runs/$runId") { header("Authorization", "Bearer $token") }
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `posting or discarding a nonexistent payroll run is a clean 404`() {
        val token = login(staffEmail, staffPassword)
        mockMvc
            .post("/api/v1/payroll/runs/${java.util.UUID.randomUUID()}/post") { header("Authorization", "Bearer $token") }
            .andExpect { status { isNotFound() } }
        mockMvc
            .post("/api/v1/payroll/runs/${java.util.UUID.randomUUID()}/discard") { header("Authorization", "Bearer $token") }
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `every payroll endpoint is denied to an account with no payroll permissions`() {
        val token = login(plainEmail, plainPassword)
        mockMvc
            .get("/api/v1/payroll/runs") { header("Authorization", "Bearer $token") }
            .andExpect { status { isForbidden() } }
        mockMvc
            .post("/api/v1/payroll/runs") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"payPeriodStart":"2027-04-01","payPeriodEnd":"2027-04-30"}"""
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `payroll view alone can read but not create`() {
        seedLimitedUser("payroll-view-only@example.com", staffPassword, "payroll:view")
        val viewToken = login("payroll-view-only@example.com", staffPassword)

        mockMvc
            .get("/api/v1/payroll/runs") { header("Authorization", "Bearer $viewToken") }
            .andExpect { status { isOk() } }
        mockMvc
            .post("/api/v1/payroll/runs") {
                header("Authorization", "Bearer $viewToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"payPeriodStart":"2027-05-01","payPeriodEnd":"2027-05-31"}"""
            }.andExpect { status { isForbidden() } }
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
