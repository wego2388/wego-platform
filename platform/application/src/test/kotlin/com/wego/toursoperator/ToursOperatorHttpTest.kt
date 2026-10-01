package com.wego.toursoperator

import com.wego.generated.jooq.tables.IdentityRole.IDENTITY_ROLE
import com.wego.generated.jooq.tables.IdentityRolePermission.IDENTITY_ROLE_PERMISSION
import com.wego.generated.jooq.tables.ToursOperatorPayment.TOURS_OPERATOR_PAYMENT
import com.wego.generated.jooq.tables.ToursOperatorTour.TOURS_OPERATOR_TOUR
import com.wego.generated.jooq.tables.ToursOperatorTourSlot.TOURS_OPERATOR_TOUR_SLOT
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

    private fun payAndConfirm(
        bookingId: String,
        amountCents: Long = 8750L,
    ) {
        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect { status { isCreated() } }

        val orderId =
            dsl
                .select(TOURS_OPERATOR_PAYMENT.PAYMOB_ORDER_ID)
                .from(TOURS_OPERATOR_PAYMENT)
                .where(TOURS_OPERATOR_PAYMENT.BOOKING_ID.eq(UUID.fromString(bookingId)))
                .fetchOne(TOURS_OPERATOR_PAYMENT.PAYMOB_ORDER_ID)
                ?: error("Payment order was not persisted")

        mockMvc
            .post("/api/v1/tours-operator/payments/paymob-callback") {
                param("hmac", "valid-hmac")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "obj": {
                        "id": "TXN-${UUID.randomUUID()}",
                        "success": true,
                        "pending": false,
                        "is_refunded": false,
                        "error_occured": false,
                        "has_parent_transaction": false,
                        "is_3d_secure": true,
                        "is_auth": false,
                        "is_capture": false,
                        "is_standalone_payment": true,
                        "is_voided": false,
                        "owner": "100001",
                        "amount_cents": $amountCents,
                        "currency": "EUR",
                        "created_at": "2026-09-29T00:00:00Z",
                        "integration_id": "100001",
                        "order": { "id": "$orderId" },
                        "source_data": { "pan": "1234", "sub_type": "MasterCard", "type": "card" },
                        "data": { "txn_response_code": "APPROVED" }
                      }
                    }
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("confirmed") }
            }
    }

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

    // ── test 3: only a verified provider callback confirms ───────────────────

    @Test
    fun `manual confirm route is absent and provider callback confirms`() {
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
            }.andExpect { status { isNotFound() } }

        payAndConfirm(bookingId)

        mockMvc
            .get("/api/v1/tours-operator/bookings/$bookingId") {
                header("Authorization", "Bearer $adminToken")
            }.andExpect { jsonPath("$.status") { value("CONFIRMED") } }
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

        payAndConfirm(bookingId)

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
    fun `concurrent parties of 3 on a 3-place slot -- exactly one succeeds and two get 409 slot fully booked`() {
        val (_, slotId) = seedTourAndSlot("concurrent-6", capacity = 3)
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

    // ── places are guests; per-unit pricing ─────────────────────────────────

    private fun bookingBody(
        slotId: UUID,
        adults: Int,
        children: Int = 0,
        unit: String = "",
    ): String =
        """
        {
          "slotId": "$slotId", "adultsCount": $adults, "childrenCount": $children,$unit
          "customer": { "fullName": "Group Guest", "phone": "+201234567891", "nationality": "EG" },
          "hotelName": "Hilton Sharm Dreams", "locale": "en"
        }
        """.trimIndent()

    private fun slotBookedCount(slotId: UUID): Int =
        dsl
            .select(TOURS_OPERATOR_TOUR_SLOT.BOOKED_COUNT)
            .from(TOURS_OPERATOR_TOUR_SLOT)
            .where(TOURS_OPERATOR_TOUR_SLOT.ID.eq(slotId))
            .fetchOne(TOURS_OPERATOR_TOUR_SLOT.BOOKED_COUNT)!!

    @Test
    fun `a booking takes one place per guest and cancelling returns them`() {
        val (_, slotId) = seedTourAndSlot("guests-places", capacity = 5)
        val adminToken = login(adminEmail, adminPassword)

        val first =
            jsonField(
                mockMvc
                    .post("/api/v1/tours-operator/bookings") {
                        contentType = MediaType.APPLICATION_JSON
                        content = bookingBody(slotId, adults = 2, children = 1)
                    }.andExpect { status { isCreated() } }
                    .andReturn()
                    .response.contentAsString,
                "id",
            )
        assertThat(slotBookedCount(slotId)).isEqualTo(3)

        // 3 more guests do not fit in the 2 places left.
        mockMvc
            .post("/api/v1/tours-operator/bookings") {
                contentType = MediaType.APPLICATION_JSON
                content = bookingBody(slotId, adults = 3)
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("slot_fully_booked") }
            }
        assertThat(slotBookedCount(slotId)).isEqualTo(3)

        mockMvc
            .post("/api/v1/tours-operator/bookings/$first/cancel") {
                header("Authorization", "Bearer $adminToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason":"Guest changed plans"}"""
            }.andExpect { status { isOk() } }
        assertThat(slotBookedCount(slotId)).isEqualTo(0)

        mockMvc
            .post("/api/v1/tours-operator/bookings") {
                contentType = MediaType.APPLICATION_JSON
                content = bookingBody(slotId, adults = 5)
            }.andExpect { status { isCreated() } }
        assertThat(slotBookedCount(slotId)).isEqualTo(5)
    }

    @Test
    fun `per-unit tours charge per unit and guests must fit in the units`() {
        val (tourId, slotId) = seedTourAndSlot("per-unit", capacity = 10)
        dsl.execute("UPDATE wego.tours_operator_tour SET price_basis = 'PER_UNIT', price_child_cents = NULL WHERE id = ?", tourId)
        dsl.execute(
            """
            INSERT INTO wego.tours_operator_tour_price_option (tour_id, code, label_en, seats_per_unit, price_cents, sort_order)
            VALUES (?, 'buggy', 'Two-seat buggy', 2, 3000, 0)
            """.trimIndent(),
            tourId,
        )

        mockMvc.get("/api/v1/tours-operator/tours/$tourId").andExpect {
            status { isOk() }
            jsonPath("$.priceBasis") { value("PER_UNIT") }
            jsonPath("$.priceOptions[0].code") { value("buggy") }
            jsonPath("$.priceOptions[0].seatsPerUnit") { value(2) }
            jsonPath("$.priceOptions[0].price.amount") { value("30.00") }
        }

        // 3 guests in one two-seat buggy do not fit.
        mockMvc
            .post("/api/v1/tours-operator/bookings") {
                contentType = MediaType.APPLICATION_JSON
                content = bookingBody(slotId, adults = 3, unit = """ "priceOptionCode": "buggy", "unitCount": 1,""")
            }.andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.error") { value("guests_exceed_units") }
            }
        // A unit is required, and it must exist.
        mockMvc
            .post("/api/v1/tours-operator/bookings") {
                contentType = MediaType.APPLICATION_JSON
                content = bookingBody(slotId, adults = 2)
            }.andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.error") { value("price_option_required") }
            }
        mockMvc
            .post("/api/v1/tours-operator/bookings") {
                contentType = MediaType.APPLICATION_JSON
                content = bookingBody(slotId, adults = 2, unit = """ "priceOptionCode": "yacht", "unitCount": 1,""")
            }.andExpect { status { isUnprocessableEntity() } }
        // More units than guests is refused.
        mockMvc
            .post("/api/v1/tours-operator/bookings") {
                contentType = MediaType.APPLICATION_JSON
                content = bookingBody(slotId, adults = 1, unit = """ "priceOptionCode": "buggy", "unitCount": 2,""")
            }.andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.error") { value("units_exceed_guests") }
            }
        assertThat(slotBookedCount(slotId)).isEqualTo(0)

        // 2 adults + 1 child in two buggies: 2 × €30, and both buggies (4 places) are taken.
        mockMvc
            .post("/api/v1/tours-operator/bookings") {
                contentType = MediaType.APPLICATION_JSON
                content = bookingBody(slotId, adults = 2, children = 1, unit = """ "priceOptionCode": "buggy", "unitCount": 2,""")
            }.andExpect {
                status { isCreated() }
                jsonPath("$.totalPrice.amount") { value("60.00") }
                jsonPath("$.priceAdult.amount") { value("0.00") }
                jsonPath("$.unit.optionCode") { value("buggy") }
                jsonPath("$.unit.unitCount") { value(2) }
                jsonPath("$.unit.unitPrice.amount") { value("30.00") }
            }
        assertThat(slotBookedCount(slotId)).isEqualTo(4)

        // A solo rider holds a whole buggy; 6 places left fit 3 more buggies, not 4.
        mockMvc
            .post("/api/v1/tours-operator/bookings") {
                contentType = MediaType.APPLICATION_JSON
                content = bookingBody(slotId, adults = 1, unit = """ "priceOptionCode": "buggy", "unitCount": 1,""")
            }.andExpect { status { isCreated() } }
        assertThat(slotBookedCount(slotId)).isEqualTo(6)
        mockMvc
            .post("/api/v1/tours-operator/bookings") {
                contentType = MediaType.APPLICATION_JSON
                content = bookingBody(slotId, adults = 3, unit = """ "priceOptionCode": "buggy", "unitCount": 3,""")
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("slot_fully_booked") }
            }
    }

    @Test
    fun `per-person tours refuse unit fields and children without a child price`() {
        val (tourId, slotId) = seedTourAndSlot("per-person-guards", capacity = 10)
        mockMvc
            .post("/api/v1/tours-operator/bookings") {
                contentType = MediaType.APPLICATION_JSON
                content = bookingBody(slotId, adults = 2, unit = """ "priceOptionCode": "buggy", "unitCount": 1,""")
            }.andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.error") { value("price_option_not_applicable") }
            }
        dsl.execute("UPDATE wego.tours_operator_tour SET price_child_cents = NULL WHERE id = ?", tourId)
        mockMvc
            .post("/api/v1/tours-operator/bookings") {
                contentType = MediaType.APPLICATION_JSON
                content = bookingBody(slotId, adults = 1, children = 1)
            }.andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.error") { value("child_price_not_available") }
            }
        assertThat(slotBookedCount(slotId)).isEqualTo(0)
    }

    // ── test 7: manual confirmation remains unavailable ──────────────────────

    @Test
    fun `manual confirm route stays absent for CANCELLED booking`() {
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
            }.andExpect { status { isNotFound() } }
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

        payAndConfirm(bookingId)

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
            .post("/api/v1/tours-operator/bookings/lookup") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"reference":"${reference.lowercase()}","phone":"+201234567890"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.reference") { value(reference) }
                jsonPath("$.customer") { doesNotExist() }
                jsonPath("$.id") { doesNotExist() }
                jsonPath("$.hotelRoom") { doesNotExist() }
                jsonPath("$.specialRequests") { doesNotExist() }
            }
    }

    @Test
    fun `public lookup rejects PII in a GET query string`() {
        mockMvc
            .get("/api/v1/tours-operator/bookings/lookup") {
                param("reference", "STR-2026-1")
                param("phone", "+201234567890")
                // GET falls through to the staff `/{id}` route and `lookup` is
                // rejected as a malformed UUID. The important boundary is that
                // the former query-string recovery contract cannot return data.
            }.andExpect { status { isBadRequest() } }
    }

    // ── test 11: staff POST /staff/tours creates a tour ──────────────────────

    @Test
    fun `staff POST tours staff creates a tour and returns 201 with Location`() {
        val adminToken = login(adminEmail, adminPassword)

        val body =
            mockMvc
                .post("/api/v1/tours-operator/staff/tours") {
                    header("Authorization", "Bearer $adminToken")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
                        {
                          "slug": "test-create-tour-11",
                          "category": "DESERT",
                          "durationText": "3–4 hours",
                          "priceAdultCents": 4000,
                          "priceChildCents": null,
                          "capacity": 15,
                          "availableTimeSlots": ["MORNING", "SUNSET"],
                          "sortOrder": 99,
                          "nameEn": "Test Create Tour 11",
                          "tourType": "TOUR",
                          "imageUrl": null,
                          "cancellationPolicy": "STANDARD"
                        }
                        """.trimIndent()
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.slug") { value("test-create-tour-11") }
                    jsonPath("$.priceAdult.amount") { value("40.00") }
                    jsonPath("$.priceAdult.currencyCode") { value("EUR") }
                    jsonPath("$.isActive") { value(false) }
                    jsonPath("$.tourType") { value("TOUR") }
                    jsonPath("$.cancellationPolicy") { value("STANDARD") }
                }.andReturn()
                .response.contentAsString

        val tourId = jsonField(body, "id")
        val adminUserId = userRepository.findByEmail(EmailAddress.of(adminEmail))!!.id.value
        val storedCreator =
            dsl
                .select(TOURS_OPERATOR_TOUR.CREATED_BY_USER_ID)
                .from(TOURS_OPERATOR_TOUR)
                .where(TOURS_OPERATOR_TOUR.ID.eq(UUID.fromString(tourId)))
                .fetchOne(TOURS_OPERATOR_TOUR.CREATED_BY_USER_ID)
        assertThat(storedCreator).isEqualTo(adminUserId)

        // New tours start inactive and must not leak through the public API.
        mockMvc
            .get("/api/v1/tours-operator/tours/$tourId")
            .andExpect { status { isNotFound() } }

        // Staff with view permission can still retrieve the inactive draft.
        mockMvc
            .get("/api/v1/tours-operator/staff/tours/$tourId") {
                header("Authorization", "Bearer $adminToken")
            }.andExpect {
                status { isOk() }
                jsonPath("$.id") { value(tourId) }
            }
    }

    @Test
    fun `staff POST tours staff returns 409 when slug already exists`() {
        val adminToken = login(adminEmail, adminPassword)
        val uniqueSuffix = System.currentTimeMillis()
        val slug = "duplicate-slug-$uniqueSuffix"

        val payload =
            """
            {
              "slug": "$slug",
              "category": "SEA",
              "durationText": "2 hours",
              "priceAdultCents": 2500,
              "capacity": 10,
              "availableTimeSlots": ["MORNING"],
              "sortOrder": 50
            }
            """.trimIndent()

        mockMvc
            .post("/api/v1/tours-operator/staff/tours") {
                header("Authorization", "Bearer $adminToken")
                contentType = MediaType.APPLICATION_JSON
                content = payload
            }.andExpect { status { isCreated() } }

        mockMvc
            .post("/api/v1/tours-operator/staff/tours") {
                header("Authorization", "Bearer $adminToken")
                contentType = MediaType.APPLICATION_JSON
                content = payload
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("slug_already_exists") }
            }
    }

    @Test
    fun `unauthenticated POST tours staff returns 401`() {
        mockMvc
            .post("/api/v1/tours-operator/staff/tours") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "slug": "no-auth-tour",
                      "category": "CULTURAL",
                      "durationText": "1 hour",
                      "priceAdultCents": 1000,
                      "capacity": 5,
                      "availableTimeSlots": ["MORNING"],
                      "sortOrder": 1
                    }
                    """.trimIndent()
            }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `authenticated user without tour manage permission cannot create a tour`() {
        val plainToken = login(plainEmail, plainPassword)

        mockMvc
            .post("/api/v1/tours-operator/staff/tours") {
                header("Authorization", "Bearer $plainToken")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {
                      "slug": "forbidden-tour-create",
                      "category": "CULTURAL",
                      "durationText": "1 hour",
                      "priceAdultCents": 1000,
                      "capacity": 5,
                      "availableTimeSlots": ["MORNING"],
                      "sortOrder": 1
                    }
                    """.trimIndent()
            }.andExpect { status { isForbidden() } }
    }

    // ── test 12: staff PUT /staff/tours/{id} updates a tour ──────────────────

    @Test
    fun `staff PUT tours staff updates and returns 204`() {
        val adminToken = login(adminEmail, adminPassword)
        val (tourId, _) = seedTourAndSlot("update-tour-12", capacity = 10)

        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .put("/api/v1/tours-operator/staff/tours/$tourId")
                    .header("Authorization", "Bearer $adminToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "category": "SEA",
                          "durationText": "8 hours",
                          "priceAdultCents": 5000,
                          "priceChildCents": 2500,
                          "capacity": 20,
                          "availableTimeSlots": ["MORNING"],
                          "sortOrder": 10,
                          "nameEn": "Updated Name",
                          "tourType": "TOUR",
                          "imageUrl": "https://example.com/img.jpg",
                          "cancellationPolicy": "FLEXIBLE"
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .status()
                    .isNoContent,
            )

        mockMvc
            .get("/api/v1/tours-operator/tours/$tourId")
            .andExpect {
                status { isOk() }
                jsonPath("$.priceAdult.amount") { value("50.00") }
                jsonPath("$.cancellationPolicy") { value("FLEXIBLE") }
                jsonPath("$.nameEn") { value("Updated Name") }
            }
    }

    @Test
    fun `staff PUT tours staff returns 404 for unknown id`() {
        val adminToken = login(adminEmail, adminPassword)

        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .put("/api/v1/tours-operator/staff/tours/${UUID.randomUUID()}")
                    .header("Authorization", "Bearer $adminToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "category": "CULTURAL",
                          "durationText": "5 hours",
                          "priceAdultCents": 7000,
                          "capacity": 10,
                          "availableTimeSlots": ["MORNING"],
                          "sortOrder": 1
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .status()
                    .isNotFound,
            )
    }

    // ── test 13: staff PATCH activate / deactivate ────────────────────────────

    @Test
    fun `staff PATCH activate and deactivate a tour`() {
        val adminToken = login(adminEmail, adminPassword)
        val (tourId, _) = seedTourAndSlot("activate-13", capacity = 5)

        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .patch("/api/v1/tours-operator/staff/tours/$tourId/activate")
                    .header("Authorization", "Bearer $adminToken"),
            ).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .status()
                    .isNoContent,
            )

        mockMvc
            .get("/api/v1/tours-operator/tours/$tourId")
            .andExpect { jsonPath("$.isActive") { value(true) } }

        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .patch("/api/v1/tours-operator/staff/tours/$tourId/deactivate")
                    .header("Authorization", "Bearer $adminToken"),
            ).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .status()
                    .isNoContent,
            )

        mockMvc
            .get("/api/v1/tours-operator/tours/$tourId")
            .andExpect { status { isNotFound() } }

        mockMvc
            .get("/api/v1/tours-operator/staff/tours/$tourId") {
                header("Authorization", "Bearer $adminToken")
            }.andExpect {
                status { isOk() }
                jsonPath("$.isActive") { value(false) }
            }
    }

    @Test
    fun `PATCH activate unknown tour returns 404`() {
        val adminToken = login(adminEmail, adminPassword)

        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .patch("/api/v1/tours-operator/staff/tours/${UUID.randomUUID()}/activate")
                    .header("Authorization", "Bearer $adminToken"),
            ).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .status()
                    .isNotFound,
            )
    }

    // ── test 14: staff POST /staff/tours/{id}/slots creates a slot ────────────

    @Test
    fun `staff POST slots staff creates a slot and returns 201`() {
        val adminToken = login(adminEmail, adminPassword)
        val (tourId, _) = seedTourAndSlot("create-slot-14", capacity = 10)
        val tomorrow =
            java.time.LocalDate
                .now()
                .plusDays(2)
                .toString()

        val body =
            mockMvc
                .post("/api/v1/tours-operator/staff/tours/$tourId/slots") {
                    header("Authorization", "Bearer $adminToken")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
                        {
                          "date": "$tomorrow",
                          "timeSlot": "SUNSET",
                          "capacity": 8
                        }
                        """.trimIndent()
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.tourId") { value(tourId.toString()) }
                    jsonPath("$.timeSlot") { value("SUNSET") }
                    jsonPath("$.capacity") { value(8) }
                    jsonPath("$.bookedCount") { value(0) }
                    jsonPath("$.isBlocked") { value(false) }
                }.andReturn()
                .response.contentAsString

        val slotId = jsonField(body, "id")
        assert(slotId.isNotBlank())
    }

    @Test
    fun `staff POST slots staff returns 409 when slot already exists`() {
        val adminToken = login(adminEmail, adminPassword)
        val (tourId, _) = seedTourAndSlot("slot-dup-14b", capacity = 10)
        val date =
            java.time.LocalDate
                .now()
                .plusDays(3)
                .toString()

        val payload = """{"date":"$date","timeSlot":"MORNING","capacity":5}"""

        mockMvc
            .post("/api/v1/tours-operator/staff/tours/$tourId/slots") {
                header("Authorization", "Bearer $adminToken")
                contentType = MediaType.APPLICATION_JSON
                content = payload
            }.andExpect { status { isCreated() } }

        mockMvc
            .post("/api/v1/tours-operator/staff/tours/$tourId/slots") {
                header("Authorization", "Bearer $adminToken")
                contentType = MediaType.APPLICATION_JSON
                content = payload
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("slot_already_exists") }
            }
    }

    // ── test 15: staff PATCH block / unblock a slot ───────────────────────────

    @Test
    fun `staff PATCH block and unblock a slot`() {
        val adminToken = login(adminEmail, adminPassword)
        val (tourId, slotId) = seedTourAndSlot("block-15", capacity = 10)

        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .patch("/api/v1/tours-operator/staff/tours/$tourId/slots/$slotId/block")
                    .header("Authorization", "Bearer $adminToken"),
            ).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .status()
                    .isNoContent,
            )

        val date = LocalDate.of(2027, 6, 15).toString()
        mockMvc
            .get("/api/v1/tours-operator/tours/$tourId/slots/by-date") {
                param("date", date)
            }.andExpect {
                status { isOk() }
                jsonPath("$[0].isBlocked") { value(true) }
            }

        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .patch("/api/v1/tours-operator/staff/tours/$tourId/slots/$slotId/unblock")
                    .header("Authorization", "Bearer $adminToken"),
            ).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .status()
                    .isNoContent,
            )

        mockMvc
            .get("/api/v1/tours-operator/tours/$tourId/slots/by-date") {
                param("date", date)
            }.andExpect {
                status { isOk() }
                jsonPath("$[0].isBlocked") { value(false) }
            }
    }

    @Test
    fun `PATCH block unknown slot returns 404`() {
        val adminToken = login(adminEmail, adminPassword)
        val (tourId, _) = seedTourAndSlot("block-404", capacity = 5)

        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .patch("/api/v1/tours-operator/staff/tours/$tourId/slots/${UUID.randomUUID()}/block")
                    .header("Authorization", "Bearer $adminToken"),
            ).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .status()
                    .isNotFound,
            )
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
            registry.add("tours-operator.paymob.mock-enabled") { true }
        }
    }

    // ── booking history (WEGO-016-F2) ─────────────────────────────────────────

    @Test
    fun `GET history shows who cancelled a booking, when and why`() {
        val (_, slotId) = seedTourAndSlot("history-f2", capacity = 10)
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
                content = """{"reason":"Customer called to cancel"}"""
            }.andExpect { status { isOk() } }

        mockMvc
            .get("/api/v1/tours-operator/bookings/$bookingId/history") {
                header("Authorization", "Bearer $adminToken")
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].eventType") { value("BOOKING_CREATED") }
                jsonPath("$[0].actorEmail") { isEmpty() }
                jsonPath("$[1].eventType") { value("BOOKING_CANCELLED") }
                jsonPath("$[1].fromStatus") { value("NEW") }
                jsonPath("$[1].toStatus") { value("CANCELLED") }
                jsonPath("$[1].reason") { value("Customer called to cancel") }
                jsonPath("$[1].actorEmail") { value(adminEmail) }
                jsonPath("$[1].occurredAt") { isNotEmpty() }
            }
    }

    @Test
    fun `GET history requires authentication and returns 404 for an unknown booking`() {
        val adminToken = login(adminEmail, adminPassword)
        val unknown = java.util.UUID.randomUUID()
        mockMvc
            .get("/api/v1/tours-operator/bookings/$unknown/history")
            .andExpect { status { isUnauthorized() } }
        mockMvc
            .get("/api/v1/tours-operator/bookings/$unknown/history") {
                header("Authorization", "Bearer $adminToken")
            }.andExpect { status { isNotFound() } }
    }
}
