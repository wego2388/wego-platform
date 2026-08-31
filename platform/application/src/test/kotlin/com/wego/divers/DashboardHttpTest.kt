package com.wego.divers

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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Proves the dashboard's 4 KPI endpoints over real HTTP and real
 * PostgreSQL, replacing WEGO-012 Phase 2's previously bare landing page
 * with genuine business numbers: real bookings today, real paid revenue
 * this month, a real upcoming trip, real active-diver and equipment
 * counts — and that each endpoint is denied to an account holding none of
 * the underlying module's permissions, the same as every other read in
 * this product.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class DashboardHttpTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    private val staffEmail = "dashboard-staff@example.com"
    private val staffPassword = "a-very-long-staff-password-123"
    private val plainEmail = "dashboard-no-permissions@example.com"
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
    ): UUID {
        val existing = userRepository.findByEmail(EmailAddress.of(email))
        if (existing != null) return existing.id.value
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

    private fun createOffering(
        token: String,
        title: String,
        startsOn: String,
    ): String =
        mockMvc
            .post("/api/v1/divers/offerings") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "offeringType": "DIVE_TRIP",
                      "title": "$title",
                      "startsOn": "$startsOn",
                      "pricingBasis": "PER_PARTICIPANT",
                      "unitPrice": {"amount": "45.00", "currencyCode": "EUR"}
                    }
                    """.trimIndent()
            }.andExpect { status { isCreated() } }
            .andReturn()
            .response.contentAsString
            .let { jsonField(it, "id") }

    private fun createPaidBooking(
        token: String,
        offeringId: String,
        idempotencyKey: String,
    ) {
        val bookingBody =
            mockMvc
                .post("/api/v1/divers/bookings") {
                    header("Authorization", "Bearer $token")
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
                        {"offeringId": "$offeringId", "partySize": 2, "customerName": "Dashboard Test Customer", "customerEmail": "dash@example.com"}
                        """.trimIndent()
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        val bookingId = jsonField(bookingBody, "id")
        mockMvc
            .patch("/api/v1/divers/bookings/$bookingId/mark-paid") { header("Authorization", "Bearer $token") }
            .andExpect { status { isOk() } }
    }

    @Test
    fun `bookings dashboard shows real today's count and real paid revenue this month`() {
        val token = login(staffEmail, staffPassword)
        val offeringId = createOffering(token, "Dashboard Revenue Trip", "2026-09-01")
        createPaidBooking(token, offeringId, "dashboard-revenue-key-1")

        val body =
            mockMvc
                .get("/api/v1/divers/dashboard/bookings") { header("Authorization", "Bearer $token") }
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString

        // Real amount: PER_PARTICIPANT * 2 = 90.00 EUR — proves this is the actual booking
        // total, not a placeholder or a count mislabeled as money.
        assertThat(body).contains("\"bookingsToday\"")
        assertThat(body).contains("90.00")
        assertThat(body).contains("EUR")
    }

    @Test
    fun `offerings dashboard lists a real offering starting within the next 7 days, not one starting later`() {
        val token = login(staffEmail, staffPassword)
        val soon = LocalDate.now().plusDays(3).toString()
        val later = LocalDate.now().plusDays(30).toString()
        createOffering(token, "Dashboard Upcoming Trip", soon)
        createOffering(token, "Dashboard Far-Future Trip", later)

        val body =
            mockMvc
                .get("/api/v1/divers/dashboard/offerings") { header("Authorization", "Bearer $token") }
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString

        assertThat(body).contains("Dashboard Upcoming Trip")
        assertThat(body).doesNotContain("Dashboard Far-Future Trip")
    }

    @Test
    fun `divers dashboard shows a real active-diver count`() {
        val token = login(staffEmail, staffPassword)
        mockMvc
            .post("/api/v1/divers/divers") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "fullName": "Dashboard Test Diver",
                      "nationality": null,
                      "primaryLanguage": null,
                      "email": "dashboard-diver@example.com",
                      "phone": null,
                      "emergencyContactName": null,
                      "emergencyContactPhone": null,
                      "medicalNotes": null,
                      "totalLoggedDives": 0,
                      "maxDepthMeters": null,
                      "lastDiveOn": null,
                      "bcdSize": null,
                      "finSize": null,
                      "wetsuitSize": null,
                      "certifications": []
                    }
                    """.trimIndent()
            }.andExpect { status { isCreated() } }

        val body =
            mockMvc
                .get("/api/v1/divers/dashboard/divers") { header("Authorization", "Bearer $token") }
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        val count = Regex(""""activeDivers"\s*:\s*(\d+)""").find(body)!!.groupValues[1].toInt()
        assertThat(count).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `equipment dashboard shows a real status breakdown including an item genuinely in maintenance`() {
        val token = login(staffEmail, staffPassword)
        val createBody =
            mockMvc
                .post("/api/v1/divers/equipment") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"equipmentType": "BCD", "label": "Dashboard Test BCD", "qrCode": "dashboard-qr-1", "itemSize": "M"}"""
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        val equipmentId = jsonField(createBody, "id")
        mockMvc
            .post("/api/v1/divers/equipment/$equipmentId/start-maintenance") { header("Authorization", "Bearer $token") }
            .andExpect { status { isOk() } }

        val body =
            mockMvc
                .get("/api/v1/divers/dashboard/equipment") { header("Authorization", "Bearer $token") }
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        val inMaintenance = Regex(""""inMaintenance"\s*:\s*(\d+)""").find(body)!!.groupValues[1].toInt()
        assertThat(inMaintenance).isGreaterThanOrEqualTo(1)
    }

    @Test
    fun `every dashboard endpoint is denied to an account with no permissions`() {
        val plainToken = login(plainEmail, plainPassword)
        for (path in listOf("bookings", "offerings", "divers", "equipment")) {
            mockMvc
                .get("/api/v1/divers/dashboard/$path") { header("Authorization", "Bearer $plainToken") }
                .andExpect { status { isForbidden() } }
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
