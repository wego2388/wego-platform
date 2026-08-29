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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.util.UUID

/**
 * Proves the diver-profile slice end to end over real HTTP and real
 * PostgreSQL: staff creates a profile, lists/searches it, updates it,
 * archives it — and permission separation is proven with genuinely
 * *limited*-role users, matching DiversHttpTest's own standard for WEGO-002.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class DiverHttpTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    @Autowired
    private lateinit var dsl: DSLContext

    private val staffEmail = "diver-staff@example.com"
    private val staffPassword = "a-very-long-staff-password-123"
    private val viewOnlyEmail = "diver-view-only@example.com"
    private val viewOnlyPassword = "a-very-long-view-password-123"
    private val noPermissionEmail = "diver-no-permissions@example.com"
    private val noPermissionPassword = "a-very-long-plain-password-123"

    @BeforeEach
    fun seedUsersIfNeeded() {
        seedUserIfNeeded(staffEmail, staffPassword, roles = setOf("platform-admin"))
        seedLimitedUser(viewOnlyEmail, viewOnlyPassword, "diver:view")
        seedUserIfNeeded(noPermissionEmail, noPermissionPassword, roles = emptySet())
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

    private fun createDiverRequest(fullName: String) =
        """
        {
          "fullName": "$fullName",
          "nationality": "British",
          "primaryLanguage": "English",
          "email": "ada@example.com",
          "phone": "+201066461010",
          "emergencyContactName": "Anna Isaacs",
          "emergencyContactPhone": "+201066461011",
          "totalLoggedDives": 12,
          "maxDepthMeters": 18.5,
          "lastDiveOn": "2026-08-01",
          "bcdSize": "M",
          "finSize": "42",
          "wetsuitSize": "L",
          "certifications": [
            {"agency": "PADI", "level": "Advanced Open Water", "certificationNumber": "AOW-12345", "issuedOn": "2024-05-01"}
          ]
        }
        """.trimIndent()

    @Test
    fun `full lifecycle - create, list, search, get, update, archive`() {
        val token = login(staffEmail, staffPassword)

        val createBody =
            mockMvc
                .post("/api/v1/divers/divers") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = createDiverRequest("Ada Lovelace HTTP")
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.fullName") { value("Ada Lovelace HTTP") }
                    jsonPath("$.status") { value("ACTIVE") }
                    jsonPath("$.certifications[0].agency") { value("PADI") }
                }.andReturn()
                .response.contentAsString
        val diverId = jsonField(createBody, "id")

        mockMvc
            .get("/api/v1/divers/divers/$diverId") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.fullName") { value("Ada Lovelace HTTP") }
            }

        mockMvc
            .get("/api/v1/divers/divers") {
                header("Authorization", "Bearer $token")
                param("search", "Lovelace HTTP")
            }.andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value(diverId) }
            }

        mockMvc
            .put("/api/v1/divers/divers/$diverId") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "fullName": "Ada K. Lovelace",
                      "email": "ada@example.com",
                      "totalLoggedDives": 20,
                      "maxDepthMeters": 24.0,
                      "certifications": []
                    }
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.fullName") { value("Ada K. Lovelace") }
                jsonPath("$.totalLoggedDives") { value(20) }
                jsonPath("$.certifications.length()") { value(0) }
            }

        mockMvc
            .delete("/api/v1/divers/divers/$diverId") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("ARCHIVED") }
            }

        // Archiving again is a conflict, not a silent success.
        mockMvc
            .delete("/api/v1/divers/divers/$diverId") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("already_archived") }
            }
    }

    @Test
    fun `the roster list omits sensitive PII that only the single-record GET returns`() {
        val token = login(staffEmail, staffPassword)
        val createBody =
            mockMvc
                .post("/api/v1/divers/divers") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = createDiverRequest("PII Sweep Diver")
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        val diverId = jsonField(createBody, "id")

        val listBody =
            mockMvc
                .get("/api/v1/divers/divers") {
                    header("Authorization", "Bearer $token")
                    param("search", "PII Sweep Diver")
                }.andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        assertThat(listBody).doesNotContain("ada@example.com")
        assertThat(listBody).doesNotContain("Anna Isaacs")
        assertThat(listBody).doesNotContain("AOW-12345")
        assertThat(listBody).doesNotContain("emergencyContactName")
        assertThat(listBody).doesNotContain("medicalNotes")

        mockMvc
            .get("/api/v1/divers/divers/$diverId") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.email") { value("ada@example.com") }
                jsonPath("$.emergencyContactName") { value("Anna Isaacs") }
            }
    }

    @Test
    fun `archiving redacts emergency contact and medical notes, visible through the real GET afterward`() {
        val token = login(staffEmail, staffPassword)
        val createBody =
            mockMvc
                .post("/api/v1/divers/divers") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = createDiverRequest("Redaction Sweep Diver")
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        val diverId = jsonField(createBody, "id")

        mockMvc.delete("/api/v1/divers/divers/$diverId") { header("Authorization", "Bearer $token") }

        mockMvc
            .get("/api/v1/divers/divers/$diverId") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("ARCHIVED") }
                jsonPath("$.emergencyContactName") { doesNotExist() }
                jsonPath("$.emergencyContactPhone") { doesNotExist() }
                jsonPath("$.medicalNotes") { doesNotExist() }
            }
    }

    @Test
    fun `rejects a diver with neither email nor phone as a clean 400, never a raw 500`() {
        val token = login(staffEmail, staffPassword)

        mockMvc
            .post("/api/v1/divers/divers") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"fullName": "No Contact Diver", "certifications": []}"""
            }.andExpect { status { is4xxClientError() } }
    }

    @Test
    fun `a view-only role can list but not create`() {
        val viewToken = login(viewOnlyEmail, viewOnlyPassword)

        mockMvc
            .get("/api/v1/divers/divers") {
                header("Authorization", "Bearer $viewToken")
            }.andExpect { status { isOk() } }

        mockMvc
            .post("/api/v1/divers/divers") {
                header("Authorization", "Bearer $viewToken")
                contentType = MediaType.APPLICATION_JSON
                content = createDiverRequest("Should Be Forbidden")
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `a user with no divers permissions is denied entirely`() {
        val token = login(noPermissionEmail, noPermissionPassword)

        mockMvc
            .get("/api/v1/divers/divers") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `an unknown diver id is a clean 404`() {
        val token = login(staffEmail, staffPassword)

        mockMvc
            .get("/api/v1/divers/divers/${UUID.randomUUID()}") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isNotFound() } }
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
