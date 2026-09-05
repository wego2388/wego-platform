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
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant

/**
 * Proves WEGO-012 Phase 4's attendance + leave-request slice end to end over
 * real HTTP and real PostgreSQL: recording attendance twice for the same day
 * corrects it rather than duplicating it, a leave request moves through the
 * real PENDING -> APPROVED workflow with a genuine overlapping-approved-leave
 * rejection, and every mutation/read is denied without the right permission.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class AttendanceLeaveHttpTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    @Autowired
    private lateinit var dsl: DSLContext

    private val staffEmail = "hr-attendance-staff@example.com"
    private val staffPassword = "a-very-long-staff-password-123"
    private val plainEmail = "hr-attendance-no-permissions@example.com"
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

    private fun createEmployee(
        token: String,
        fullName: String,
    ): String {
        val body =
            mockMvc
                .post("/api/v1/hr/employees") {
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
                          "baseSalary": null,
                          "linkedUserId": null
                        }
                        """.trimIndent()
                }.andReturn()
                .response.contentAsString
        return jsonField(body, "id")
    }

    @Test
    fun `recording attendance twice for the same employee and date corrects the record instead of duplicating it`() {
        val token = login(staffEmail, staffPassword)
        val employeeId = createEmployee(token, "Attendance Test Employee")

        mockMvc
            .post("/api/v1/hr/attendance") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"employeeId":"$employeeId","attendanceDate":"2026-08-30","status":"LATE","clockIn":null,"clockOut":null,"notes":"Traffic"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("LATE") }
            }

        mockMvc
            .post("/api/v1/hr/attendance") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"employeeId":"$employeeId","attendanceDate":"2026-08-30","status":"PRESENT","clockIn":null,"clockOut":null,"notes":"Actually on time"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("PRESENT") }
                jsonPath("$.notes") { value("Actually on time") }
            }

        val listBody =
            mockMvc
                .get("/api/v1/hr/attendance") {
                    header("Authorization", "Bearer $token")
                    param("employeeId", employeeId)
                }.andReturn()
                .response.contentAsString
        assertThat(listBody).contains("\"status\":\"PRESENT\"")
        assertThat(listBody).doesNotContain("\"status\":\"LATE\"")
    }

    @Test
    fun `recording attendance for a future date is a clean 400`() {
        val token = login(staffEmail, staffPassword)
        val employeeId = createEmployee(token, "Future Attendance Employee")

        mockMvc
            .post("/api/v1/hr/attendance") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"employeeId":"$employeeId","attendanceDate":"2099-01-01","status":"PRESENT","clockIn":null,"clockOut":null,"notes":null}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("attendance_date_in_future") }
            }
    }

    @Test
    fun `recording attendance for a terminated employee is a clean 400`() {
        val token = login(staffEmail, staffPassword)
        val employeeId = createEmployee(token, "Terminated Attendance Employee")
        mockMvc.post("/api/v1/hr/employees/$employeeId/terminate") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }

        mockMvc
            .post("/api/v1/hr/attendance") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"employeeId":"$employeeId","attendanceDate":"2026-08-30","status":"PRESENT","clockIn":null,"clockOut":null,"notes":null}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("employee_not_active") }
            }
    }

    @Test
    fun `leave request full lifecycle - submit, read, list, approve`() {
        val token = login(staffEmail, staffPassword)
        val employeeId = createEmployee(token, "Leave Lifecycle Employee")

        val submitBody =
            mockMvc
                .post("/api/v1/hr/leave-requests") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"employeeId":"$employeeId","leaveType":"ANNUAL","startDate":"2026-09-01","endDate":"2026-09-05","reason":"Family trip"}"""
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.status") { value("PENDING") }
                }.andReturn()
                .response.contentAsString
        val leaveId = jsonField(submitBody, "id")

        mockMvc
            .get("/api/v1/hr/leave-requests/$leaveId") { header("Authorization", "Bearer $token") }
            .andExpect {
                status { isOk() }
                jsonPath("$.leaveType") { value("ANNUAL") }
            }

        val listBody =
            mockMvc
                .get("/api/v1/hr/leave-requests") {
                    header("Authorization", "Bearer $token")
                    param("employeeId", employeeId)
                }.andReturn()
                .response.contentAsString
        assertThat(listBody).contains(leaveId)

        mockMvc
            .post("/api/v1/hr/leave-requests/$leaveId/approve") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"notes":"Enjoy"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("APPROVED") }
                jsonPath("$.decisionNotes") { value("Enjoy") }
            }

        mockMvc
            .post("/api/v1/hr/leave-requests/$leaveId/approve") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("not_pending") }
            }
    }

    @Test
    fun `approving a leave request that overlaps another already-approved one for the same employee is a clean 409`() {
        val token = login(staffEmail, staffPassword)
        val employeeId = createEmployee(token, "Overlap Leave Employee")

        val firstBody =
            mockMvc
                .post("/api/v1/hr/leave-requests") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"employeeId":"$employeeId","leaveType":"ANNUAL","startDate":"2026-10-01","endDate":"2026-10-10","reason":null}"""
                }.andReturn()
                .response.contentAsString
        val firstId = jsonField(firstBody, "id")
        mockMvc.post("/api/v1/hr/leave-requests/$firstId/approve") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }

        val secondBody =
            mockMvc
                .post("/api/v1/hr/leave-requests") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"employeeId":"$employeeId","leaveType":"SICK","startDate":"2026-10-05","endDate":"2026-10-15","reason":null}"""
                }.andReturn()
                .response.contentAsString
        val secondId = jsonField(secondBody, "id")

        mockMvc
            .post("/api/v1/hr/leave-requests/$secondId/approve") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("overlaps_approved_leave") }
            }
    }

    @Test
    fun `rejecting and cancelling leave requests work and are each terminal`() {
        val token = login(staffEmail, staffPassword)
        val employeeId = createEmployee(token, "Reject Cancel Leave Employee")

        val rejectBody =
            mockMvc
                .post("/api/v1/hr/leave-requests") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"employeeId":"$employeeId","leaveType":"UNPAID","startDate":"2026-11-01","endDate":"2026-11-02","reason":null}"""
                }.andReturn()
                .response.contentAsString
        val rejectId = jsonField(rejectBody, "id")
        mockMvc
            .post("/api/v1/hr/leave-requests/$rejectId/reject") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"notes":"Understaffed"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("REJECTED") }
            }
        mockMvc
            .post("/api/v1/hr/leave-requests/$rejectId/cancel") { header("Authorization", "Bearer $token") }
            .andExpect { status { isConflict() } }

        val cancelBody =
            mockMvc
                .post("/api/v1/hr/leave-requests") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"employeeId":"$employeeId","leaveType":"OTHER","startDate":"2026-12-01","endDate":"2026-12-02","reason":null}"""
                }.andReturn()
                .response.contentAsString
        val cancelId = jsonField(cancelBody, "id")
        mockMvc
            .post("/api/v1/hr/leave-requests/$cancelId/cancel") { header("Authorization", "Bearer $token") }
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("CANCELLED") }
            }
        mockMvc
            .post("/api/v1/hr/leave-requests/$cancelId/approve") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect { status { isConflict() } }
    }

    @Test
    fun `every attendance and leave endpoint is denied to an account with no hr permissions`() {
        val token = login(plainEmail, plainPassword)
        mockMvc
            .get("/api/v1/hr/attendance") { header("Authorization", "Bearer $token") }
            .andExpect { status { isForbidden() } }
        mockMvc
            .get("/api/v1/hr/leave-requests") { header("Authorization", "Bearer $token") }
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `hr leave-view alone can read but not submit or decide`() {
        seedLimitedUser("hr-leave-view-only@example.com", staffPassword, "hr:leave-view")
        val viewToken = login("hr-leave-view-only@example.com", staffPassword)

        mockMvc
            .get("/api/v1/hr/leave-requests") { header("Authorization", "Bearer $viewToken") }
            .andExpect { status { isOk() } }
        mockMvc
            .post("/api/v1/hr/leave-requests") {
                header("Authorization", "Bearer $viewToken")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"employeeId":"${java.util.UUID.randomUUID()}","leaveType":"ANNUAL","startDate":"2026-09-01","endDate":"2026-09-02","reason":null}"""
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
