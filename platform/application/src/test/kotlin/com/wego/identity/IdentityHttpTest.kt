package com.wego.identity

import com.wego.generated.jooq.tables.IdentityAuditEvent.IDENTITY_AUDIT_EVENT
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
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant

/**
 * A minimal `@PreAuthorize`-gated route used only to prove the RBAC wiring
 * (session -> resolved permissions -> `hasAuthority` decision) actually
 * enforces a specific permission end to end. Not a production capability —
 * registered only for this test's Spring context via [PermissionProbeConfiguration].
 */
@RestController
class PermissionProbeController {
    @GetMapping("/api/v1/identity/_test/admin-only")
    @PreAuthorize("hasAuthority('identity:administer')")
    fun adminOnly(): Map<String, Boolean> = mapOf("ok" to true)
}

@TestConfiguration
class PermissionProbeConfiguration {
    @Bean
    fun permissionProbeController(): PermissionProbeController = PermissionProbeController()
}

/**
 * This class exercises auth/session/permission behavior across several test
 * methods that reuse the same seeded accounts, which would otherwise collide
 * with the real per-email `LoginAttemptThrottle`'s window (see
 * [NoThrottleConfiguration]). The throttle's own behavior is proven
 * separately, end to end over real HTTP, in [LoginRateLimitHttpTest].
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(PermissionProbeConfiguration::class, NoThrottleConfiguration::class)
class IdentityHttpTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    @Autowired
    private lateinit var dsl: DSLContext

    private val adminEmail = "admin@example.com"
    private val adminPassword = "a-very-long-admin-password-123"
    private val plainEmail = "no-permissions@example.com"
    private val plainPassword = "a-very-long-plain-password-123"

    /**
     * Idempotent rather than `@BeforeAll`: the Spring context (and its
     * database) is cached and reused across test methods in this class, but
     * `@TestInstance(PER_CLASS)` conflicts with Testcontainers' container
     * startup ordering for `@DynamicPropertySource`, so seeding runs before
     * every method and simply skips users that already exist.
     */
    @BeforeEach
    fun seedUsersIfNeeded() {
        if (userRepository.findByEmail(EmailAddress.of(adminEmail)) == null) {
            val admin =
                User.bootstrap(
                    email = EmailAddress.of(adminEmail),
                    passwordHash = passwordHasher.hash(adminPassword),
                    role = RoleCode.of("platform-admin"),
                    now = Instant.now(),
                )
            userRepository.save(admin)
        }

        if (userRepository.findByEmail(EmailAddress.of(plainEmail)) == null) {
            val plainUser =
                User(
                    id = UserId.generate(),
                    email = EmailAddress.of(plainEmail),
                    passwordHash = passwordHasher.hash(plainPassword),
                    status = UserStatus.ACTIVE,
                    roles = emptySet(),
                    createdAt = Instant.now(),
                    failedLoginCount = 0,
                    lockedUntil = null,
                )
            userRepository.save(plainUser)
        }
    }

    private fun login(
        email: String,
        password: String,
    ) = mockMvc
        .post("/api/v1/identity/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"$password"}"""
        }

    private fun tokenFrom(responseBody: String): String {
        val match = Regex(""""token"\s*:\s*"([^"]+)"""").find(responseBody)
        return requireNotNull(match) { "No token field in response body: $responseBody" }.groupValues[1]
    }

    @Test
    fun `wrong password is rejected without revealing account existence`() {
        login(adminEmail, "definitely-wrong").andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("invalid_credentials") }
        }
    }

    @Test
    fun `an over-length email is rejected cleanly instead of failing the audit write with a 500`() {
        val hugeEmail = "a".repeat(500) + "@example.com"
        login(hugeEmail, "whatever").andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("invalid_credentials") }
        }
    }

    @Test
    fun `unauthenticated access to a protected identity route gets a 401 challenge, not a 403`() {
        mockMvc.get("/api/v1/identity/me").andExpect {
            status { isUnauthorized() }
            header { string("WWW-Authenticate", "Bearer") }
        }
    }

    @Test
    fun `full session lifecycle - login, authenticated access, permission grant, logout, revocation`() {
        val loginBody =
            login(adminEmail, adminPassword)
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        val token = tokenFrom(loginBody)

        mockMvc
            .get("/api/v1/identity/me") { header("Authorization", "Bearer $token") }
            .andExpect {
                status { isOk() }
                jsonPath("$.email") { value(adminEmail) }
                jsonPath("$.roles[0]") { value("platform-admin") }
                jsonPath("$.permissions[0]") { value("identity:administer") }
            }

        mockMvc
            .get("/api/v1/identity/_test/admin-only") { header("Authorization", "Bearer $token") }
            .andExpect { status { isOk() } }

        mockMvc
            .post("/api/v1/identity/logout") { header("Authorization", "Bearer $token") }
            .andExpect { status { isNoContent() } }

        // Post-logout: the token is revoked, so this is now "not
        // authenticated" (401), the same as a request that never carried a
        // token at all — not "authenticated but forbidden" (403).
        mockMvc
            .get("/api/v1/identity/me") { header("Authorization", "Bearer $token") }
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `an authenticated user without the required permission is denied and the denial is audited`() {
        val loginBody =
            login(plainEmail, plainPassword)
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        val token = tokenFrom(loginBody)
        val plainUserId = userRepository.findByEmail(EmailAddress.of(plainEmail))!!.id.value

        mockMvc
            .get("/api/v1/identity/me") { header("Authorization", "Bearer $token") }
            .andExpect {
                status { isOk() }
                jsonPath("$.permissions") { isEmpty() }
            }

        mockMvc
            .get("/api/v1/identity/_test/admin-only") { header("Authorization", "Bearer $token") }
            .andExpect { status { isForbidden() } }

        val auditedDenial =
            dsl
                .selectFrom(IDENTITY_AUDIT_EVENT)
                .where(IDENTITY_AUDIT_EVENT.EVENT_TYPE.eq("PERMISSION_DENIED"))
                .and(IDENTITY_AUDIT_EVENT.ACTOR_USER_ID.eq(plainUserId))
                .fetchOne()
        requireNotNull(auditedDenial) { "Expected a PERMISSION_DENIED audit row for the denied request" }
        // The actual required permission, not just which URL was hit — an
        // operator reviewing the audit log needs to know *what* was denied.
        org.assertj.core.api.Assertions
            .assertThat(auditedDenial.detail)
            .contains("identity:administer")
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
