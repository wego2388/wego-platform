package com.wego.divers

import com.wego.generated.jooq.tables.IdentityRole.IDENTITY_ROLE
import com.wego.generated.jooq.tables.IdentityRolePermission.IDENTITY_ROLE_PERMISSION
import com.wego.identity.application.PasswordHasher
import com.wego.identity.application.UserRepository
import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.User
import com.wego.identity.domain.UserId
import com.wego.identity.domain.UserStatus
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
import java.util.UUID

/**
 * Proves the course-enrollment pipeline end to end over real HTTP and real
 * PostgreSQL: a real diver enrolls in a real COURSE offering, an
 * instructor is assigned, a skill is evaluated, and the enrollment
 * advances through the whole real Lead -> Theory -> Pool -> Open Water ->
 * Certified pipeline — plus the real guards (only COURSE offerings can be
 * enrolled into, a finished enrollment cannot be touched further).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class CourseEnrollmentHttpTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    @Autowired
    private lateinit var dsl: DSLContext

    private val staffEmail = "course-staff@example.com"
    private val staffPassword = "a-very-long-staff-password-123"
    private val viewOnlyEmail = "course-view-only@example.com"
    private val viewOnlyPassword = "a-very-long-view-password-123"

    @BeforeEach
    fun seedUsersIfNeeded() {
        seedUserIfNeeded(staffEmail, staffPassword, roles = setOf("platform-admin"))
        seedLimitedUser(viewOnlyEmail, viewOnlyPassword, "course:view")
    }

    private fun seedLimitedUser(
        email: String,
        password: String,
        permission: String,
    ): UUID {
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
        return seedUserIfNeeded(email, password, roles = setOf(roleCode))
    }

    private fun seedUserIfNeeded(
        email: String,
        password: String,
        roles: Set<String>,
    ): UUID {
        val existing = userRepository.findByEmail(EmailAddress.of(email))
        if (existing != null) return existing.id.value
        val user =
            User(
                id = UserId.generate(),
                email = EmailAddress.of(email),
                passwordHash = passwordHasher.hash(password),
                status = UserStatus.ACTIVE,
                roles = roles.map(RoleCode::of).toSet(),
                createdAt = Instant.now(),
                failedLoginCount = 0,
                lockedUntil = null,
            )
        userRepository.save(user)
        return user.id.value
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

    private fun createDiver(
        token: String,
        fullName: String,
    ): String =
        mockMvc
            .post("/api/v1/divers/divers") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"fullName": "$fullName", "email": "course-diver@example.com", "totalLoggedDives": 0, "certifications": []}"""
            }.andExpect { status { isCreated() } }
            .andReturn()
            .response.contentAsString
            .let { jsonField(it, "id") }

    private fun createOffering(
        token: String,
        title: String,
        offeringType: String = "COURSE",
    ): String =
        mockMvc
            .post("/api/v1/divers/offerings") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "offeringType": "$offeringType",
                      "title": "$title",
                      "startsOn": "2026-09-01",
                      "pricingBasis": "FLAT",
                      "unitPrice": {"amount": "350.00", "currencyCode": "EUR"}
                    }
                    """.trimIndent()
            }.andExpect { status { isCreated() } }
            .andReturn()
            .response.contentAsString
            .let { jsonField(it, "id") }

    @Test
    fun `full pipeline - enroll, assign instructor, evaluate a skill, advance through every real stage to certified`() {
        val token = login(staffEmail, staffPassword)
        val diverId = createDiver(token, "Course Pipeline Diver")
        val offeringId = createOffering(token, "PADI Open Water HTTP")

        val enrollBody =
            mockMvc
                .post("/api/v1/divers/course-enrollments") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"diverId": "$diverId", "offeringId": "$offeringId"}"""
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.stage") { value("LEAD") }
                }.andReturn()
                .response.contentAsString
        val enrollmentId = jsonField(enrollBody, "id")

        val instructorId = userRepository.findByEmail(EmailAddress.of(staffEmail))!!.id.value
        mockMvc
            .put("/api/v1/divers/course-enrollments/$enrollmentId/instructor") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"instructorUserId": "$instructorId"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.instructorUserId") { value(instructorId.toString()) }
            }

        mockMvc
            .post("/api/v1/divers/course-enrollments/$enrollmentId/skill-evaluations") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"skillName": "Mask clearing", "passed": true, "evaluatedOn": "2026-09-02"}"""
            }.andExpect { status { isCreated() } }

        mockMvc
            .get("/api/v1/divers/course-enrollments/$enrollmentId/skill-evaluations") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$[0].skillName") { value("Mask clearing") }
            }

        val realStages = listOf("THEORY", "POOL", "OPEN_WATER", "CERTIFIED")
        for (expectedStage in realStages) {
            mockMvc
                .post("/api/v1/divers/course-enrollments/$enrollmentId/advance") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.stage") { value(expectedStage) }
                }
        }

        mockMvc
            .post("/api/v1/divers/course-enrollments/$enrollmentId/advance") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("enrollment_finished") }
            }
    }

    @Test
    fun `withdrawing is terminal and stops further advancement`() {
        val token = login(staffEmail, staffPassword)
        val diverId = createDiver(token, "Course Withdraw Diver")
        val offeringId = createOffering(token, "PADI Advanced HTTP")
        val enrollmentId =
            mockMvc
                .post("/api/v1/divers/course-enrollments") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"diverId": "$diverId", "offeringId": "$offeringId"}"""
                }.andReturn()
                .response.contentAsString
                .let { jsonField(it, "id") }

        mockMvc
            .post("/api/v1/divers/course-enrollments/$enrollmentId/withdraw") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.stage") { value("WITHDRAWN") }
            }

        mockMvc
            .post("/api/v1/divers/course-enrollments/$enrollmentId/advance") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("enrollment_finished") }
            }
    }

    @Test
    fun `rejects enrolling into an offering that is not a real course`() {
        val token = login(staffEmail, staffPassword)
        val diverId = createDiver(token, "Non Course Diver")
        val tripOfferingId = createOffering(token, "Not A Course HTTP", offeringType = "DIVE_TRIP")

        mockMvc
            .post("/api/v1/divers/course-enrollments") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"diverId": "$diverId", "offeringId": "$tripOfferingId"}"""
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("offering_is_not_a_course") }
            }
    }

    @Test
    fun `rejects enrolling an unknown diver`() {
        val token = login(staffEmail, staffPassword)
        val offeringId = createOffering(token, "Unknown Diver Course HTTP")

        mockMvc
            .post("/api/v1/divers/course-enrollments") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"diverId": "${UUID.randomUUID()}", "offeringId": "$offeringId"}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("diver_not_found") }
            }
    }

    @Test
    fun `a course view-only role can list but not enroll`() {
        val viewToken = login(viewOnlyEmail, viewOnlyPassword)

        mockMvc
            .get("/api/v1/divers/course-enrollments") {
                header("Authorization", "Bearer $viewToken")
            }.andExpect { status { isOk() } }

        mockMvc
            .post("/api/v1/divers/course-enrollments") {
                header("Authorization", "Bearer $viewToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"diverId": "${UUID.randomUUID()}", "offeringId": "${UUID.randomUUID()}"}"""
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
