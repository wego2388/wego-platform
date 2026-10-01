package com.wego.travelmarketplace

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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Proves Phase 1B end to end over real HTTP: the public create-request and
 * reference-lookup endpoints, staff roster/detail/action endpoints with
 * separate permissions, and — the privacy requirement
 * `delivery/01_REQUEST_AND_BOOKING.md` calls out by name — that the public
 * lookup response never contains the customer's name, phone or email, while
 * the staff response does.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class TravelRequestHttpTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    private val staffEmail = "travel-request-staff@example.com"
    private val staffPassword = "a-very-long-staff-password-123"
    private val noPermissionEmail = "travel-request-view-only@example.com"
    private val noPermissionPassword = "a-very-long-view-password-123"

    @BeforeEach
    fun seedUsersIfNeeded() {
        seedUserIfNeeded(staffEmail, staffPassword, roles = setOf("platform-admin"))
        seedUserIfNeeded(noPermissionEmail, noPermissionPassword, roles = emptySet())
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
        return requireNotNull(Regex(""""token"\s*:\s*"([^"]+)"""").find(body)) { "No token in: $body" }.groupValues[1]
    }

    private fun jsonField(
        body: String,
        field: String,
    ): String = requireNotNull(Regex(""""$field"\s*:\s*"([^"]+)"""").find(body)) { "No $field field in: $body" }.groupValues[1]

    /** Publishes one real service (DRAFT -> REVIEW -> APPROVED -> PUBLISHED) through the real HTTP API, returns (serviceId, optionId). */
    private fun publishedService(
        token: String,
        confirmationType: String = "INSTANT",
    ): Pair<String, String> {
        val categoryId =
            jsonField(
                mockMvc
                    .post("/api/v1/travel-marketplace/categories") {
                        header("Authorization", "Bearer $token")
                        contentType = MediaType.APPLICATION_JSON
                        content =
                            """{"code": "cat-${UUID.randomUUID().toString().take(
                                8,
                            )}", "name": {"en": "Transfers", "ar": "الانتقالات"}, "description": null, "displayOrder": 0}"""
                    }.andReturn()
                    .response.contentAsString,
                "id",
            )
        val createBody =
            mockMvc
                .post("/api/v1/travel-marketplace/services") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
                        {
                          "categoryId": "$categoryId",
                          "name": {"en": "Airport Transfer", "ar": "انتقال من المطار"},
                          "description": {"en": "A private transfer.", "ar": "انتقال خاص."},
                          "fulfilmentModel": "DIRECT",
                          "providerId": null,
                          "confirmationType": "$confirmationType",
                          "cancellationPolicy": {"en": "Free up to 24h before.", "ar": "إلغاء مجاني حتى 24 ساعة قبل الموعد."},
                          "pickupInfo": null,
                          "inclusions": null,
                          "exclusions": null,
                          "options": [{"id": null, "label": {"en": "Arrival", "ar": "استلام"}, "durationMinutes": 60, "maxParticipants": 3, "priceAmount": 700.00, "priceCurrency": "EGP", "priceBasis": "PER_VEHICLE"}],
                          "media": [{"id": null, "assetReference": "asset-001", "rightsEvidence": "Owner-supplied, rights confirmed 2026-08-01", "locale": "en"}]
                        }
                        """.trimIndent()
                }.andReturn()
                .response.contentAsString
        val serviceId = jsonField(createBody, "id")
        val optionId =
            requireNotNull(Regex(""""options"\s*:\s*\[\s*\{\s*"id"\s*:\s*"([^"]+)"""").find(createBody)) {
                "No option id in: $createBody"
            }.groupValues[1]

        mockMvc.post("/api/v1/travel-marketplace/services/$serviceId/submit-for-review") { header("Authorization", "Bearer $token") }
        mockMvc.post("/api/v1/travel-marketplace/services/$serviceId/approve") { header("Authorization", "Bearer $token") }
        mockMvc.post("/api/v1/travel-marketplace/services/$serviceId/publish") { header("Authorization", "Bearer $token") }

        return serviceId to optionId
    }

    /**
     * The public create/lookup responses never carry the internal `id`
     * (only `reference` — see [TravelRequestPublicResponse]). Staff actions
     * are keyed by `id`, so tests that create a request publicly and then
     * act on it as staff resolve the id through the staff roster, matching
     * on the `reference` both shapes do carry.
     */
    private fun findRequestIdByReference(
        token: String,
        reference: String,
    ): String {
        val rosterBody =
            mockMvc
                .get("/api/v1/travel-marketplace/requests") { header("Authorization", "Bearer $token") }
                .andReturn()
                .response.contentAsString
        return requireNotNull(
            Regex(""""id"\s*:\s*"([^"]+)"\s*,\s*"reference"\s*:\s*"$reference"""").find(rosterBody),
        ) { "No request with reference $reference in roster: $rosterBody" }.groupValues[1]
    }

    private fun createRequestJson(
        serviceId: String,
        optionId: String,
        adults: Int = 2,
        expectedPriceAmount: String = "700.00", // matches publishedService()'s own hardcoded option price
    ) = """
        {
          "serviceId": "$serviceId",
          "serviceOptionId": "$optionId",
          "requestedDate": "${LocalDate.now().plusDays(10)}",
          "requestedTime": null,
          "adults": $adults,
          "children": 0,
          "hotelOrPickup": "Four Seasons Sharm",
          "locale": "en",
          "notes": null,
          "sourceChannel": "WEBSITE",
          "customer": {"name": "Nour", "phone": "+201001413469", "email": null},
          "expectedPriceAmount": $expectedPriceAmount,
          "expectedPriceCurrency": "EGP"
        }
        """.trimIndent()

    @Test
    fun `public create-request auto-confirms an INSTANT service and the reference lookup returns it, with no customer PII`() {
        val token = login(staffEmail, staffPassword)
        val (serviceId, optionId) = publishedService(token, confirmationType = "INSTANT")
        val idempotencyKey = UUID.randomUUID().toString()

        val createBody =
            mockMvc
                .post("/api/v1/travel-marketplace/public/requests") {
                    header("Idempotency-Key", idempotencyKey)
                    contentType = MediaType.APPLICATION_JSON
                    content = createRequestJson(serviceId, optionId)
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.status") { value("CONFIRMED") }
                    jsonPath("$.reference") { exists() }
                }.andReturn()
                .response.contentAsString
        val reference = jsonField(createBody, "reference")

        org.assertj.core.api.Assertions
            .assertThat(createBody)
            .doesNotContain("Nour")
            .doesNotContain("+201001413469")

        mockMvc
            .get("/api/v1/travel-marketplace/public/requests/$reference")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("CONFIRMED") }
            }.andReturn()
            .response.contentAsString
            .let { lookupBody ->
                org.assertj.core.api.Assertions
                    .assertThat(lookupBody)
                    .doesNotContain("Nour")
                    .doesNotContain("+201001413469")
            }
    }

    @Test
    fun `resubmitting the same idempotency key over HTTP returns 200 with the original request, not 201`() {
        val token = login(staffEmail, staffPassword)
        val (serviceId, optionId) = publishedService(token)
        val key = UUID.randomUUID().toString()

        val firstBody =
            mockMvc
                .post("/api/v1/travel-marketplace/public/requests") {
                    header("Idempotency-Key", key)
                    contentType = MediaType.APPLICATION_JSON
                    content = createRequestJson(serviceId, optionId)
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        val firstReference = jsonField(firstBody, "reference")

        mockMvc
            .post("/api/v1/travel-marketplace/public/requests") {
                header("Idempotency-Key", key)
                contentType = MediaType.APPLICATION_JSON
                content = createRequestJson(serviceId, optionId)
            }.andExpect {
                status { isOk() }
                jsonPath("$.reference") { value(firstReference) }
            }
    }

    @Test
    fun `party size over the option's maxParticipants is rejected with 409`() {
        val token = login(staffEmail, staffPassword)
        val (serviceId, optionId) = publishedService(token)

        mockMvc
            .post("/api/v1/travel-marketplace/public/requests") {
                header("Idempotency-Key", UUID.randomUUID().toString())
                contentType = MediaType.APPLICATION_JSON
                content = createRequestJson(serviceId, optionId, adults = 5)
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("party_size_exceeds_capacity") }
            }
    }

    @Test
    fun `a stale expected price is rejected with 409 and the real current price, not silently confirmed`() {
        val token = login(staffEmail, staffPassword)
        val (serviceId, optionId) = publishedService(token, confirmationType = "INSTANT")

        mockMvc
            .post("/api/v1/travel-marketplace/public/requests") {
                header("Idempotency-Key", UUID.randomUUID().toString())
                contentType = MediaType.APPLICATION_JSON
                content = createRequestJson(serviceId, optionId, expectedPriceAmount = "650.00") // real price is 700.00
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("price_changed") }
                jsonPath("$.currentPriceAmount") { value("700.00") }
                jsonPath("$.currentPriceCurrency") { value("EGP") }
            }
    }

    @Test
    fun `missing Idempotency-Key header is a clean 400, not a 500`() {
        val token = login(staffEmail, staffPassword)
        val (serviceId, optionId) = publishedService(token)

        mockMvc
            .post("/api/v1/travel-marketplace/public/requests") {
                contentType = MediaType.APPLICATION_JSON
                content = createRequestJson(serviceId, optionId)
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `an unauthenticated lookup for an unknown reference is 404, not a stack trace`() {
        mockMvc.get("/api/v1/travel-marketplace/public/requests/STG-ZZZZZZZZ").andExpect { status { isNotFound() } }
    }

    @Test
    fun `staff full lifecycle over HTTP - review through completion, with separate permission checks`() {
        val staffToken = login(staffEmail, staffPassword)
        val noPermissionToken = login(noPermissionEmail, noPermissionPassword)
        val (serviceId, optionId) = publishedService(staffToken, confirmationType = "STAFF_REVIEW")

        val createBody =
            mockMvc
                .post("/api/v1/travel-marketplace/public/requests") {
                    header("Idempotency-Key", UUID.randomUUID().toString())
                    contentType = MediaType.APPLICATION_JSON
                    content = createRequestJson(serviceId, optionId)
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        val requestId = findRequestIdByReference(staffToken, jsonField(createBody, "reference"))

        // A user with no permissions at all cannot even view the roster.
        mockMvc
            .get("/api/v1/travel-marketplace/requests") { header("Authorization", "Bearer $noPermissionToken") }
            .andExpect { status { isForbidden() } }

        mockMvc
            .post("/api/v1/travel-marketplace/requests/$requestId/start-review") { header("Authorization", "Bearer $staffToken") }
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("IN_REVIEW") }
                jsonPath("$.customer.name") { value("Nour") }
            }

        mockMvc
            .post("/api/v1/travel-marketplace/requests/$requestId/confirm") { header("Authorization", "Bearer $staffToken") }
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("CONFIRMED") }
            }

        mockMvc
            .post("/api/v1/travel-marketplace/requests/$requestId/complete") { header("Authorization", "Bearer $staffToken") }
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("COMPLETED") }
            }

        // Every transition left a trace, newest first: COMPLETED, CONFIRMED, IN_REVIEW, NEW.
        mockMvc
            .get("/api/v1/travel-marketplace/requests/$requestId/audit") { header("Authorization", "Bearer $staffToken") }
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(4) }
                jsonPath("$[0].toStatus") { value("COMPLETED") }
                jsonPath("$[0].actorType") { value("STAFF") }
                jsonPath("$[3].toStatus") { value("NEW") }
                jsonPath("$[3].actorType") { value("CUSTOMER") }
            }

        mockMvc
            .get("/api/v1/travel-marketplace/requests/$requestId/audit") { header("Authorization", "Bearer $noPermissionToken") }
            .andExpect { status { isForbidden() } }
    }

    @Test
    fun `staff cancel requires a typed reason and rejects an already-terminal request`() {
        val token = login(staffEmail, staffPassword)
        val (serviceId, optionId) = publishedService(token)

        val createBody =
            mockMvc
                .post("/api/v1/travel-marketplace/public/requests") {
                    header("Idempotency-Key", UUID.randomUUID().toString())
                    contentType = MediaType.APPLICATION_JSON
                    content = createRequestJson(serviceId, optionId)
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        val requestId = findRequestIdByReference(token, jsonField(createBody, "reference"))

        mockMvc
            .post("/api/v1/travel-marketplace/requests/$requestId/cancel") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason": "CUSTOMER_REQUESTED", "detail": "Change of plans"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("CANCELLED") }
                jsonPath("$.cancelReason") { value("CUSTOMER_REQUESTED") }
            }

        mockMvc
            .post("/api/v1/travel-marketplace/requests/$requestId/cancel") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason": "OTHER", "detail": null}"""
            }.andExpect { status { isConflict() } }
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("wego_sharm_to_go_test")
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
