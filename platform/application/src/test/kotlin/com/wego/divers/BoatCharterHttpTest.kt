package com.wego.divers

import com.wego.identity.application.PasswordHasher
import com.wego.identity.application.UserRepository
import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.User
import com.wego.identity.domain.UserId
import com.wego.identity.domain.UserStatus
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
 * Proves the boat-charter registry and its real payoff — the offering
 * capacity guardrail — end to end over real HTTP and real PostgreSQL: a
 * charter can be created/edited/ended, and an offering can only be linked
 * to a charter when its capacity fits the boat's real licensed capacity.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class BoatCharterHttpTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    private val staffEmail = "charter-staff@example.com"
    private val staffPassword = "a-very-long-staff-password-123"

    @BeforeEach
    fun seedUsersIfNeeded() {
        seedUserIfNeeded(staffEmail, staffPassword, roles = setOf("platform-admin"))
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

    private fun createOffering(
        token: String,
        title: String,
        capacity: Int,
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
                      "startsOn": "2026-09-01",
                      "capacity": $capacity,
                      "pricingBasis": "PER_PARTICIPANT",
                      "unitPrice": {"amount": "45.00", "currencyCode": "EUR"}
                    }
                    """.trimIndent()
            }.andExpect { status { isCreated() } }
            .andReturn()
            .response.contentAsString
            .let { jsonField(it, "id") }

    private fun createCharter(
        token: String,
        boatName: String,
        licensedCapacity: Int,
    ): String =
        mockMvc
            .post("/api/v1/divers/boat-charters") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"boatName": "$boatName", "charterType": "STANDING", "licensedCapacity": $licensedCapacity, "startsOn": "2026-01-01"}"""
            }.andExpect { status { isCreated() } }
            .andReturn()
            .response.contentAsString
            .let { jsonField(it, "id") }

    @Test
    fun `full lifecycle - create, list, get, update, end`() {
        val token = login(staffEmail, staffPassword)

        val createBody =
            mockMvc
                .post("/api/v1/divers/boat-charters") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"boatName": "Barbarossa HTTP", "charterType": "STANDING", "licensedCapacity": 50, "startsOn": "2026-01-01"}"""
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.boatName") { value("Barbarossa HTTP") }
                    jsonPath("$.licensedCapacity") { value(50) }
                    jsonPath("$.status") { value("ACTIVE") }
                }.andReturn()
                .response.contentAsString
        val charterId = jsonField(createBody, "id")

        mockMvc
            .get("/api/v1/divers/boat-charters/$charterId") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.boatName") { value("Barbarossa HTTP") }
            }

        mockMvc
            .get("/api/v1/divers/boat-charters") {
                header("Authorization", "Bearer $token")
                param("search", "Barbarossa HTTP")
            }.andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value(charterId) }
            }

        mockMvc
            .put("/api/v1/divers/boat-charters/$charterId") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"boatName": "Barbarossa HTTP (renamed)", "licensedCapacity": 48, "startsOn": "2026-01-01"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.boatName") { value("Barbarossa HTTP (renamed)") }
                jsonPath("$.licensedCapacity") { value(48) }
            }

        mockMvc
            .post("/api/v1/divers/boat-charters/$charterId/end") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("ENDED") }
            }

        mockMvc
            .post("/api/v1/divers/boat-charters/$charterId/end") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("already_ended") }
            }
    }

    @Test
    fun `links an offering whose capacity fits the charter's licensed capacity`() {
        val token = login(staffEmail, staffPassword)
        val charterId = createCharter(token, "Al-Horeya HTTP Fits", 40)
        val offeringId = createOffering(token, "HTTP Boat Trip Fits", capacity = 40)

        mockMvc
            .put("/api/v1/divers/offerings/$offeringId/boat-charter") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"boatCharterId": "$charterId"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.boatCharterId") { value(charterId) }
            }

        mockMvc
            .get("/api/v1/divers/offerings/$offeringId/boat-charter") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.boatCharterId") { value(charterId) }
            }

        mockMvc
            .delete("/api/v1/divers/offerings/$offeringId/boat-charter") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isNoContent() } }

        mockMvc
            .get("/api/v1/divers/offerings/$offeringId/boat-charter") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `rejects linking an offering whose capacity exceeds the charter's licensed capacity`() {
        val token = login(staffEmail, staffPassword)
        val charterId = createCharter(token, "Al-Horeya HTTP Exceeds", 40)
        val offeringId = createOffering(token, "HTTP Boat Trip Exceeds", capacity = 45)

        mockMvc
            .put("/api/v1/divers/offerings/$offeringId/boat-charter") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"boatCharterId": "$charterId"}"""
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("offering_capacity_exceeds_charter") }
            }
    }

    @Test
    fun `rejects linking to an ended charter`() {
        val token = login(staffEmail, staffPassword)
        val charterId = createCharter(token, "Al-Horeya HTTP Ended", 40)
        mockMvc.post("/api/v1/divers/boat-charters/$charterId/end") { header("Authorization", "Bearer $token") }
        val offeringId = createOffering(token, "HTTP Boat Trip Ended Charter", capacity = 30)

        mockMvc
            .put("/api/v1/divers/offerings/$offeringId/boat-charter") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"boatCharterId": "$charterId"}"""
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("charter_not_active") }
            }
    }

    @Test
    fun `rejects reducing a charter's capacity below a currently linked offering's capacity`() {
        val token = login(staffEmail, staffPassword)
        val charterId = createCharter(token, "Al-Horeya HTTP Reduce", 40)
        val offeringId = createOffering(token, "HTTP Boat Trip Reduce Guard", capacity = 40)
        mockMvc
            .put("/api/v1/divers/offerings/$offeringId/boat-charter") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"boatCharterId": "$charterId"}"""
            }.andExpect { status { isOk() } }

        mockMvc
            .put("/api/v1/divers/boat-charters/$charterId") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"boatName": "Al-Horeya HTTP Reduce", "licensedCapacity": 30, "startsOn": "2026-01-01"}"""
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("capacity_below_linked_offerings") }
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
