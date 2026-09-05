package com.wego.identity

import com.wego.identity.application.PasswordHasher
import com.wego.identity.application.UserRepository
import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.User
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
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
 * Proves the first real consumer of `identity:administer`'s intent: real
 * account/role administration, not just an unused permission code (see
 * SECURITY_MODEL.md's "role/permission assignment is schema-only today,
 * with no admin UI or API" gap, closed here).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(NoThrottleConfiguration::class)
class IdentityAdminHttpTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    private val adminEmail = "identity-admin-http-test-admin@example.com"
    private val adminPassword = "a-very-long-admin-password-123"
    private val frontDeskEmail = "identity-admin-http-test-frontdesk@example.com"
    private val frontDeskPassword = "a-very-long-frontdesk-password-123"

    @BeforeEach
    fun seedUsersIfNeeded() {
        if (userRepository.findByEmail(EmailAddress.of(adminEmail)) == null) {
            userRepository.save(
                User.bootstrap(
                    email = EmailAddress.of(adminEmail),
                    passwordHash = passwordHasher.hash(adminPassword),
                    role = RoleCode.of("platform-admin"),
                    now = Instant.now(),
                ),
            )
        }
        if (userRepository.findByEmail(EmailAddress.of(frontDeskEmail)) == null) {
            userRepository.save(
                User.create(
                    email = EmailAddress.of(frontDeskEmail),
                    passwordHash = passwordHasher.hash(frontDeskPassword),
                    roles = setOf(RoleCode.of("front-desk")),
                    now = Instant.now(),
                ),
            )
        }
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
                }.andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        return requireNotNull(Regex(""""token"\s*:\s*"([^"]+)"""").find(body)) { "No token in $body" }.groupValues[1]
    }

    @Test
    fun `full lifecycle - create, list, disable, enable, reset password, reassign roles, and the new user can actually log in`() {
        val adminToken = login(adminEmail, adminPassword)
        val newEmail = "new-staff-member@example.com"

        val createBody =
            mockMvc
                .post("/api/v1/identity/users") {
                    header("Authorization", "Bearer $adminToken")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"$newEmail","password":"a-real-password-123456","roleCodes":["front-desk"]}"""
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.email") { value(newEmail) }
                    jsonPath("$.status") { value("ACTIVE") }
                    jsonPath("$.roles[0]") { value("front-desk") }
                }.andReturn()
                .response.contentAsString
        val newUserId = Regex(""""id"\s*:\s*"([^"]+)"""").find(createBody)!!.groupValues[1]

        // A real login with the password just set — proves this isn't just a database row.
        login(newEmail, "a-real-password-123456")

        mockMvc
            .get("/api/v1/identity/users") { header("Authorization", "Bearer $adminToken") }
            .andExpect {
                status { isOk() }
                jsonPath("$[?(@.email=='$newEmail')]") { exists() }
            }

        mockMvc
            .post("/api/v1/identity/users/$newUserId/disable") { header("Authorization", "Bearer $adminToken") }
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("DISABLED") }
            }

        // A disabled account cannot authenticate — real enforcement, not just a stored flag.
        mockMvc
            .post("/api/v1/identity/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$newEmail","password":"a-real-password-123456"}"""
            }.andExpect { status { isUnauthorized() } }

        mockMvc
            .post("/api/v1/identity/users/$newUserId/enable") { header("Authorization", "Bearer $adminToken") }
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("ACTIVE") }
            }

        mockMvc
            .post("/api/v1/identity/users/$newUserId/reset-password") {
                header("Authorization", "Bearer $adminToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"newPassword":"a-brand-new-password-999888"}"""
            }.andExpect { status { isOk() } }

        // The old password no longer works, the new one does.
        mockMvc
            .post("/api/v1/identity/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$newEmail","password":"a-real-password-123456"}"""
            }.andExpect { status { isUnauthorized() } }
        login(newEmail, "a-brand-new-password-999888")

        mockMvc
            .put("/api/v1/identity/users/$newUserId/roles") {
                header("Authorization", "Bearer $adminToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"roleCodes":["accountant"]}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.roles[0]") { value("accountant") }
            }
    }

    @Test
    fun `creating a user with an unknown role is a clean 400, not a 500`() {
        val adminToken = login(adminEmail, adminPassword)
        mockMvc
            .post("/api/v1/identity/users") {
                header("Authorization", "Bearer $adminToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"someone@example.com","password":"a-real-password-123456","roleCodes":["not-a-real-role"]}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("unknown_role:not-a-real-role") }
            }
    }

    @Test
    fun `creating a user with an already-used email is a clean 409`() {
        val adminToken = login(adminEmail, adminPassword)
        mockMvc
            .post("/api/v1/identity/users") {
                header("Authorization", "Bearer $adminToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"$adminEmail","password":"a-real-password-123456","roleCodes":["front-desk"]}"""
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("email_already_in_use") }
            }
    }

    @Test
    fun `an admin cannot disable their own account`() {
        val adminToken = login(adminEmail, adminPassword)
        val selfId = userRepository.findByEmail(EmailAddress.of(adminEmail))!!.id.value
        mockMvc
            .post("/api/v1/identity/users/$selfId/disable") { header("Authorization", "Bearer $adminToken") }
            .andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("cannot_disable_self") }
            }
    }

    @Test
    fun `an admin cannot change their own roles`() {
        val adminToken = login(adminEmail, adminPassword)
        val selfId = userRepository.findByEmail(EmailAddress.of(adminEmail))!!.id.value
        mockMvc
            .put("/api/v1/identity/users/$selfId/roles") {
                header("Authorization", "Bearer $adminToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"roleCodes":["front-desk"]}"""
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("cannot_change_own_roles") }
            }
    }

    @Test
    fun `full role lifecycle - create a role, list permissions, update its permission set`() {
        val adminToken = login(adminEmail, adminPassword)

        mockMvc
            .get("/api/v1/identity/permissions") { header("Authorization", "Bearer $adminToken") }
            .andExpect {
                status { isOk() }
                jsonPath("$[?(@.code=='diver:view')]") { exists() }
            }

        mockMvc
            .post("/api/v1/identity/roles") {
                header("Authorization", "Bearer $adminToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"code":"http-test-role","description":"A role created by a live test","permissionCodes":["diver:view"]}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.code") { value("http-test-role") }
                jsonPath("$.permissions[0]") { value("diver:view") }
            }

        mockMvc
            .get("/api/v1/identity/roles") { header("Authorization", "Bearer $adminToken") }
            .andExpect {
                status { isOk() }
                jsonPath("$[?(@.code=='http-test-role')]") { exists() }
            }

        mockMvc
            .put("/api/v1/identity/roles/http-test-role/permissions") {
                header("Authorization", "Bearer $adminToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"permissionCodes":["diver:view","diver:manage"]}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.permissions.length()") { value(2) }
            }

        mockMvc
            .post("/api/v1/identity/roles") {
                header("Authorization", "Bearer $adminToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"code":"another-role","description":"desc","permissionCodes":["not-a-real-permission"]}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("unknown_permission:not-a-real-permission") }
            }
    }

    @Test
    fun `every identity-administration mutation is denied to an account without the required permission`() {
        val frontDeskToken = login(frontDeskEmail, frontDeskPassword)
        val someUserId = userRepository.findByEmail(EmailAddress.of(adminEmail))!!.id.value

        mockMvc
            .get("/api/v1/identity/users") { header("Authorization", "Bearer $frontDeskToken") }
            .andExpect { status { isForbidden() } }
        mockMvc
            .post("/api/v1/identity/users") {
                header("Authorization", "Bearer $frontDeskToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"email":"x@example.com","password":"a-real-password-123456","roleCodes":["front-desk"]}"""
            }.andExpect { status { isForbidden() } }
        mockMvc
            .post("/api/v1/identity/users/$someUserId/disable") { header("Authorization", "Bearer $frontDeskToken") }
            .andExpect { status { isForbidden() } }
        mockMvc
            .post("/api/v1/identity/users/$someUserId/enable") { header("Authorization", "Bearer $frontDeskToken") }
            .andExpect { status { isForbidden() } }
        mockMvc
            .post("/api/v1/identity/users/$someUserId/reset-password") {
                header("Authorization", "Bearer $frontDeskToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"newPassword":"whatever-password-123456"}"""
            }.andExpect { status { isForbidden() } }
        mockMvc
            .put("/api/v1/identity/users/$someUserId/roles") {
                header("Authorization", "Bearer $frontDeskToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"roleCodes":["accountant"]}"""
            }.andExpect { status { isForbidden() } }
        mockMvc
            .get("/api/v1/identity/roles") { header("Authorization", "Bearer $frontDeskToken") }
            .andExpect { status { isForbidden() } }
        mockMvc
            .get("/api/v1/identity/permissions") { header("Authorization", "Bearer $frontDeskToken") }
            .andExpect { status { isForbidden() } }
        mockMvc
            .post("/api/v1/identity/roles") {
                header("Authorization", "Bearer $frontDeskToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"code":"sneaky-role","description":"desc","permissionCodes":[]}"""
            }.andExpect { status { isForbidden() } }
        mockMvc
            .put("/api/v1/identity/roles/front-desk/permissions") {
                header("Authorization", "Bearer $frontDeskToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"permissionCodes":["identity:administer"]}"""
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `disabling or resetting a nonexistent user is a clean 404`() {
        val adminToken = login(adminEmail, adminPassword)
        val fakeId = java.util.UUID.randomUUID()
        mockMvc
            .post("/api/v1/identity/users/$fakeId/disable") { header("Authorization", "Bearer $adminToken") }
            .andExpect { status { isNotFound() } }
        mockMvc
            .post("/api/v1/identity/users/$fakeId/reset-password") {
                header("Authorization", "Bearer $adminToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"newPassword":"whatever-password-123456"}"""
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
