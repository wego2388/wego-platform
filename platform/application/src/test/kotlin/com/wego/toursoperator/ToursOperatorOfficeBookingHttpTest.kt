package com.wego.toursoperator

import com.jayway.jsonpath.JsonPath
import com.wego.generated.jooq.tables.IdentityRole.IDENTITY_ROLE
import com.wego.generated.jooq.tables.IdentityRolePermission.IDENTITY_ROLE_PERMISSION
import com.wego.generated.jooq.tables.ToursOperatorBooking.TOURS_OPERATOR_BOOKING
import com.wego.generated.jooq.tables.ToursOperatorBookingAuditEvent.TOURS_OPERATOR_BOOKING_AUDIT_EVENT
import com.wego.generated.jooq.tables.ToursOperatorOfficeCollection.TOURS_OPERATOR_OFFICE_COLLECTION
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
import com.wego.toursoperator.application.ExpireBookingResult
import com.wego.toursoperator.application.ExpireBookingService
import com.wego.toursoperator.application.ExpireOverduePaymentsService
import com.wego.toursoperator.domain.BookingId
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
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * WEGO-016-OPS2-C: staff-created (office) bookings and the office cash ledger,
 * over real PostgreSQL. Sales control is global state, so this class owns its
 * own database and always restores it.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class ToursOperatorOfficeBookingHttpTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var passwordHasher: PasswordHasher

    @Autowired private lateinit var dsl: DSLContext

    @Autowired private lateinit var expireOverdue: ExpireOverduePaymentsService

    @Autowired private lateinit var expireBooking: ExpireBookingService

    private val adminEmail = "office-admin@example.com"
    private val password = "a-very-long-office-password-123"

    private lateinit var adminId: UUID

    @BeforeEach
    fun seedUsers() {
        adminId = seedUser(adminEmail, setOf("platform-admin"))
        seedUser("office-plain@example.com", emptySet())
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun seedUser(
        email: String,
        roles: Set<String>,
    ): UUID {
        userRepository.findByEmail(EmailAddress.of(email))?.let { return it.id.value }
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

    private fun tokenWith(permission: String): String {
        val roleCode = "office-test-${permission.replace(':', '-').replace('.', '-')}"
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
        val email = "$roleCode@example.com"
        seedUser(email, setOf(roleCode))
        return login(email)
    }

    private fun login(email: String): String {
        val body =
            mockMvc
                .post("/api/v1/identity/login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"$email","password":"$password"}"""
                }.andReturn()
                .response.contentAsString
        return JsonPath.read(body, "$.token")
    }

    private fun adminToken() = login(adminEmail)

    private fun seedTourAndSlot(
        suffix: String,
        capacity: Int,
        date: LocalDate = LocalDate.of(2027, 6, 15),
        perUnit: Boolean = false,
    ): Pair<UUID, UUID> {
        val tourId = UUID.randomUUID()
        val slotId = UUID.randomUUID()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        dsl
            .insertInto(TOURS_OPERATOR_TOUR)
            .set(TOURS_OPERATOR_TOUR.ID, tourId)
            .set(TOURS_OPERATOR_TOUR.SLUG, "office-test-$suffix")
            .set(TOURS_OPERATOR_TOUR.CATEGORY, "DESERT")
            .set(TOURS_OPERATOR_TOUR.DURATION_TEXT, "4 hours")
            .set(TOURS_OPERATOR_TOUR.PRICE_ADULT_CENTS, 3500L)
            .set(TOURS_OPERATOR_TOUR.PRICE_CHILD_CENTS, 1750L)
            .set(TOURS_OPERATOR_TOUR.CAPACITY, capacity)
            .set(TOURS_OPERATOR_TOUR.AVAILABLE_TIME_SLOTS, "MORNING")
            .set(TOURS_OPERATOR_TOUR.SORT_ORDER, 1)
            .set(TOURS_OPERATOR_TOUR.IS_ACTIVE, true)
            .set(TOURS_OPERATOR_TOUR.CREATED_BY_USER_ID, adminId)
            .set(TOURS_OPERATOR_TOUR.CREATED_AT, now)
            .execute()
        dsl
            .insertInto(TOURS_OPERATOR_TOUR_SLOT)
            .set(TOURS_OPERATOR_TOUR_SLOT.ID, slotId)
            .set(TOURS_OPERATOR_TOUR_SLOT.TOUR_ID, tourId)
            .set(TOURS_OPERATOR_TOUR_SLOT.DATE, date)
            .set(TOURS_OPERATOR_TOUR_SLOT.TIME_SLOT, "MORNING")
            .set(TOURS_OPERATOR_TOUR_SLOT.CAPACITY, capacity)
            .set(TOURS_OPERATOR_TOUR_SLOT.BOOKED_COUNT, 0)
            .set(TOURS_OPERATOR_TOUR_SLOT.IS_BLOCKED, false)
            .set(TOURS_OPERATOR_TOUR_SLOT.CREATED_AT, now)
            .execute()
        if (perUnit) {
            dsl.execute("UPDATE wego.tours_operator_tour SET price_basis = 'PER_UNIT', price_child_cents = NULL WHERE id = ?", tourId)
            dsl.execute(
                """
                INSERT INTO wego.tours_operator_tour_price_option (tour_id, code, label_en, seats_per_unit, price_cents, sort_order)
                VALUES (?, 'buggy', 'Two-seat buggy', 2, 3000, 0)
                """.trimIndent(),
                tourId,
            )
        }
        return tourId to slotId
    }

    private fun party(
        slotId: UUID,
        adults: Int = 2,
        children: Int = 1,
        unit: String = "",
    ) = """
        "slotId": "$slotId", "adultsCount": $adults, "childrenCount": $children,$unit
        "customer": { "fullName": "Ahmed Hassan", "phone": "+201234567890", "nationality": "EG" },
        "hotelName": "Hilton Sharm Dreams", "hotelRoom": "312", "locale": "en"
        """.trimIndent()

    private fun publicBody(
        slotId: UUID,
        adults: Int = 2,
        children: Int = 1,
        unit: String = "",
    ) = "{ ${party(slotId, adults, children, unit)} }"

    private fun officeBody(
        slotId: UUID,
        key: UUID = UUID.randomUUID(),
        adults: Int = 2,
        children: Int = 1,
        unit: String = "",
    ) = """{ "clientRequestId": "$key", ${party(slotId, adults, children, unit)} }"""

    private fun createOffice(
        token: String,
        body: String,
    ) = mockMvc.post("/api/v1/tours-operator/staff/bookings") {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun newOffice(
        slotId: UUID,
        adults: Int = 2,
        children: Int = 1,
    ): String =
        JsonPath.read(
            createOffice(adminToken(), officeBody(slotId, adults = adults, children = children))
                .andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString,
            "$.id",
        )

    private fun booked(slotId: UUID): Int =
        dsl
            .select(TOURS_OPERATOR_TOUR_SLOT.BOOKED_COUNT)
            .from(TOURS_OPERATOR_TOUR_SLOT)
            .where(TOURS_OPERATOR_TOUR_SLOT.ID.eq(slotId))
            .fetchOne(TOURS_OPERATOR_TOUR_SLOT.BOOKED_COUNT)!!

    private fun collect(
        token: String,
        bookingId: String,
        amount: String,
        method: String = "CASH_AT_OFFICE",
        key: UUID = UUID.randomUUID(),
        currency: String = "EUR",
        reference: String? = null,
    ) = mockMvc.post("/api/v1/tours-operator/staff/bookings/$bookingId/collections") {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        val ref = reference?.let { ""","reference":${jsonString(it)}""" } ?: ""
        content = """{"clientRequestId":"$key","method":"$method","currency":"$currency","amount":$amount$ref}"""
    }

    private fun jsonString(raw: String): String =
        buildString {
            append('"')
            for (c in raw) {
                when {
                    c == '"' -> append("\\\"")
                    c == '\\' -> append("\\\\")
                    c.code < 0x20 -> append("\\u%04x".format(c.code))
                    else -> append(c)
                }
            }
            append('"')
        }

    private fun setRate(
        token: String,
        rate: String,
    ) = mockMvc.post("/api/v1/tours-operator/staff/fx-rate") {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = """{"egpPerEur":$rate}"""
    }

    private fun reverse(
        token: String,
        bookingId: String,
        collectionId: String,
        reason: String = "Counted wrong",
        key: UUID = UUID.randomUUID(),
    ) = mockMvc.post("/api/v1/tours-operator/staff/bookings/$bookingId/collections/$collectionId/reverse") {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = """{"clientRequestId":"$key","reason":"$reason"}"""
    }

    private fun getBooking(
        token: String,
        id: String,
    ): String =
        mockMvc
            .get("/api/v1/tours-operator/bookings/$id") { header("Authorization", "Bearer $token") }
            .andReturn()
            .response.contentAsString

    private fun entryId(response: String): String = JsonPath.read(response, "$.entry.id")

    // ── permissions ──────────────────────────────────────────────────────────

    @Test
    fun `permission matrix separates creating an office booking from recording cash`() {
        val (_, slotId) = seedTourAndSlot("perm", capacity = 20)

        mockMvc
            .post("/api/v1/tours-operator/staff/bookings") {
                contentType = MediaType.APPLICATION_JSON
                content = officeBody(slotId)
            }.andExpect { status { isUnauthorized() } }
        createOffice(login("office-plain@example.com"), officeBody(slotId)).andExpect { status { isForbidden() } }
        createOffice(tokenWith("tours-operator.booking:collect-cash"), officeBody(slotId)).andExpect { status { isForbidden() } }
        createOffice(tokenWith("tours-operator.booking:view"), officeBody(slotId)).andExpect { status { isForbidden() } }
        assertThat(booked(slotId)).isZero()

        val creator = tokenWith("tours-operator.booking:create-office")
        val id =
            JsonPath.read<String>(
                createOffice(creator, officeBody(slotId))
                    .andExpect { status { isCreated() } }
                    .andReturn()
                    .response.contentAsString,
                "$.id",
            )
        // The creator cannot record or reverse cash, and cash-only staff cannot read the ledger without view.
        collect(creator, id, "10.00").andExpect { status { isForbidden() } }
        reverse(creator, id, UUID.randomUUID().toString()).andExpect { status { isForbidden() } }
        val cashier = tokenWith("tours-operator.booking:collect-cash")
        collect(cashier, id, "10.00").andExpect { status { isCreated() } }
        mockMvc
            .get("/api/v1/tours-operator/staff/bookings/$id/collections") { header("Authorization", "Bearer $cashier") }
            .andExpect { status { isForbidden() } }
        mockMvc
            .get("/api/v1/tours-operator/staff/bookings/$id/collections") {
                header("Authorization", "Bearer ${tokenWith("tours-operator.booking:view")}")
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
            }
    }

    // ── pricing and capacity parity with the public path ─────────────────────

    @Test
    fun `office booking prices exactly like the public path and starts unpaid and awaiting collection`() {
        val (_, slotId) = seedTourAndSlot("parity", capacity = 20)
        val publicBooking =
            mockMvc
                .post("/api/v1/tours-operator/bookings") {
                    contentType = MediaType.APPLICATION_JSON
                    content = publicBody(slotId)
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.channel") { value("ONLINE") }
                    jsonPath("$.awaitingCollection") { value(false) }
                    jsonPath("$.officePayment") { doesNotExist() }
                }.andReturn()
                .response.contentAsString
        val office =
            createOffice(adminToken(), officeBody(slotId))
                .andExpect {
                    status { isCreated() }
                    jsonPath("$.status") { value("NEW") }
                    jsonPath("$.channel") { value("OFFICE") }
                    jsonPath("$.awaitingCollection") { value(true) }
                    jsonPath("$.officePayment.state") { value("UNPAID") }
                    jsonPath("$.officePayment.collected.amount") { value("0.00") }
                    jsonPath("$.officePayment.outstanding.amount") { value("87.50") }
                    jsonPath("$.officePayment.cashToReturn") { doesNotExist() }
                }.andReturn()
                .response.contentAsString
        for (field in listOf("priceAdult.amount", "priceChild.amount", "totalPrice.amount")) {
            assertThat(JsonPath.read<String>(office, "$.$field")).isEqualTo(JsonPath.read<String>(publicBooking, "$.$field"))
        }
        assertThat(booked(slotId)).isEqualTo(6)
        // No Paymob row and no online payment for an office booking.
        val id = UUID.fromString(JsonPath.read(office, "$.id"))
        assertThat(dsl.fetchCount(TOURS_OPERATOR_PAYMENT, TOURS_OPERATOR_PAYMENT.BOOKING_ID.eq(id))).isZero()
    }

    @Test
    fun `per-unit office booking uses the same unit pricing and seats`() {
        val (_, slotId) = seedTourAndSlot("unit", capacity = 10, perUnit = true)
        val unit = """ "priceOptionCode": "buggy", "unitCount": 2,"""
        createOffice(adminToken(), officeBody(slotId, adults = 2, children = 1, unit = unit)).andExpect {
            status { isCreated() }
            jsonPath("$.totalPrice.amount") { value("60.00") }
            jsonPath("$.unit.optionCode") { value("buggy") }
            jsonPath("$.unit.unitCount") { value(2) }
            jsonPath("$.officePayment.outstanding.amount") { value("60.00") }
        }
        assertThat(booked(slotId)).isEqualTo(4)
        createOffice(adminToken(), officeBody(slotId, adults = 3, children = 0, unit = """ "priceOptionCode": "buggy", "unitCount": 1,"""))
            .andExpect {
                status { isUnprocessableEntity() }
                jsonPath("$.error") { value("guests_exceed_units") }
            }
        createOffice(adminToken(), officeBody(slotId, adults = 2, children = 0)).andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.error") { value("price_option_required") }
        }
        assertThat(booked(slotId)).isEqualTo(4)
    }

    @Test
    fun `past blocked and full slots are refused for office bookings`() {
        val (_, past) = seedTourAndSlot("past", capacity = 10, date = LocalDate.of(2020, 1, 1))
        createOffice(adminToken(), officeBody(past)).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("slot_in_past") }
        }
        val (_, blocked) = seedTourAndSlot("blocked", capacity = 10)
        dsl.execute("UPDATE wego.tours_operator_tour_slot SET is_blocked = true WHERE id = ?", blocked)
        createOffice(adminToken(), officeBody(blocked)).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("slot_blocked") }
        }
        val (_, tiny) = seedTourAndSlot("tiny", capacity = 2)
        createOffice(adminToken(), officeBody(tiny)).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("slot_fully_booked") }
        }
        createOffice(adminToken(), officeBody(UUID.randomUUID())).andExpect { status { isNotFound() } }
        assertThat(booked(past) + booked(blocked) + booked(tiny)).isZero()
    }

    @Test
    fun `sales pause stops online sales but not staff office bookings`() {
        val admin = adminToken()
        val (_, slotId) = seedTourAndSlot("paused", capacity = 20)

        fun pause(paused: Boolean) =
            mockMvc
                .put("/api/v1/tours-operator/staff/sales-control") {
                    header("Authorization", "Bearer $admin")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"bookingsPaused":$paused,"paymentsPaused":$paused}"""
                }.andExpect { status { isOk() } }
        pause(true)
        try {
            mockMvc
                .post("/api/v1/tours-operator/bookings") {
                    contentType = MediaType.APPLICATION_JSON
                    content = publicBody(slotId)
                }.andExpect {
                    status { isServiceUnavailable() }
                    jsonPath("$.error") { value("bookings_paused") }
                }
            createOffice(admin, officeBody(slotId)).andExpect { status { isCreated() } }
        } finally {
            pause(false)
        }
        assertThat(booked(slotId)).isEqualTo(3)
    }

    // ── audit ────────────────────────────────────────────────────────────────

    @Test
    fun `audit trail records the staff actor and the office channel`() {
        val (_, slotId) = seedTourAndSlot("audit", capacity = 20)
        val id = newOffice(slotId)
        val row = dsl.selectFrom(TOURS_OPERATOR_BOOKING).where(TOURS_OPERATOR_BOOKING.ID.eq(UUID.fromString(id))).fetchOne()!!
        assertThat(row.channel).isEqualTo("OFFICE")
        assertThat(row.createdByUserId).isEqualTo(adminId)
        assertThat(row.clientRequestId).isNotNull()
        val events =
            dsl
                .selectFrom(TOURS_OPERATOR_BOOKING_AUDIT_EVENT)
                .where(TOURS_OPERATOR_BOOKING_AUDIT_EVENT.BOOKING_ID.eq(UUID.fromString(id)))
                .fetch()
        assertThat(events).hasSize(1)
        assertThat(events[0].eventType).isEqualTo("BOOKING_CREATED_OFFICE")
        assertThat(events[0].actorUserId).isEqualTo(adminId)
        mockMvc
            .get("/api/v1/tours-operator/bookings/$id/history") { header("Authorization", "Bearer ${adminToken()}") }
            .andExpect {
                status { isOk() }
                jsonPath("$[0].eventType") { value("BOOKING_CREATED_OFFICE") }
                jsonPath("$[0].actorEmail") { value(adminEmail) }
            }
    }

    // ── idempotency ──────────────────────────────────────────────────────────

    @Test
    fun `retrying the same request id returns the same booking and takes no more places`() {
        val (_, slotId) = seedTourAndSlot("idem", capacity = 20)
        val key = UUID.randomUUID()
        val admin = adminToken()
        val first =
            createOffice(admin, officeBody(slotId, key))
                .andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        val retry =
            createOffice(admin, officeBody(slotId, key))
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        assertThat(JsonPath.read<String>(retry, "$.id")).isEqualTo(JsonPath.read<String>(first, "$.id"))
        assertThat(JsonPath.read<String>(retry, "$.reference")).isEqualTo(JsonPath.read<String>(first, "$.reference"))
        assertThat(booked(slotId)).isEqualTo(3)
        assertThat(dsl.fetchCount(TOURS_OPERATOR_BOOKING, TOURS_OPERATOR_BOOKING.CLIENT_REQUEST_ID.eq(key))).isEqualTo(1)
        // The same key for a different party is a client bug, not a replay.
        createOffice(admin, officeBody(slotId, key, adults = 4, children = 0)).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("idempotency_key_reused") }
        }
        assertThat(booked(slotId)).isEqualTo(3)
    }

    @Test
    fun `concurrent retries of one request id create exactly one booking`() {
        val (_, slotId) = seedTourAndSlot("idem-race", capacity = 20)
        val key = UUID.randomUUID()
        val admin = adminToken()
        val executor = Executors.newFixedThreadPool(6)
        val statuses =
            (1..6)
                .map { executor.submit(Callable { createOffice(admin, officeBody(slotId, key)).andReturn().response.status }) }
                .map { it.get() }
        executor.shutdown()
        assertThat(statuses.count { it == 201 }).isEqualTo(1)
        assertThat(statuses.count { it == 200 }).isEqualTo(5)
        assertThat(booked(slotId)).isEqualTo(3)
    }

    // ── capacity race ────────────────────────────────────────────────────────

    @Test
    fun `concurrent office and online bookings never oversell a slot`() {
        // 6 places, parties of 2 → exactly 3 of the 12 racing requests may win.
        val (_, slotId) = seedTourAndSlot("race", capacity = 6)
        val admin = adminToken()
        val executor = Executors.newFixedThreadPool(12)
        val start = CountDownLatch(1)
        val results =
            (1..12).map { n ->
                executor.submit(
                    Callable {
                        start.await()
                        if (n % 2 == 0) {
                            createOffice(admin, officeBody(slotId, adults = 2, children = 0)).andReturn().response.status
                        } else {
                            mockMvc
                                .post("/api/v1/tours-operator/bookings") {
                                    contentType = MediaType.APPLICATION_JSON
                                    content = publicBody(slotId, adults = 2, children = 0)
                                }.andReturn()
                                .response.status
                        }
                    },
                )
            }
        start.countDown()
        val statuses = results.map { it.get() }
        executor.shutdown()
        assertThat(statuses.count { it == 201 }).isEqualTo(3)
        assertThat(statuses.count { it == 409 }).isEqualTo(9)
        assertThat(booked(slotId)).isEqualTo(6)
        assertThat(dsl.fetchCount(TOURS_OPERATOR_BOOKING, TOURS_OPERATOR_BOOKING.SLOT_ID.eq(slotId))).isEqualTo(3)
    }

    // ── no online expiry; cancel releases places ─────────────────────────────

    @Test
    fun `office bookings are never expired by the online payment sweeper`() {
        val (_, slotId) = seedTourAndSlot("sweeper", capacity = 20)
        val officeId = newOffice(slotId)
        val onlineId =
            JsonPath.read<String>(
                mockMvc
                    .post("/api/v1/tours-operator/bookings") {
                        contentType = MediaType.APPLICATION_JSON
                        content = publicBody(slotId)
                    }.andExpect { status { isCreated() } }
                    .andReturn()
                    .response.contentAsString,
                "$.id",
            )
        val longAgo = OffsetDateTime.now(ZoneOffset.UTC).minusHours(3)
        dsl
            .update(TOURS_OPERATOR_BOOKING)
            .set(TOURS_OPERATOR_BOOKING.CREATED_AT, longAgo)
            .where(TOURS_OPERATOR_BOOKING.ID.`in`(UUID.fromString(officeId), UUID.fromString(onlineId)))
            .execute()

        expireOverdue.expireOverdue()

        assertThat(statusOf(officeId)).isEqualTo("NEW")
        assertThat(statusOf(onlineId)).isEqualTo("EXPIRED")
        // Only the online booking's three places came back.
        assertThat(booked(slotId)).isEqualTo(3)
        // Even a direct expire call refuses an office booking.
        assertThat(expireBooking.expire(BookingId(UUID.fromString(officeId)), null)).isEqualTo(ExpireBookingResult.CannotExpire)
        assertThat(statusOf(officeId)).isEqualTo("NEW")
    }

    private fun statusOf(id: String): String =
        dsl
            .select(TOURS_OPERATOR_BOOKING.STATUS)
            .from(TOURS_OPERATOR_BOOKING)
            .where(TOURS_OPERATOR_BOOKING.ID.eq(UUID.fromString(id)))
            .fetchOne(TOURS_OPERATOR_BOOKING.STATUS)!!

    @Test
    fun `the database refuses an expired office booking`() {
        val (_, slotId) = seedTourAndSlot("db-guard", capacity = 20)
        val id = newOffice(slotId)
        org.assertj.core.api.Assertions
            .assertThatThrownBy {
                dsl.execute(
                    "UPDATE wego.tours_operator_booking SET status = 'EXPIRED', expired_at = now() WHERE id = ?",
                    UUID.fromString(id),
                )
            }.hasMessageContaining("tours_operator_booking_office_never_expires")
    }

    @Test
    fun `staff cancel releases the places and an office booking cannot be paid online`() {
        val (_, slotId) = seedTourAndSlot("cancel", capacity = 20)
        val id = newOffice(slotId)
        assertThat(booked(slotId)).isEqualTo(3)
        mockMvc
            .post("/api/v1/tours-operator/bookings/$id/pay") {
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("office_booking_not_payable_online") }
            }
        assertThat(dsl.fetchCount(TOURS_OPERATOR_PAYMENT, TOURS_OPERATOR_PAYMENT.BOOKING_ID.eq(UUID.fromString(id)))).isZero()
        mockMvc
            .post("/api/v1/tours-operator/bookings/$id/cancel") {
                header("Authorization", "Bearer ${adminToken()}")
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason":"Customer called the office"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("CANCELLED") }
                jsonPath("$.awaitingCollection") { value(false) }
                jsonPath("$.officePayment.cashToReturn") { doesNotExist() }
            }
        assertThat(booked(slotId)).isZero()
    }

    // ── cash collections ─────────────────────────────────────────────────────

    @Test
    fun `deposit then remainder moves UNPAID to PARTIALLY_PAID to PAID`() {
        val (_, slotId) = seedTourAndSlot("cash", capacity = 20)
        val id = newOffice(slotId) // €87.50
        val admin = adminToken()

        collect(admin, id, "20.00", "CASH_ON_PICKUP").andExpect {
            status { isCreated() }
            jsonPath("$.entry.kind") { value("COLLECTION") }
            jsonPath("$.entry.method") { value("CASH_ON_PICKUP") }
            jsonPath("$.entry.recordedByUserId") { value(adminId.toString()) }
            jsonPath("$.officePayment.state") { value("PARTIALLY_PAID") }
            jsonPath("$.officePayment.collected.amount") { value("20.00") }
            jsonPath("$.officePayment.outstanding.amount") { value("67.50") }
        }
        mockMvc
            .get("/api/v1/tours-operator/bookings") {
                header("Authorization", "Bearer $admin")
                param("date", "2027-06-15")
                param("size", "200")
            }.andExpect {
                jsonPath("$[?(@.id=='$id')].officePayment.state") { value("PARTIALLY_PAID") }
                jsonPath("$[?(@.id=='$id')].officePayment.outstanding.amount") { value("67.50") }
            }
        collect(admin, id, "67.50").andExpect {
            status { isCreated() }
            jsonPath("$.officePayment.state") { value("PAID") }
            jsonPath("$.officePayment.outstanding.amount") { value("0.00") }
        }
        // Fully collected: nothing more can be taken. The booking is still a live NEW booking.
        collect(admin, id, "0.01").andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("amount_exceeds_outstanding") }
        }
        assertThat(statusOf(id)).isEqualTo("NEW")
        assertThat(dsl.fetchCount(TOURS_OPERATOR_PAYMENT, TOURS_OPERATOR_PAYMENT.BOOKING_ID.eq(UUID.fromString(id)))).isZero()
        assertThat(dsl.fetchCount(TOURS_OPERATOR_OFFICE_COLLECTION, TOURS_OPERATOR_OFFICE_COLLECTION.BOOKING_ID.eq(UUID.fromString(id))))
            .isEqualTo(2)
    }

    @Test
    fun `overpayment invalid amounts and unknown methods are refused`() {
        val (_, slotId) = seedTourAndSlot("overpay", capacity = 20)
        val id = newOffice(slotId)
        val admin = adminToken()
        collect(admin, id, "87.51").andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("amount_exceeds_outstanding") }
        }
        collect(admin, id, "0").andExpect { status { isBadRequest() } }
        collect(admin, id, "-5.00").andExpect { status { isBadRequest() } }
        collect(admin, id, "10.005").andExpect { status { isBadRequest() } }
        collect(admin, id, "10.00", "BANK_TRANSFER").andExpect { status { isBadRequest() } }
        collect(admin, id, "10.00", "CHEQUE").andExpect { status { isBadRequest() } }
        collect(admin, id, "10.00", currency = "USD").andExpect { status { isBadRequest() } }
        assertThat(dsl.fetchCount(TOURS_OPERATOR_OFFICE_COLLECTION, TOURS_OPERATOR_OFFICE_COLLECTION.BOOKING_ID.eq(UUID.fromString(id))))
            .isZero()
        // Online bookings never take office cash.
        val online =
            JsonPath.read<String>(
                mockMvc
                    .post("/api/v1/tours-operator/bookings") {
                        contentType = MediaType.APPLICATION_JSON
                        content = publicBody(slotId)
                    }.andReturn()
                    .response.contentAsString,
                "$.id",
            )
        collect(admin, online, "10.00").andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("not_an_office_booking") }
        }
    }

    @Test
    fun `a retried collection with the same request id is recorded once`() {
        val (_, slotId) = seedTourAndSlot("cash-idem", capacity = 20)
        val id = newOffice(slotId)
        val admin = adminToken()
        val key = UUID.randomUUID()
        collect(admin, id, "10.00", key = key).andExpect { status { isCreated() } }
        collect(admin, id, "10.00", key = key).andExpect { status { isOk() } }
        collect(admin, id, "11.00", key = key).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("idempotency_key_reused") }
        }
        getBooking(admin, id).let { assertThat(JsonPath.read<String>(it, "$.officePayment.collected.amount")).isEqualTo("10.00") }
    }

    @Test
    fun `concurrent collections can never exceed the booking total`() {
        val (_, slotId) = seedTourAndSlot("cash-race", capacity = 20)
        val id = newOffice(slotId) // €87.50 → only two €30 collections fit
        val admin = adminToken()
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val results =
            (1..8).map {
                executor.submit(
                    Callable {
                        start.await()
                        collect(admin, id, "30.00").andReturn().response.status
                    },
                )
            }
        start.countDown()
        val statuses = results.map { it.get() }
        executor.shutdown()
        assertThat(statuses.count { it == 201 }).isEqualTo(2)
        assertThat(statuses.count { it == 409 }).isEqualTo(6)
        val net =
            dsl
                .select(
                    org.jooq.impl.DSL
                        .sum(TOURS_OPERATOR_OFFICE_COLLECTION.AMOUNT_EUR),
                ).from(TOURS_OPERATOR_OFFICE_COLLECTION)
                .where(TOURS_OPERATOR_OFFICE_COLLECTION.BOOKING_ID.eq(UUID.fromString(id)))
                .fetchOne(0, java.math.BigDecimal::class.java)!!
        assertThat(net).isEqualByComparingTo("60.00")
        assertThat(JsonPath.read<String>(getBooking(admin, id), "$.officePayment.state")).isEqualTo("PARTIALLY_PAID")
    }

    @Test
    fun `a mistake is corrected only by an audited reversal with a reason`() {
        val (_, slotId) = seedTourAndSlot("reversal", capacity = 20)
        val id = newOffice(slotId)
        val admin = adminToken()
        val first = entryId(collect(admin, id, "87.50").andReturn().response.contentAsString)
        assertThat(JsonPath.read<String>(getBooking(admin, id), "$.officePayment.state")).isEqualTo("PAID")

        reverse(admin, id, first, reason = "   ").andExpect { status { isBadRequest() } }
        reverse(admin, id, UUID.randomUUID().toString()).andExpect { status { isNotFound() } }
        val reversal =
            reverse(admin, id, first, reason = "Counted the wrong note")
                .andExpect {
                    status { isCreated() }
                    jsonPath("$.entry.kind") { value("REVERSAL") }
                    jsonPath("$.entry.reason") { value("Counted the wrong note") }
                    jsonPath("$.entry.reversesCollectionId") { value(first) }
                    jsonPath("$.entry.recordedByUserId") { value(adminId.toString()) }
                    jsonPath("$.officePayment.state") { value("UNPAID") }
                    jsonPath("$.officePayment.outstanding.amount") { value("87.50") }
                }.andReturn()
                .response.contentAsString
        // Reversed once only, and a reversal cannot itself be reversed.
        reverse(admin, id, first).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("collection_already_reversed") }
        }
        reverse(admin, id, entryId(reversal)).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("collection_not_reversible") }
        }
        // History is append-only: both entries remain, nothing was edited.
        mockMvc
            .get("/api/v1/tours-operator/staff/bookings/$id/collections") { header("Authorization", "Bearer $admin") }
            .andExpect {
                jsonPath("$.length()") { value(2) }
                jsonPath("$[0].kind") { value("COLLECTION") }
                jsonPath("$[1].kind") { value("REVERSAL") }
            }
        // The real money can be collected again after the correction.
        collect(admin, id, "87.50").andExpect { status { isCreated() } }
    }

    @Test
    fun `cancelling a part-paid office booking keeps its collection history and flags cash to return`() {
        val (_, slotId) = seedTourAndSlot("cancel-cash", capacity = 20)
        val id = newOffice(slotId)
        val admin = adminToken()
        val deposit = entryId(collect(admin, id, "25.00").andReturn().response.contentAsString)

        mockMvc
            .post("/api/v1/tours-operator/bookings/$id/cancel") {
                header("Authorization", "Bearer $admin")
                contentType = MediaType.APPLICATION_JSON
                content = """{"reason":"Customer cancelled after the deposit"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("CANCELLED") }
                jsonPath("$.officePayment.state") { value("PARTIALLY_PAID") }
                jsonPath("$.officePayment.collected.amount") { value("25.00") }
                jsonPath("$.officePayment.cashToReturn.amount") { value("25.00") }
            }
        assertThat(booked(slotId)).isZero()
        // No automatic refund: the ledger is untouched, no Paymob rows exist, and no new cash can be taken.
        assertThat(dsl.fetchCount(TOURS_OPERATOR_OFFICE_COLLECTION, TOURS_OPERATOR_OFFICE_COLLECTION.BOOKING_ID.eq(UUID.fromString(id))))
            .isEqualTo(1)
        assertThat(dsl.fetchCount(TOURS_OPERATOR_PAYMENT, TOURS_OPERATOR_PAYMENT.BOOKING_ID.eq(UUID.fromString(id)))).isZero()
        collect(admin, id, "5.00").andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("booking_not_open_status_cancelled") }
        }
        mockMvc
            .get("/api/v1/tours-operator/staff/bookings/$id/collections") { header("Authorization", "Bearer $admin") }
            .andExpect { jsonPath("$[0].id") { value(deposit) } }
        // Handing the cash back is recorded as an audited reversal, which clears the flag.
        reverse(admin, id, deposit, reason = "Cash handed back to the customer").andExpect {
            status { isCreated() }
            jsonPath("$.officePayment.cashToReturn") { doesNotExist() }
        }
    }

    // ── methods and receipt references ───────────────────────────────────────

    @Test
    fun `every approved method records and non-cash needs a clean reference`() {
        val (_, slotId) = seedTourAndSlot("methods", capacity = 40)
        val id = newOffice(slotId)
        val admin = adminToken()
        val tag = UUID.randomUUID().toString().take(8)
        collect(admin, id, "1.00", "CASH_ON_PICKUP").andExpect { status { isCreated() } }
        for (method in listOf("MOBILE_WALLET", "CARD_TERMINAL", "INSTAPAY", "FAWRY_OFFICE")) {
            collect(admin, id, "1.00", method).andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("reference_required") }
            }
            collect(admin, id, "1.00", method, reference = "   ").andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("reference_required") }
            }
            collect(admin, id, "1.00", method, reference = "  $method-$tag  ").andExpect {
                status { isCreated() }
                jsonPath("$.entry.method") { value(method) }
                jsonPath("$.entry.reference") { value("$method-$tag") }
            }
        }
        collect(admin, id, "1.00", "CASH_AT_OFFICE", reference = "R-1").andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("reference_not_allowed") }
        }
        collect(admin, id, "1.00", "INSTAPAY", reference = "x".repeat(65)).andExpect { status { isBadRequest() } }
        collect(admin, id, "1.00", "INSTAPAY", reference = "AB\u0007CD").andExpect { status { isBadRequest() } }
        collect(admin, id, "1.00", "INSTAPAY", reference = "AB\nCD").andExpect { status { isBadRequest() } }
        assertThat(dsl.fetchCount(TOURS_OPERATOR_OFFICE_COLLECTION, TOURS_OPERATOR_OFFICE_COLLECTION.BOOKING_ID.eq(UUID.fromString(id))))
            .isEqualTo(5)
    }

    @Test
    fun `the same receipt cannot be recorded twice for a method but may exist under another method`() {
        val (_, slotId) = seedTourAndSlot("dup-ref", capacity = 40)
        val first = newOffice(slotId)
        val second = newOffice(slotId)
        val admin = adminToken()
        val ref = "RCPT-${UUID.randomUUID()}".take(40)
        val entry = entryId(collect(admin, first, "5.00", "CARD_TERMINAL", reference = ref).andReturn().response.contentAsString)
        collect(admin, second, "5.00", "CARD_TERMINAL", reference = " $ref ").andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("reference_already_used") }
        }
        collect(admin, second, "5.00", "INSTAPAY", reference = ref).andExpect { status { isCreated() } }
        // A reversed receipt stays burnt: the entry is kept, so it cannot be re-recorded.
        reverse(admin, first, entry).andExpect { status { isCreated() } }
        collect(admin, second, "5.00", "CARD_TERMINAL", reference = ref).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("reference_already_used") }
        }
    }

    @Test
    fun `the database itself rejects a duplicate receipt and a missing reference`() {
        val (_, slotId) = seedTourAndSlot("db-ref", capacity = 40)
        val a = newOffice(slotId)
        val b = newOffice(slotId)
        collect(adminToken(), a, "5.00", "FAWRY_OFFICE", reference = "DB-REF-1").andExpect { status { isCreated() } }

        fun insert(
            booking: String,
            method: String,
            reference: String?,
        ) = dsl.execute(
            """
            INSERT INTO wego.tours_operator_office_collection
              (id, booking_id, kind, method, currency_paid, amount_paid, amount_eur, reference, recorded_by_user_id, client_request_id, recorded_at)
            VALUES (?, ?, 'COLLECTION', ?, 'EUR', 1.00, 1.00, ?, ?, ?, now())
            """.trimIndent(),
            UUID.randomUUID(),
            UUID.fromString(booking),
            method,
            reference,
            adminId,
            UUID.randomUUID(),
        )
        org.assertj.core.api.Assertions
            .assertThatThrownBy { insert(b, "FAWRY_OFFICE", "DB-REF-1") }
            .hasMessageContaining("tours_operator_office_collection_reference_unique")
        org.assertj.core.api.Assertions
            .assertThatThrownBy { insert(b, "FAWRY_OFFICE", null) }
            .hasMessageContaining("tours_operator_office_collection_reference_required")
        org.assertj.core.api.Assertions
            .assertThatThrownBy { insert(b, "INSTAPAY", "bad\u0001ref") }
            .hasMessageContaining("tours_operator_office_collection_reference_shape")
    }

    @Test
    fun `concurrent records of one receipt on two bookings leave exactly one`() {
        val (_, slotId) = seedTourAndSlot("ref-race", capacity = 40)
        val bookings = listOf(newOffice(slotId), newOffice(slotId))
        val admin = adminToken()
        val ref = "RACE-${UUID.randomUUID()}".take(40)
        val executor = Executors.newFixedThreadPool(2)
        val start = CountDownLatch(1)
        val statuses =
            bookings
                .map { b ->
                    executor.submit(
                        Callable {
                            start.await()
                            collect(admin, b, "5.00", "MOBILE_WALLET", reference = ref).andReturn().response.status
                        },
                    )
                }.also { start.countDown() }
                .map { it.get() }
        executor.shutdown()
        assertThat(statuses.sorted()).containsExactly(201, 409)
    }

    // ── EGP and the manager-set daily rate ───────────────────────────────────

    @Test
    fun `rate permission is separate and staff cannot set or supply a rate`() {
        val plain = login("office-plain@example.com")
        setRate(plain, "50.0000").andExpect { status { isForbidden() } }
        val cashier = tokenWith("tours-operator.booking:collect-cash")
        setRate(cashier, "50.0000").andExpect { status { isForbidden() } }
        mockMvc
            .get("/api/v1/tours-operator/staff/fx-rate/history") { header("Authorization", "Bearer $cashier") }
            .andExpect { status { isForbidden() } }
        mockMvc.get("/api/v1/tours-operator/staff/fx-rate/today").andExpect { status { isUnauthorized() } }
        mockMvc
            .get("/api/v1/tours-operator/staff/fx-rate/today") { header("Authorization", "Bearer $plain") }
            .andExpect { status { isForbidden() } }
        mockMvc
            .get("/api/v1/tours-operator/staff/fx-rate/today") { header("Authorization", "Bearer $cashier") }
            .andExpect { status { isOk() } }
        val manager = tokenWith("tours-operator.fx-rate:manage")
        // A manager can set a rate but cannot record cash.
        setRate(manager, "0.5").andExpect { status { isBadRequest() } }
        setRate(manager, "1001").andExpect { status { isBadRequest() } }
        setRate(manager, "50.12345").andExpect { status { isBadRequest() } }
        collect(manager, UUID.randomUUID().toString(), "1.00").andExpect { status { isForbidden() } }
    }

    @Test
    fun `without a rate for today EGP is refused with a clear code while EUR still works`() {
        // Earlier tests may have set today's rate: move them to yesterday so this test starts with none.
        dsl.execute("UPDATE wego.tours_operator_fx_rate SET rate_date = rate_date - 1")
        val (_, slotId) = seedTourAndSlot("no-rate", capacity = 20)
        val id = newOffice(slotId)
        val admin = adminToken()
        mockMvc
            .get("/api/v1/tours-operator/staff/fx-rate/today") { header("Authorization", "Bearer $admin") }
            .andExpect { jsonPath("$.rate") { doesNotExist() } }
        collect(admin, id, "1000.00", "INSTAPAY", currency = "EGP", reference = "NO-RATE-1").andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("fx_rate_not_set") }
        }
        mockMvc
            .get("/api/v1/tours-operator/staff/bookings/$id/collections/quote") {
                header("Authorization", "Bearer $admin")
                param("currency", "EGP")
                param("amount", "1000.00")
            }.andExpect { jsonPath("$.status") { value("RATE_MISSING") } }
        collect(admin, id, "10.00", "INSTAPAY", reference = "NO-RATE-EUR").andExpect { status { isCreated() } }
    }

    @Test
    fun `EGP settles at today's manager rate which is stored on the collection and history is kept`() {
        // Earlier tests may have set today's rate: move them to yesterday so this test starts with none.
        dsl.execute("UPDATE wego.tours_operator_fx_rate SET rate_date = rate_date - 1")
        val manager = tokenWith("tours-operator.fx-rate:manage")
        val (_, slotId) = seedTourAndSlot("egp", capacity = 20)
        val id = newOffice(slotId) // €87.50
        val admin = adminToken()

        setRate(manager, "40.0000").andExpect { status { isCreated() } }
        setRate(manager, "50.0000")
            .andExpect {
                status { isCreated() }
                jsonPath("$.egpPerEur") { value("50.0000") }
                jsonPath("$.setByUserId") { exists() }
            }
        mockMvc
            .get("/api/v1/tours-operator/staff/fx-rate/today") { header("Authorization", "Bearer $admin") }
            .andExpect { jsonPath("$.rate.egpPerEur") { value("50.0000") } }
        // Both rows remain as history, newest first.
        mockMvc
            .get("/api/v1/tours-operator/staff/fx-rate/history") { header("Authorization", "Bearer $manager") }
            .andExpect {
                jsonPath("$[0].egpPerEur") { value("50.0000") }
                jsonPath("$[1].egpPerEur") { value("40.0000") }
            }

        // The quote shows the EUR before saving; it writes nothing.
        mockMvc
            .get("/api/v1/tours-operator/staff/bookings/$id/collections/quote") {
                header("Authorization", "Bearer $admin")
                param("currency", "EGP")
                param("amount", "2500.00")
            }.andExpect {
                jsonPath("$.status") { value("OK") }
                jsonPath("$.settledEur.amount") { value("50.00") }
                jsonPath("$.fxRate") { value("50.0000") }
                jsonPath("$.outstanding.amount") { value("87.50") }
            }
        assertThat(
            dsl.fetchCount(TOURS_OPERATOR_OFFICE_COLLECTION, TOURS_OPERATOR_OFFICE_COLLECTION.BOOKING_ID.eq(UUID.fromString(id))),
        ).isZero()

        // Staff cannot supply a rate or the EUR: unknown fields are rejected, nothing is written.
        mockMvc
            .post("/api/v1/tours-operator/staff/bookings/$id/collections") {
                header("Authorization", "Bearer $admin")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"clientRequestId":"${UUID.randomUUID()}","method":"INSTAPAY","currency":"EGP","amount":2500.00,"reference":"EGP-0","fxRate":1,"amountEur":87.50}"""
            }.andExpect { status { isBadRequest() } }
        assertThat(
            dsl.fetchCount(TOURS_OPERATOR_OFFICE_COLLECTION, TOURS_OPERATOR_OFFICE_COLLECTION.BOOKING_ID.eq(UUID.fromString(id))),
        ).isZero()

        // The stored rate is the manager's.
        collect(admin, id, "2500.00", "INSTAPAY", currency = "EGP", reference = "EGP-1").andExpect {
            status { isCreated() }
            jsonPath("$.entry.amount.amount") { value("50.00") }
            jsonPath("$.entry.amount.currencyCode") { value("EUR") }
            jsonPath("$.entry.amountPaid.amount") { value("2500.00") }
            jsonPath("$.entry.amountPaid.currencyCode") { value("EGP") }
            jsonPath("$.entry.fxRate") { value("50.0000") }
            jsonPath("$.officePayment.outstanding.amount") { value("37.50") }
            jsonPath("$.officePayment.state") { value("PARTIALLY_PAID") }
        }

        // A later rate does not rewrite the earlier collection.
        setRate(manager, "60.0000").andExpect { status { isCreated() } }
        mockMvc
            .get("/api/v1/tours-operator/staff/bookings/$id/collections") { header("Authorization", "Bearer $admin") }
            .andExpect { jsonPath("$[0].fxRate") { value("50.0000") } }

        // Too much EGP is refused; the exact EGP value of the balance closes it exactly (37.50 × 60 = 2250.00).
        collect(admin, id, "2251.00", "INSTAPAY", currency = "EGP", reference = "EGP-2").andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("amount_exceeds_outstanding") }
        }
        collect(admin, id, "0.20", "INSTAPAY", currency = "EGP", reference = "EGP-3").andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.error") { value("amount_too_small") }
        }
        collect(admin, id, "2250.00", "INSTAPAY", currency = "EGP", reference = "EGP-4").andExpect {
            status { isCreated() }
            jsonPath("$.entry.amount.amount") { value("37.50") }
            jsonPath("$.officePayment.state") { value("PAID") }
            jsonPath("$.officePayment.outstanding.amount") { value("0.00") }
        }
        assertThat(dsl.fetchCount(TOURS_OPERATOR_PAYMENT, TOURS_OPERATOR_PAYMENT.BOOKING_ID.eq(UUID.fromString(id)))).isZero()
    }

    @Test
    fun `reversing an EGP collection restores the EUR balance it settled and keeps the rate`() {
        // Earlier tests may have set today's rate: move them to yesterday so this test starts with none.
        dsl.execute("UPDATE wego.tours_operator_fx_rate SET rate_date = rate_date - 1")
        setRate(tokenWith("tours-operator.fx-rate:manage"), "48.5000").andExpect { status { isCreated() } }
        val (_, slotId) = seedTourAndSlot("egp-reverse", capacity = 20)
        val id = newOffice(slotId)
        val admin = adminToken()
        val entry =
            entryId(
                collect(admin, id, "1000.00", "MOBILE_WALLET", currency = "EGP", reference = "W-1").andReturn().response.contentAsString,
            )
        // 1000 / 48.5 = 20.6185… → 20.62
        assertThat(JsonPath.read<String>(getBooking(admin, id), "$.officePayment.collected.amount")).isEqualTo("20.62")
        reverse(admin, id, entry).andExpect {
            status { isCreated() }
            jsonPath("$.entry.amount.amount") { value("20.62") }
            jsonPath("$.entry.amountPaid.currencyCode") { value("EGP") }
            jsonPath("$.entry.fxRate") { value("48.5000") }
            jsonPath("$.entry.reference") { doesNotExist() }
            jsonPath("$.officePayment.state") { value("UNPAID") }
        }
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("wego_office_booking_test")
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
}
