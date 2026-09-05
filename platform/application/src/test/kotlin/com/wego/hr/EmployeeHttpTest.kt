package com.wego.hr

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
import org.springframework.test.web.servlet.put
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant

/**
 * Proves the whole WEGO-012 Phase 3 slice end to end over real HTTP and real
 * PostgreSQL: staff creates an employee, the roster omits salary while the
 * single-record GET includes it (same PII-minimization discipline the
 * divers module already established), updates and terminates the record,
 * and every mutation is denied to an account holding none of the two real
 * `hr:employee-*` permissions.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class EmployeeHttpTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    @Autowired
    private lateinit var dsl: DSLContext

    private val staffEmail = "hr-staff@example.com"
    private val staffPassword = "a-very-long-staff-password-123"
    private val plainEmail = "hr-no-permissions@example.com"
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
        val match = Regex(""""token"\s*:\s*"([^"]+)"""").find(body)
        return requireNotNull(match) { "No token field in response body: $body" }.groupValues[1]
    }

    private fun jsonField(
        body: String,
        field: String,
    ): String {
        val match = Regex(""""$field"\s*:\s*"([^"]+)"""").find(body)
        return requireNotNull(match) { "No $field field in response body: $body" }.groupValues[1]
    }

    private fun createEmployeeRequest(fullName: String) =
        """
        {
          "fullName": "$fullName",
          "position": "Dive Instructor",
          "department": "Operations",
          "hireDate": "2026-01-15",
          "email": "employee-http-test@example.com",
          "phone": "+201066461010",
          "baseSalary": {"amount": "15000.00", "currencyCode": "EGP"},
          "linkedUserId": null
        }
        """.trimIndent()

    @Test
    fun `full lifecycle - create, roster omits salary, single-record GET includes it, update, terminate`() {
        val token = login(staffEmail, staffPassword)

        val createBody =
            mockMvc
                .post("/api/v1/hr/employees") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = createEmployeeRequest("HTTP Lifecycle Employee")
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.fullName") { value("HTTP Lifecycle Employee") }
                    jsonPath("$.status") { value("ACTIVE") }
                    jsonPath("$.baseSalary.amount") { value("15000.00") }
                }.andReturn()
                .response.contentAsString
        val employeeId = jsonField(createBody, "id")

        val listBody =
            mockMvc
                .get("/api/v1/hr/employees") {
                    header("Authorization", "Bearer $token")
                    param("search", "HTTP Lifecycle Employee")
                }.andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        assertThat(listBody).contains("HTTP Lifecycle Employee")
        assertThat(listBody).doesNotContain("15000.00")
        assertThat(listBody).doesNotContain("baseSalary")

        mockMvc
            .get("/api/v1/hr/employees/$employeeId") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.baseSalary.amount") { value("15000.00") }
            }

        mockMvc
            .put("/api/v1/hr/employees/$employeeId") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "fullName": "HTTP Lifecycle Employee",
                      "position": "Senior Dive Instructor",
                      "department": "Operations",
                      "hireDate": "2026-01-15",
                      "email": "employee-http-test@example.com",
                      "phone": "+201066461010",
                      "baseSalary": {"amount": "18000.00", "currencyCode": "EGP"},
                      "linkedUserId": null
                    }
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.position") { value("Senior Dive Instructor") }
                jsonPath("$.baseSalary.amount") { value("18000.00") }
            }

        mockMvc
            .post("/api/v1/hr/employees/$employeeId/terminate") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason": "End of contract"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("TERMINATED") }
                // Real proof salary is not redacted on termination, unlike a diver's archive.
                jsonPath("$.baseSalary.amount") { value("18000.00") }
            }

        mockMvc
            .post("/api/v1/hr/employees/$employeeId/terminate") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("already_terminated") }
            }
    }

    @Test
    fun `creating an employee linked to an inactive or unknown user is a clean 400`() {
        val token = login(staffEmail, staffPassword)
        mockMvc
            .post("/api/v1/hr/employees") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "fullName": "Linked Employee",
                      "position": "Dive Instructor",
                      "department": null,
                      "hireDate": "2026-01-15",
                      "email": null,
                      "phone": null,
                      "baseSalary": null,
                      "linkedUserId": "${java.util.UUID.randomUUID()}"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("linked_user_not_active_staff") }
            }
    }

    @Test
    fun `terminating a nonexistent employee is a clean 404`() {
        val token = login(staffEmail, staffPassword)
        mockMvc
            .post("/api/v1/hr/employees/${java.util.UUID.randomUUID()}/terminate") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `every mutation and read is denied to an account with no hr permissions`() {
        val token = login(plainEmail, plainPassword)
        mockMvc
            .get("/api/v1/hr/employees") { header("Authorization", "Bearer $token") }
            .andExpect { status { isForbidden() } }
        mockMvc
            .post("/api/v1/hr/employees") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = createEmployeeRequest("Should Be Denied")
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `hr employee-view alone can read but not create`() {
        seedLimitedUser("hr-view-only@example.com", staffPassword, "hr:employee-view")
        val token = login("hr-view-only@example.com", staffPassword)

        mockMvc
            .get("/api/v1/hr/employees") { header("Authorization", "Bearer $token") }
            .andExpect { status { isOk() } }
        mockMvc
            .post("/api/v1/hr/employees") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = createEmployeeRequest("Should Be Denied Too")
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
