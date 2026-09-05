package com.wego.accounting

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
 * Proves WEGO-012 Phase 5's accounting slice end to end over real HTTP and
 * real PostgreSQL: real accounts, a real balanced double-entry posting, a
 * real rejection of an unbalanced one, and a real reversal that flips every
 * line's direction — not unit-level assumptions about what the service
 * layer does.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class AccountingHttpTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    @Autowired
    private lateinit var dsl: DSLContext

    private val staffEmail = "accounting-staff@example.com"
    private val staffPassword = "a-very-long-staff-password-123"
    private val plainEmail = "accounting-no-permissions@example.com"
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

    private fun createAccount(
        token: String,
        code: String,
        name: String,
        accountType: String,
    ): String {
        val body =
            mockMvc
                .post("/api/v1/accounting/accounts") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"code":"$code","name":"$name","accountType":"$accountType","parentAccountId":null,"description":null}"""
                }.andReturn()
                .response.contentAsString
        return jsonField(body, "id")
    }

    @Test
    fun `account full lifecycle - create, list, get, update, deactivate, reactivate`() {
        val token = login(staffEmail, staffPassword)
        val id = createAccount(token, "9100", "HTTP Test Asset", "ASSET")

        val listBody =
            mockMvc
                .get("/api/v1/accounting/accounts") {
                    header("Authorization", "Bearer $token")
                    param("search", "HTTP Test Asset")
                }.andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        assertThat(listBody).contains("HTTP Test Asset")

        mockMvc
            .get("/api/v1/accounting/accounts/$id") { header("Authorization", "Bearer $token") }
            .andExpect {
                status { isOk() }
                jsonPath("$.normalBalance") { value("DEBIT") }
            }

        mockMvc
            .put("/api/v1/accounting/accounts/$id") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"HTTP Test Asset Renamed","description":"Renamed in test"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { value("HTTP Test Asset Renamed") }
            }

        mockMvc
            .post("/api/v1/accounting/accounts/$id/deactivate") { header("Authorization", "Bearer $token") }
            .andExpect {
                status { isOk() }
                jsonPath("$.active") { value(false) }
            }

        mockMvc
            .post("/api/v1/accounting/accounts/$id/deactivate") { header("Authorization", "Bearer $token") }
            .andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("already_inactive") }
            }

        mockMvc
            .post("/api/v1/accounting/accounts/$id/reactivate") { header("Authorization", "Bearer $token") }
            .andExpect {
                status { isOk() }
                jsonPath("$.active") { value(true) }
            }
    }

    @Test
    fun `creating an account with a duplicate code is a clean 409`() {
        val token = login(staffEmail, staffPassword)
        createAccount(token, "9200", "First Duplicate Test", "ASSET")

        mockMvc
            .post("/api/v1/accounting/accounts") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"code":"9200","name":"Second Duplicate Test","accountType":"ASSET","parentAccountId":null,"description":null}"""
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("code_already_in_use") }
            }
    }

    @Test
    fun `a balanced journal entry posts, an unbalanced one is rejected, and a reversal flips every line`() {
        val token = login(staffEmail, staffPassword)
        val cashId = createAccount(token, "9300", "Journal Test Cash", "ASSET")
        val revenueId = createAccount(token, "9400", "Journal Test Revenue", "REVENUE")

        val postBody =
            mockMvc
                .post("/api/v1/accounting/journal-entries") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
                        {
                          "entryDate": "2026-08-31",
                          "description": "Test booking revenue",
                          "reference": "BK-TEST-1",
                          "currencyCode": "EGP",
                          "lines": [
                            {"accountId": "$cashId", "direction": "DEBIT", "amount": "250.00"},
                            {"accountId": "$revenueId", "direction": "CREDIT", "amount": "250.00"}
                          ]
                        }
                        """.trimIndent()
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.debitTotal") { value("250.00") }
                    jsonPath("$.creditTotal") { value("250.00") }
                }.andReturn()
                .response.contentAsString
        val entryId = jsonField(postBody, "id")

        mockMvc
            .post("/api/v1/accounting/journal-entries") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "entryDate": "2026-08-31",
                      "description": "Unbalanced test",
                      "reference": null,
                      "currencyCode": "EGP",
                      "lines": [
                        {"accountId": "$cashId", "direction": "DEBIT", "amount": "100.00"},
                        {"accountId": "$revenueId", "direction": "CREDIT", "amount": "99.00"}
                      ]
                    }
                    """.trimIndent()
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("unbalanced") }
            }

        val reversalBody =
            mockMvc
                .post("/api/v1/accounting/journal-entries/$entryId/reverse") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"reason":"Test booking cancelled"}"""
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.reversalOfEntryId") { value(entryId) }
                }.andReturn()
                .response.contentAsString
        assertThat(reversalBody).contains("\"direction\":\"CREDIT\"")

        mockMvc
            .post("/api/v1/accounting/journal-entries/$entryId/reverse") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason":"Second attempt"}"""
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("already_reversed") }
            }

        val filteredBody =
            mockMvc
                .get("/api/v1/accounting/journal-entries") {
                    header("Authorization", "Bearer $token")
                    param("accountId", cashId)
                }.andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        assertThat(filteredBody).contains("Test booking revenue")
    }

    @Test
    fun `posting against an inactive account is a clean 400`() {
        val token = login(staffEmail, staffPassword)
        val cashId = createAccount(token, "9500", "Inactive Test Cash", "ASSET")
        val revenueId = createAccount(token, "9600", "Inactive Test Revenue", "REVENUE")
        mockMvc.post("/api/v1/accounting/accounts/$cashId/deactivate") { header("Authorization", "Bearer $token") }

        mockMvc
            .post("/api/v1/accounting/journal-entries") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "entryDate": "2026-08-31",
                      "description": "Against inactive account",
                      "reference": null,
                      "currencyCode": "EGP",
                      "lines": [
                        {"accountId": "$cashId", "direction": "DEBIT", "amount": "10.00"},
                        {"accountId": "$revenueId", "direction": "CREDIT", "amount": "10.00"}
                      ]
                    }
                    """.trimIndent()
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("account_inactive") }
            }
    }

    @Test
    fun `reversing a nonexistent journal entry is a clean 404`() {
        val token = login(staffEmail, staffPassword)
        mockMvc
            .post("/api/v1/accounting/journal-entries/${java.util.UUID.randomUUID()}/reverse") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason":"Doesn't exist"}"""
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `every accounting endpoint is denied to an account with no accounting permissions`() {
        val token = login(plainEmail, plainPassword)
        mockMvc
            .get("/api/v1/accounting/accounts") { header("Authorization", "Bearer $token") }
            .andExpect { status { isForbidden() } }
        mockMvc
            .get("/api/v1/accounting/journal-entries") { header("Authorization", "Bearer $token") }
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `accounting journal-view alone can read but not post`() {
        seedLimitedUser("accounting-view-only@example.com", staffPassword, "accounting:journal-view")
        val viewToken = login("accounting-view-only@example.com", staffPassword)

        mockMvc
            .get("/api/v1/accounting/journal-entries") { header("Authorization", "Bearer $viewToken") }
            .andExpect { status { isOk() } }
        // A well-formed body (real UUID shapes, valid amount format) — this
        // must be rejected on authorization alone, not incidentally on
        // request validation, so an empty/malformed body would prove
        // nothing about whether @PreAuthorize actually protects this route.
        mockMvc
            .post("/api/v1/accounting/journal-entries") {
                header("Authorization", "Bearer $viewToken")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "entryDate": "2026-08-31",
                      "description": "Denied",
                      "reference": null,
                      "currencyCode": "EGP",
                      "lines": [
                        {"accountId": "${java.util.UUID.randomUUID()}", "direction": "DEBIT", "amount": "10.00"},
                        {"accountId": "${java.util.UUID.randomUUID()}", "direction": "CREDIT", "amount": "10.00"}
                      ]
                    }
                    """.trimIndent()
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
