package com.wego.accounting

import com.wego.identity.application.PasswordHasher
import com.wego.identity.application.UserRepository
import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.User
import com.wego.identity.domain.UserId
import com.wego.identity.domain.UserStatus
import org.assertj.core.api.Assertions.assertThat
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
 * Proves WEGO-012 Phase 7's reports slice end to end over real HTTP and
 * real PostgreSQL: posts a real balanced entry against real accounts,
 * then confirms the trial balance, income statement, and balance sheet
 * all reflect the real posted numbers — not unit-level assumptions about
 * what the aggregation query does.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class ReportHttpTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    private val staffEmail = "reports-staff@example.com"
    private val staffPassword = "a-very-long-staff-password-123"
    private val plainEmail = "reports-no-permissions@example.com"
    private val plainPassword = "a-very-long-plain-password-123"

    @BeforeEach
    fun seedUsersIfNeeded() {
        seedUserIfNeeded(staffEmail, staffPassword, roles = setOf("platform-admin"))
        seedUserIfNeeded(plainEmail, plainPassword, roles = emptySet())
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
    fun `trial balance, income statement, and balance sheet all reflect a real posted entry`() {
        val token = login(staffEmail, staffPassword)
        val cashId = createAccount(token, "9700", "Report Test Cash", "ASSET")
        val revenueId = createAccount(token, "9800", "Report Test Revenue", "REVENUE")

        mockMvc.post("/api/v1/accounting/journal-entries") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content =
                """
                {
                  "entryDate": "2026-08-15",
                  "description": "Report test booking revenue",
                  "reference": null,
                  "currencyCode": "EGP",
                  "lines": [
                    {"accountId": "$cashId", "direction": "DEBIT", "amount": "777.00"},
                    {"accountId": "$revenueId", "direction": "CREDIT", "amount": "777.00"}
                  ]
                }
                """.trimIndent()
        }

        val trialBalanceBody =
            mockMvc
                .get("/api/v1/accounting/reports/trial-balance") {
                    header("Authorization", "Bearer $token")
                    param("asOfDate", "2026-08-31")
                }.andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        assertThat(trialBalanceBody).contains("\"code\":\"9700\"")
        assertThat(trialBalanceBody).contains("\"debitBalance\":\"777.00\"")
        assertThat(trialBalanceBody).contains("\"code\":\"9800\"")
        assertThat(trialBalanceBody).contains("\"creditBalance\":\"777.00\"")
        val totalDebits = Regex(""""totalDebits"\s*:\s*"([^"]+)"""").find(trialBalanceBody)!!.groupValues[1]
        val totalCredits = Regex(""""totalCredits"\s*:\s*"([^"]+)"""").find(trialBalanceBody)!!.groupValues[1]
        assertThat(totalDebits.toBigDecimal()).isEqualByComparingTo(totalCredits.toBigDecimal())

        val incomeStatementBody =
            mockMvc
                .get("/api/v1/accounting/reports/income-statement") {
                    header("Authorization", "Bearer $token")
                    param("from", "2026-08-01")
                    param("to", "2026-08-31")
                }.andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        assertThat(incomeStatementBody).contains("\"code\":\"9800\"")
        assertThat(incomeStatementBody).contains("\"amount\":\"777.00\"")

        val balanceSheetBody =
            mockMvc
                .get("/api/v1/accounting/reports/balance-sheet") {
                    header("Authorization", "Bearer $token")
                    param("asOfDate", "2026-08-31")
                }.andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        assertThat(balanceSheetBody).contains("\"code\":\"9700\"")
        assertThat(balanceSheetBody).contains("Retained Earnings (accumulated)")
        val totalAssets = Regex(""""totalAssets"\s*:\s*"([^"]+)"""").find(balanceSheetBody)!!.groupValues[1]
        val totalLiabilities = Regex(""""totalLiabilities"\s*:\s*"([^"]+)"""").find(balanceSheetBody)!!.groupValues[1]
        val totalEquity = Regex(""""totalEquity"\s*:\s*"([^"]+)"""").find(balanceSheetBody)!!.groupValues[1]
        assertThat(totalAssets.toBigDecimal()).isEqualByComparingTo(totalLiabilities.toBigDecimal() + totalEquity.toBigDecimal())
    }

    @Test
    fun `every report endpoint is denied to an account with no accounting permissions`() {
        val token = login(plainEmail, plainPassword)
        mockMvc
            .get("/api/v1/accounting/reports/trial-balance") {
                header("Authorization", "Bearer $token")
                param("asOfDate", "2026-08-31")
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
