package com.wego.divers

import com.wego.generated.jooq.tables.DiversBookingAuditEvent.DIVERS_BOOKING_AUDIT_EVENT
import com.wego.generated.jooq.tables.IntegrationOutbox.INTEGRATION_OUTBOX
import com.wego.identity.application.PasswordHasher
import com.wego.identity.application.UserRepository
import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.User
import com.wego.identity.domain.UserId
import com.wego.identity.domain.UserStatus
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.util.UUID

/**
 * Proves requirement F end to end: one incoming `X-Correlation-Id` on a
 * single booking-creation request is the *same* id on the HTTP response
 * header, the `divers_booking_audit_event` row it wrote, and the
 * `integration_outbox` row it wrote — not three independently generated
 * ids that merely resemble each other.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class CorrelationPropagationHttpTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    @Autowired
    private lateinit var dsl: DSLContext

    private val email = "correlation-propagation-staff@example.com"
    private val password = "a-very-long-staff-password-123"

    private fun seedStaffUser() {
        if (userRepository.findByEmail(EmailAddress.of(email)) != null) return
        userRepository.save(
            User(
                id = UserId.generate(),
                email = EmailAddress.of(email),
                passwordHash = passwordHasher.hash(password),
                status = UserStatus.ACTIVE,
                roles = setOf(RoleCode.of("platform-admin")),
                createdAt = Instant.now(),
                failedLoginCount = 0,
                lockedUntil = null,
            ),
        )
    }

    private fun login(): String {
        val body =
            mockMvc
                .post("/api/v1/identity/login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"$email","password":"$password"}"""
                }.andReturn()
                .response.contentAsString
        return requireNotNull(Regex(""""token"\s*:\s*"([^"]+)"""").find(body)) { body }.groupValues[1]
    }

    private fun jsonField(
        body: String,
        field: String,
    ): String = requireNotNull(Regex(""""$field"\s*:\s*"([^"]+)"""").find(body)) { body }.groupValues[1]

    @Test
    fun `a booking creation's response, audit event, and outbox event all share the caller's correlation id`() {
        seedStaffUser()
        val token = login()
        val correlationId = UUID.randomUUID()

        val offeringBody =
            mockMvc
                .post("/api/v1/divers/offerings") {
                    header("Authorization", "Bearer $token")
                    header("X-Correlation-Id", correlationId.toString())
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
                        {
                          "offeringType": "DIVE_TRIP",
                          "title": "Correlation Propagation Trip",
                          "startsOn": "2026-09-20",
                          "pricingBasis": "PER_PARTICIPANT",
                          "unitPrice": {"amount": "45.00", "currencyCode": "EUR"}
                        }
                        """.trimIndent()
                }.andExpect {
                    status { isCreated() }
                    header { string("X-Correlation-Id", correlationId.toString()) }
                }.andReturn()
                .response.contentAsString
        val offeringId = jsonField(offeringBody, "id")

        val bookingBody =
            mockMvc
                .post("/api/v1/divers/bookings") {
                    header("Authorization", "Bearer $token")
                    header("X-Correlation-Id", correlationId.toString())
                    header("Idempotency-Key", "correlation-propagation-key-1")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
                        {
                          "offeringId": "$offeringId",
                          "partySize": 1,
                          "customerName": "Correlation Customer",
                          "customerEmail": "correlation-customer@example.com"
                        }
                        """.trimIndent()
                }.andExpect {
                    status { isCreated() }
                    header { string("X-Correlation-Id", correlationId.toString()) }
                }.andReturn()
                .response.contentAsString
        val bookingId = UUID.fromString(jsonField(bookingBody, "id"))

        val auditCorrelationId =
            dsl
                .select(DIVERS_BOOKING_AUDIT_EVENT.CORRELATION_ID)
                .from(DIVERS_BOOKING_AUDIT_EVENT)
                .where(DIVERS_BOOKING_AUDIT_EVENT.BOOKING_ID.eq(bookingId))
                .and(DIVERS_BOOKING_AUDIT_EVENT.EVENT_TYPE.eq("BOOKING_CREATED"))
                .fetchOne(DIVERS_BOOKING_AUDIT_EVENT.CORRELATION_ID)
        assertThat(auditCorrelationId).isEqualTo(correlationId)

        val outboxCorrelationId =
            dsl
                .select(INTEGRATION_OUTBOX.CORRELATION_ID)
                .from(INTEGRATION_OUTBOX)
                .where(INTEGRATION_OUTBOX.AGGREGATE_ID.eq(bookingId.toString()))
                .and(INTEGRATION_OUTBOX.EVENT_TYPE.eq("booking.created"))
                .fetchOne(INTEGRATION_OUTBOX.CORRELATION_ID)
        assertThat(outboxCorrelationId).isEqualTo(correlationId)
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
