package com.wego.toursoperator

import com.wego.generated.jooq.tables.IdentityRole.IDENTITY_ROLE
import com.wego.generated.jooq.tables.IdentityRolePermission.IDENTITY_ROLE_PERMISSION
import com.wego.generated.jooq.tables.ToursOperatorTour.TOURS_OPERATOR_TOUR
import com.wego.generated.jooq.tables.ToursOperatorTourSlot.TOURS_OPERATOR_TOUR_SLOT
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
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * End-to-end HTTP integration tests for the tours-operator slice.
 * Runs over real PostgreSQL via Testcontainers.
 *
 * Test 6 (concurrent booking) is the proof that findByIdForUpdate
 * serializes concurrent requests: exactly one of three concurrent threads
 * succeeds (201), the other two are rejected (409 slot_fully_booked).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class ToursOperatorHttpTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    @Autowired
    private lateinit var dsl: DSLContext

    private val adminEmail = "sts-admin@example.com"
    private val adminPassword = "a-very-long-admin-password-sts-123"
    private val plainEmail = "sts-noperm@example.com"
    private val plainPassword = "a-very-long-plain-password-sts-123"

    @BeforeEach
    fun seedUsers() {
        seedUserIfNeeded(adminEmail, adminPassword, roles = setOf("platform-admin"))
        seedUserIfNeeded(plainEmail, plainPassword, roles = emptySet())
    }

    // ── helpers ───────────────────────────────────────────────────────────────

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

    private fun seedLimitedUser(
        email: String,
        password: String,
        permission: String,
    ): UUID {
        val roleCode = "sts-test-${permission.replace(':', '-').replace('.', '-')}"
        if (dsl.fetchOne(IDENTITY_ROLE, IDENTITY_ROLE.CODE.eq(roleCode)) == null) {
            dsl
                .insertInto(IDENTITY_ROLE)
                .set(IDENTITY_ROLE.CODE, roleCode)
                .set(IDENTITY_ROLE.DESCRIPTION, "Test-only role for $permission")
                .execute()
            dsl
                .insertInto(IDENTITY_ROLE_PERMISSION)
                .set(IDENTITY_ROLE_PERMISSION.ROLE_CODE, roleCode)
                .set(IDENTITY_ROLE_PERMISSION.PERMISSION_CODE, permission)
                .execute()
        }
        return seedUserIfNeeded(email, password, roles = setOf(roleCode))
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
        return requireNotNull(match) { "No token in login response: $body" }.groupValues[1]
    }

    private fun jsonField(
        body: String,
        field: String,
    ): String {
        val match = Regex(""""$field"\s*:\s*"([^"]+)"""").find(body)
        return requireNotNull(match) { "No field '$field' in: $body" }.groupValues[1]
    }

    /**
     * Inserts a tour + slot directly in DB — avoids dependency on a
     * yet-to-be-built Tours CRUD API (out of scope for this packet).
     */
    private fun seedTourAndSlot(
        slugSuffix: String,
        capacity: Int,
        date: LocalDate = LocalDate.of(2027, 6, 15),
        timeSlot: String = "MORNING",
    ): Pair<UUID, UUID> {
        val tourId = UUID.randomUUID()
        val slotId = UUID.randomUUID()
        val adminUser = userRepository.findByEmail(EmailAddress.of(adminEmail))!!
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        dsl
            .insertInto(TOURS_OPERATOR_TOUR)
            .set(TOURS_OPERATOR_TOUR.ID, tourId)
            .set(TOURS_OPERATOR_TOUR.SLUG, "sts-test-$slugSuffix")
            .set(TOURS_OPERATOR_TOUR.CATEGORY, "DESERT")
            .set(TOURS_OPERATOR_TOUR.DURATION_TEXT, "4 hours")
            .set(TOURS_OPERATOR_TOUR.PRICE_ADULT_CENTS, 3500L)
            .set(TOURS_OPERATOR_TOUR.PRICE_CHILD_CENTS, 1750L)
            .set(TOURS_OPERATOR_TOUR.CAPACITY, capacity)
            .set(TOURS_OPERATOR_TOUR.AVAILABLE_TIME_SLOTS, timeSlot)
            .set(TOURS_OPERATOR_TOUR.SORT_ORDER, 1)
            .set(TOURS_OPERATOR_TOUR.IS_ACTIVE, true)
            .set(TOURS_OPERATOR_TOUR.CREATED_BY_USER_ID, adminUser.id.value)
            .set(TOURS_OPERATOR_TOUR.CREATED_AT, now)
            .execute()

        dsl
            .insertInto(TOURS_OPERATOR_TOUR_SLOT)
            .set(TOURS_OPERATOR_TOUR_SLOT.ID, slotId)
            .set(TOURS_OPERATOR_TOUR_SLOT.TOUR_ID, tourId)
            .set(TOURS_OPERATOR_TOUR_SLOT.DATE, date)
            .set(TOURS_OPERATOR_TOUR_SLOT.TIME_SLOT, timeSlot)
            .set(TOURS_OPERATOR_TOUR_SLOT.CAPACITY, capacity)
            .set(TOURS_OPERATOR_TOUR_SLOT.BOOKED_COUNT, 0)
            .set(TOURS_OPERATOR_TOUR_SLOT.IS_BLOCKED, false)
            .set(TOURS_OPERATOR_TOUR_SLOT.CREATED_AT, now)
            .execute()

        return Pair(tourId, slotId)
    }

    private fun bookingRequestBody(slotId: UUID): String =
        """
        {
          "slotId": "$slotId",
          "adultsCount": 2,
          "childrenCount": 1,
          "customer": {
            "fullName": "Ahmed Hassan",
            "phone": "+201234567890",
            "nationality": "EG",
            "email": "ahmed@example.com"
          },
          "hotelName": "Hilton Sharm Dreams",
          "hotelRoom": "312",
          "locale": "en"
        }
        """.trimIndent()

    // ── test 1: unauthenticated staff endpoints ───────────────────────────────

    @Test
    fun `tours list is public but bookings list requires auth`() {
        mockMvc
            .get("/api/v1/tours-operator/tours")
            .andExpect { status { isOk() } }

        mockMvc
            .get("/api/v1/tours-operator/bookings")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `public tour lookup by slug returns contract money`() {
        val (tourId, _) = seedTourAndSlot("slug-lookup", capacity = 10)

        mockMvc
            .get("/api/v1/tours-operator/tours/by-slug") {
                param("slug", "sts-test-slug-lookup")
            }.andExpect {
                status { isOk() }
                jsonPath("$.id") { value(tourId.toString()) }
                jsonPath("$.priceAdult.amount") { value("35.00") }
                jsonPath("$.priceAdult.currencyCode") { value("EUR") }
                jsonPath("$.priceAdultCents") { doesNotExist() }
            }
    }

    @Test
    fun `public booking validates nested customer contract before domain execution`() {
        val (_, slotId) = seedTourAndSlot("validation", capacity = 10)
        val invalidBody =
            bookingRequestBody(slotId)
                .replace("\"nationality\": \"EG\"", "\"nationality\": \"egypt\"")

        mockMvc
            .post("/api/v1/tours-operator/bookings") {
                contentType = MediaType.APPLICATION_JSON
                content = invalidBody
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("validation_failed") }
                jsonPath("$.message") { value(org.hamcrest.Matchers.containsString("customer.nationality")) }
            }
    }

    // ── test 2: public booking creation ──────────────────────────────────────

    @Test
    fun `public POST bookings creates a NEW booking with STR-YYYY-N reference`() {
        val (_, slotId) = seedTourAndSlot("book-201", capacity = 10)

        val body =
            mockMvc
                .post("/api/v1/tours-operator/bookings") {
                    contentType = MediaType.APPLICATION_JSON
                    content = bookingRequestBody(slotId)
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.status") { value("NEW") }
                    jsonPath("$.priceAdult.amount") { value("35.00") }
                    jsonPath("$.priceAdult.currencyCode") { value("EUR") }
                    jsonPath("$.priceChild.amount") { value("17.50") }
                    jsonPath("$.totalPrice.amount") { value("87.50") }
                    jsonPath("$.totalPrice.currencyCode") { value("EUR") }
                    jsonPath("$.customer.fullName") { value("Ahmed Hassan") }
                    jsonPath("$.customer.phone") { value("+201234567890") }
                    jsonPath("$.totalEur") { doesNotExist() }
                    jsonPath("$.customerPhone") { doesNotExist() }
                }.andReturn()
                .response.contentAsString

        val reference = jsonField(body, "reference")
        assert(reference.matches(Regex("STR-\\d{4}-\\d+"))) {
            "Reference '$reference' does not match STR-YYYY-N format"
        }
    }

    // ── test 3: confirm (payment-update permission) ───────────────────────────

    @Test
    fun `POST confirm transitions booking to CONFIRMED`() {
        val (_, slotId) = seedTourAndSlot("confirm-3", capacity = 10)
        val adminToken = login(adminEmail, adminPassword)

        val bookingId =
            jsonField(
                mockMvc
                    .post("/api/v1/tours-operator/bookings") {
                        contentType = MediaType.APPLICATION_JSON
                        content = bookingRequestBody(slotId)
                    }.andExpect { status { isCreated() } }
                    .andReturn()
                    .response.contentAsString,
                "id",
            )

        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/confirm") {
                header("Authorization", "Bearer $adminToken")
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("CONFIRMED") }
            }
    }

    // ── test 4: cancel (cancel permission) ───────────────────────────────────

    @Test
    fun `POST cancel transitions booking to CANCELLED`() {
        val (_, slotId) = seedTourAndSlot("cancel-4", capacity = 10)
        val adminToken = login(adminEmail, adminPassword)

        val bookingId =
            jsonField(
                mockMvc
                    .post("/api/v1/tours-operator/bookings") {
                        contentType = MediaType.APPLICATION_JSON
                        content = bookingRequestBody(slotId)
                    }.andExpect { status { isCreated() } }
                    .andReturn()
                    .response.contentAsString,
                "id",
            )

        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/cancel") {
                header("Authorization", "Bearer $adminToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason":"Test cancellation"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("CANCELLED") }
                jsonPath("$.cancellationReason") { value("Test cancellation") }
            }
    }

    // ── test 5: complete (complete permission) ────────────────────────────────

    @Test
    fun `POST complete transitions CONFIRMED booking to COMPLETED`() {
        val (_, slotId) = seedTourAndSlot("complete-5", capacity = 10)
        val adminToken = login(adminEmail, adminPassword)

        val bookingId =
            jsonField(
                mockMvc
                    .post("/api/v1/tours-operator/bookings") {
                        contentType = MediaType.APPLICATION_JSON
                        content = bookingRequestBody(slotId)
                    }.andExpect { status { isCreated() } }
                    .andReturn()
                    .response.contentAsString,
                "id",
            )

        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/confirm") {
                header("Authorization", "Bearer $adminToken")
            }.andExpect { status { isOk() } }

        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/complete") {
                header("Authorization", "Bearer $adminToken")
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("COMPLETED") }
            }
    }

    // ── test 6: concurrent booking — the FOR UPDATE proof ────────────────────

    @Test
    fun `concurrent booking on capacity-1 slot -- exactly one succeeds and two get 409 slot fully booked`() {
        val (_, slotId) = seedTourAndSlot("concurrent-6", capacity = 1)
        val request = bookingRequestBody(slotId)
        val executor = Executors.newFixedThreadPool(3)

        val futures: List<Future<Int>> =
            (1..3).map {
                executor.submit(
                    Callable {
                        mockMvc
                            .post("/api/v1/tours-operator/bookings") {
                                contentType = MediaType.APPLICATION_JSON
                                content = request
                            }.andReturn()
                            .response.status
                    },
                )
            }

        executor.shutdown()
        val statuses = futures.map { it.get() }
        val created = statuses.count { it == 201 }
        val conflict = statuses.count { it == 409 }

        assert(created == 1) {
            "Expected exactly 1 success (201), got $created. All statuses: $statuses"
        }
        assert(conflict == 2) {
            "Expected exactly 2 conflicts (409), got $conflict. All statuses: $statuses"
        }
    }

    // ── test 7: confirm on CANCELLED → 409 ───────────────────────────────────

    @Test
    fun `confirm on CANCELLED booking returns 409 cannot confirm`() {
        val (_, slotId) = seedTourAndSlot("confirm-cancelled-7", capacity = 10)
        val adminToken = login(adminEmail, adminPassword)

        val bookingId =
            jsonField(
                mockMvc
                    .post("/api/v1/tours-operator/bookings") {
                        contentType = MediaType.APPLICATION_JSON
                        content = bookingRequestBody(slotId)
                    }.andExpect { status { isCreated() } }
                    .andReturn()
                    .response.contentAsString,
                "id",
            )

        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/cancel") {
                header("Authorization", "Bearer $adminToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason":"Operator cancelled"}"""
            }.andExpect { status { isOk() } }

        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/confirm") {
                header("Authorization", "Bearer $adminToken")
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("cannot_confirm") }
            }
    }

    // ── test 8: cancel on COMPLETED → 409 ────────────────────────────────────

    @Test
    fun `cancel on COMPLETED booking returns 409 cannot cancel`() {
        val (_, slotId) = seedTourAndSlot("cancel-completed-8", capacity = 10)
        val adminToken = login(adminEmail, adminPassword)

        val bookingId =
            jsonField(
                mockMvc
                    .post("/api/v1/tours-operator/bookings") {
                        contentType = MediaType.APPLICATION_JSON
                        content = bookingRequestBody(slotId)
                    }.andExpect { status { isCreated() } }
                    .andReturn()
                    .response.contentAsString,
                "id",
            )

        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/confirm") {
                header("Authorization", "Bearer $adminToken")
            }.andExpect { status { isOk() } }

        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/complete") {
                header("Authorization", "Bearer $adminToken")
            }.andExpect { status { isOk() } }

        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/cancel") {
                header("Authorization", "Bearer $adminToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason":"Attempted cancel after complete"}"""
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("cannot_cancel") }
            }
    }

    // ── test 9: GET bookings without booking:view → 403 ──────────────────────

    @Test
    fun `GET bookings returns 403 for user without booking view permission`() {
        seedLimitedUser(
            "sts-no-view@example.com",
            "a-very-long-password-sts-no-view",
            "tours-operator.booking:cancel",
        )
        val limitedToken = login("sts-no-view@example.com", "a-very-long-password-sts-no-view")

        mockMvc
            .get("/api/v1/tours-operator/bookings") {
                header("Authorization", "Bearer $limitedToken")
            }.andExpect { status { isForbidden() } }
    }

    // ── test 10: public lookup by reference + phone ───────────────────────────

    @Test
    fun `public lookup by reference and phone returns 200`() {
        val (_, slotId) = seedTourAndSlot("lookup-10", capacity = 10)

        val body =
            mockMvc
                .post("/api/v1/tours-operator/bookings") {
                    contentType = MediaType.APPLICATION_JSON
                    content = bookingRequestBody(slotId)
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString

        val reference = jsonField(body, "reference")

        mockMvc
            .get("/api/v1/tours-operator/bookings/lookup") {
                param("reference", reference)
                param("phone", "+201234567890")
            }.andExpect {
                status { isOk() }
                jsonPath("$.reference") { value(reference) }
                jsonPath("$.customer.phone") { value("+201234567890") }
            }
    }

    // ── Testcontainers setup ──────────────────────────────────────────────────

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("wego_test_sts")
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
