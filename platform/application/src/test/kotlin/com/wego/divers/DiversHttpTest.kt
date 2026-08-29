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
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.util.UUID

/**
 * Proves the whole WEGO-002 slice end to end over real HTTP and real
 * PostgreSQL: staff creates an offering, creates a booking against it,
 * cancels it, marks it paid, refunds it — plus permission separation is
 * proven with genuinely *limited*-role users (not just `platform-admin`,
 * which holds every permission and so cannot prove anything was actually
 * denied), capacity/idempotency conflicts are enforced at the HTTP layer,
 * and malformed input never reaches a raw 500.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class DiversHttpTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    @Autowired
    private lateinit var dsl: DSLContext

    private val staffEmail = "divers-staff@example.com"
    private val staffPassword = "a-very-long-staff-password-123"
    private val plainEmail = "divers-no-permissions@example.com"
    private val plainPassword = "a-very-long-plain-password-123"

    @BeforeEach
    fun seedUsersIfNeeded() {
        seedUserIfNeeded(staffEmail, staffPassword, roles = setOf("platform-admin"))
        seedUserIfNeeded(plainEmail, plainPassword, roles = emptySet())
    }

    /** A user holding exactly one divers permission, via an ad hoc single-permission role — never `platform-admin`. */
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

    private fun createOffering(
        token: String,
        title: String,
        capacity: Int? = null,
        pricingBasis: String = "PER_PARTICIPANT",
        startsOn: String = "2026-09-01",
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
                      ${capacity?.let { """"capacity": $it,""" } ?: ""}
                      "pricingBasis": "$pricingBasis",
                      "unitPrice": {"amount": "45.00", "currencyCode": "EUR"}
                    }
                    """.trimIndent()
            }.andExpect { status { isCreated() } }
            .andReturn()
            .response.contentAsString
            .let { jsonField(it, "id") }

    private fun createBooking(
        token: String,
        offeringId: String,
        idempotencyKey: String,
        partySize: Int = 2,
        customerName: String = "Ada Lovelace",
    ): String =
        mockMvc
            .post("/api/v1/divers/bookings") {
                header("Authorization", "Bearer $token")
                header("Idempotency-Key", idempotencyKey)
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "offeringId": "$offeringId",
                      "partySize": $partySize,
                      "customerName": "$customerName",
                      "customerEmail": "ada@example.com"
                    }
                    """.trimIndent()
            }.andExpect { status { isCreated() } }
            .andReturn()
            .response.contentAsString
            .let { jsonField(it, "id") }

    @Test
    fun `full lifecycle - create offering, create booking, cancel, mark paid, refund`() {
        val token = login(staffEmail, staffPassword)

        val offeringId = createOffering(token, "HTTP Lifecycle Trip", capacity = 5)

        val bookingBody =
            mockMvc
                .post("/api/v1/divers/bookings") {
                    header("Authorization", "Bearer $token")
                    header("Idempotency-Key", "http-lifecycle-key-1")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
                        {
                          "offeringId": "$offeringId",
                          "partySize": 2,
                          "customerName": "Ada Lovelace",
                          "customerEmail": "ada@example.com"
                        }
                        """.trimIndent()
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.status") { value("CONFIRMED") }
                    jsonPath("$.pricingBasis") { value("PER_PARTICIPANT") }
                    jsonPath("$.unitPrice.amount") { value("45.00") }
                    jsonPath("$.billableQuantity") { value(2) }
                    jsonPath("$.totalPrice.amount") { value("90.00") }
                }.andReturn()
                .response.contentAsString
        val bookingId = jsonField(bookingBody, "id")

        // A retry with the same Idempotency-Key must return the same
        // booking unchanged (200, not a second 201).
        mockMvc
            .post("/api/v1/divers/bookings") {
                header("Authorization", "Bearer $token")
                header("Idempotency-Key", "http-lifecycle-key-1")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "offeringId": "$offeringId",
                      "partySize": 2,
                      "customerName": "Ada Lovelace",
                      "customerEmail": "ada@example.com"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.id") { value(bookingId) }
            }

        mockMvc
            .patch("/api/v1/divers/bookings/$bookingId/mark-paid") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.paymentStatus") { value("PAID") }
            }

        mockMvc
            .post("/api/v1/divers/bookings/$bookingId/cancel") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason": "Customer requested cancellation"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("CANCELLED") }
            }

        // Cancellation and payment are independent — a cancelled-but-paid
        // booking can still be refunded.
        mockMvc
            .patch("/api/v1/divers/bookings/$bookingId/refund") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason": "Refund after cancellation"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.paymentStatus") { value("REFUNDED") }
            }
    }

    @Test
    fun `a user without divers permissions is denied creating an offering`() {
        val token = login(plainEmail, plainPassword)

        mockMvc
            .post("/api/v1/divers/offerings") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "offeringType": "DIVE_TRIP",
                      "title": "Denied Trip",
                      "startsOn": "2026-09-01",
                      "pricingBasis": "PER_PARTICIPANT",
                      "unitPrice": {"amount": "45.00", "currencyCode": "EUR"}
                    }
                    """.trimIndent()
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `booking-create alone cannot mark paid or refund — proven with a genuinely limited role`() {
        val staffToken = login(staffEmail, staffPassword)
        val offeringId = createOffering(staffToken, "Permission Split Trip")
        val bookingId = createBooking(staffToken, offeringId, "http-permission-split-key-1")

        seedLimitedUser("divers-booking-create-only@example.com", "a-very-long-password-123", "booking:create")
        val limitedToken = login("divers-booking-create-only@example.com", "a-very-long-password-123")

        mockMvc
            .patch("/api/v1/divers/bookings/$bookingId/mark-paid") {
                header("Authorization", "Bearer $limitedToken")
            }.andExpect { status { isForbidden() } }

        mockMvc
            .patch("/api/v1/divers/bookings/$bookingId/refund") {
                header("Authorization", "Bearer $limitedToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason": "Attempted refund"}"""
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `booking-payment-update can mark paid but is denied refund`() {
        val staffToken = login(staffEmail, staffPassword)
        val offeringId = createOffering(staffToken, "Payment Update Only Trip")
        val bookingId = createBooking(staffToken, offeringId, "http-payment-update-only-key-1")

        seedLimitedUser("divers-payment-update-only@example.com", "a-very-long-password-123", "booking:payment-update")
        val limitedToken = login("divers-payment-update-only@example.com", "a-very-long-password-123")

        mockMvc
            .patch("/api/v1/divers/bookings/$bookingId/mark-paid") {
                header("Authorization", "Bearer $limitedToken")
            }.andExpect {
                status { isOk() }
                jsonPath("$.paymentStatus") { value("PAID") }
            }

        mockMvc
            .patch("/api/v1/divers/bookings/$bookingId/refund") {
                header("Authorization", "Bearer $limitedToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason": "Attempted refund"}"""
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `booking-refund can refund a paid booking but is denied marking paid`() {
        val staffToken = login(staffEmail, staffPassword)
        val offeringId = createOffering(staffToken, "Refund Only Trip")
        val bookingId = createBooking(staffToken, offeringId, "http-refund-only-key-1")
        mockMvc.patch("/api/v1/divers/bookings/$bookingId/mark-paid") { header("Authorization", "Bearer $staffToken") }

        seedLimitedUser("divers-refund-only@example.com", "a-very-long-password-123", "booking:refund")
        val limitedToken = login("divers-refund-only@example.com", "a-very-long-password-123")

        mockMvc
            .patch("/api/v1/divers/bookings/$bookingId/mark-paid") {
                header("Authorization", "Bearer $limitedToken")
            }.andExpect { status { isForbidden() } }

        mockMvc
            .patch("/api/v1/divers/bookings/$bookingId/refund") {
                header("Authorization", "Bearer $limitedToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason": "Customer requested a refund"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.paymentStatus") { value("REFUNDED") }
            }
    }

    @Test
    fun `refunding an unpaid booking is rejected as a conflict, not applied`() {
        val token = login(staffEmail, staffPassword)
        val offeringId = createOffering(token, "Unpaid Refund Attempt Trip")
        val bookingId = createBooking(token, offeringId, "http-unpaid-refund-key-1")

        mockMvc
            .patch("/api/v1/divers/bookings/$bookingId/refund") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason": "Attempted refund of an unpaid booking"}"""
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("invalid_payment_transition") }
            }
    }

    @Test
    fun `cancelling requires a non-blank reason`() {
        val token = login(staffEmail, staffPassword)
        val offeringId = createOffering(token, "Cancel Reason Trip")
        val bookingId = createBooking(token, offeringId, "http-cancel-reason-key-1")

        mockMvc
            .post("/api/v1/divers/bookings/$bookingId/cancel") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason": ""}"""
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `capacity is enforced over real HTTP, not just at the service level`() {
        val token = login(staffEmail, staffPassword)
        val offeringId = createOffering(token, "HTTP Capacity Trip", capacity = 1, startsOn = "2026-09-02")

        mockMvc
            .post("/api/v1/divers/bookings") {
                header("Authorization", "Bearer $token")
                header("Idempotency-Key", "http-capacity-key-1")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "offeringId": "$offeringId",
                      "partySize": 1,
                      "customerName": "First Customer",
                      "customerEmail": "first@example.com"
                    }
                    """.trimIndent()
            }.andExpect { status { isCreated() } }

        mockMvc
            .post("/api/v1/divers/bookings") {
                header("Authorization", "Bearer $token")
                header("Idempotency-Key", "http-capacity-key-2")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "offeringId": "$offeringId",
                      "partySize": 1,
                      "customerName": "Second Customer",
                      "customerEmail": "second@example.com"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("capacity_exceeded") }
            }
    }

    @Test
    fun `reusing an Idempotency-Key against a different offering is a 409, not a silent replay`() {
        val token = login(staffEmail, staffPassword)
        val firstOfferingId = createOffering(token, "First Trip", startsOn = "2026-09-03")
        val secondOfferingId = createOffering(token, "Second Trip", startsOn = "2026-09-03")

        mockMvc
            .post("/api/v1/divers/bookings") {
                header("Authorization", "Bearer $token")
                header("Idempotency-Key", "http-conflict-key-1")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "offeringId": "$firstOfferingId",
                      "partySize": 1,
                      "customerName": "First Customer",
                      "customerEmail": "first@example.com"
                    }
                    """.trimIndent()
            }.andExpect { status { isCreated() } }

        mockMvc
            .post("/api/v1/divers/bookings") {
                header("Authorization", "Bearer $token")
                header("Idempotency-Key", "http-conflict-key-1")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "offeringId": "$secondOfferingId",
                      "partySize": 1,
                      "customerName": "First Customer",
                      "customerEmail": "first@example.com"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("idempotency_key_conflict") }
            }
    }

    @Test
    fun `a request that fails a domain validation rule is a clean 400, not an unhandled 500`() {
        val token = login(staffEmail, staffPassword)
        val offeringId = createOffering(token, "Validation Trip", startsOn = "2026-09-04")

        mockMvc
            .post("/api/v1/divers/bookings") {
                header("Authorization", "Bearer $token")
                header("Idempotency-Key", "http-validation-key-1")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "offeringId": "$offeringId",
                      "partySize": 0,
                      "customerName": "Invalid Party Size",
                      "customerEmail": "invalid-party-size@example.com"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("validation_failed") }
            }
    }

    @Test
    fun `framework binding failures use the divers validation error contract`() {
        val token = login(staffEmail, staffPassword)

        mockMvc
            .post("/api/v1/divers/bookings") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "offeringId": "${UUID.randomUUID()}",
                      "partySize": 1,
                      "customerName": "Missing Header Customer",
                      "customerEmail": "customer@example.com"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("validation_failed") }
            }

        mockMvc
            .get("/api/v1/divers/bookings?page=not-an-integer") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("validation_failed") }
            }
    }

    @Test
    fun `an unrecognized JSON property is rejected with a clean 400`() {
        val token = login(staffEmail, staffPassword)

        mockMvc
            .post("/api/v1/divers/offerings") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "offeringType": "DIVE_TRIP",
                      "title": "Unknown Property Trip",
                      "startsOn": "2026-09-05",
                      "pricingBasis": "PER_PARTICIPANT",
                      "unitPrice": {"amount": "45.00", "currencyCode": "EUR"},
                      "notARealField": "should be rejected"
                    }
                    """.trimIndent()
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a valid incoming correlation id is echoed back and an invalid one is replaced, never a 500`() {
        val token = login(staffEmail, staffPassword)
        val correlationId = UUID.randomUUID().toString()

        mockMvc
            .get("/api/v1/divers/offerings") {
                header("Authorization", "Bearer $token")
                header("X-Correlation-Id", correlationId)
            }.andExpect {
                status { isOk() }
                header { string("X-Correlation-Id", correlationId) }
            }

        mockMvc
            .get("/api/v1/divers/offerings") {
                header("Authorization", "Bearer $token")
                header("X-Correlation-Id", "not-a-real-uuid")
            }.andExpect {
                status { isOk() }
                header { exists("X-Correlation-Id") }
            }
    }

    @Test
    fun `closing an offering blocks further bookings, and closing it twice is a conflict`() {
        val token = login(staffEmail, staffPassword)
        val offeringId = createOffering(token, "Close Lifecycle Trip", startsOn = "2026-09-06")

        mockMvc
            .post("/api/v1/divers/offerings/$offeringId/close") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason": "Fully booked externally"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("CLOSED") }
            }

        mockMvc
            .post("/api/v1/divers/bookings") {
                header("Authorization", "Bearer $token")
                header("Idempotency-Key", "http-close-key-1")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "offeringId": "$offeringId",
                      "partySize": 1,
                      "customerName": "Too Late Customer",
                      "customerEmail": "toolate@example.com"
                    }
                    """.trimIndent()
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("offering_closed") }
            }

        mockMvc
            .post("/api/v1/divers/offerings/$offeringId/close") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason": "Second close attempt"}"""
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("already_closed") }
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
