package com.wego.toursoperator

import com.jayway.jsonpath.JsonPath
import com.wego.generated.jooq.tables.ToursOperatorBooking.TOURS_OPERATOR_BOOKING
import com.wego.generated.jooq.tables.ToursOperatorOnlineRequestAudit.TOURS_OPERATOR_ONLINE_REQUEST_AUDIT
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
import com.wego.toursoperator.application.BookingRepository
import com.wego.toursoperator.application.CreateBookingService
import com.wego.toursoperator.application.OnlineRequestRepository
import com.wego.toursoperator.application.OnlineRequestService
import com.wego.toursoperator.application.TourRepository
import com.wego.toursoperator.application.TourSlotRepository
import com.wego.toursoperator.application.TransactionRunner
import com.wego.toursoperator.domain.Money
import com.wego.toursoperator.domain.OnlineRequest
import com.wego.toursoperator.domain.OnlineRequestStatus
import com.wego.toursoperator.domain.TourSlotId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jooq.DSLContext
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
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = ["tours-operator.booking-mode=ENQUIRY_ONLY", "tours-operator.paymob.mock-enabled=false"])
@AutoConfigureMockMvc
class ToursOperatorOnlineRequestHttpTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18.4-alpine").withDatabaseName("online_request_test")

        @DynamicPropertySource @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.enabled") { true }
        }
    }

    @Autowired private lateinit var mvc: MockMvc

    @Autowired private lateinit var dsl: DSLContext

    @Autowired private lateinit var users: UserRepository

    @Autowired private lateinit var hasher: PasswordHasher

    @Autowired private lateinit var repository: OnlineRequestRepository

    @Autowired private lateinit var tours: TourRepository

    @Autowired private lateinit var slots: TourSlotRepository

    @Autowired private lateinit var bookings: BookingRepository

    @Autowired private lateinit var createBooking: CreateBookingService

    @Autowired private lateinit var transactions: TransactionRunner

    @Autowired private lateinit var clock: Clock
    private val publicPath = "/api/v1/tours-operator/booking-requests"
    private val staffPath = "/api/v1/tours-operator/staff/booking-requests"
    private val day get() = LocalDate.now(ZoneId.of("Africa/Cairo")).plusDays(7)

    private fun user(
        admin: Boolean = true,
        permissions: Set<String>? = null,
    ): Pair<UUID, String> {
        val id = UserId.generate()
        val email = "request-${id.value}@example.com"
        val password = "online-request-test-password-2026"
        val roles =
            if (permissions != null) {
                val code = "request-${id.value}"
                dsl.execute("INSERT INTO wego.identity_role(code,description) VALUES (?, 'Request permission test')", code)
                permissions.forEach { permission ->
                    dsl.execute("INSERT INTO wego.identity_role_permission(role_code,permission_code) VALUES (?,?)", code, permission)
                }
                setOf(RoleCode.of(code))
            } else if (admin) {
                setOf(RoleCode.of("platform-admin"))
            } else {
                emptySet()
            }
        users.save(
            User(
                id,
                EmailAddress.of(email),
                hasher.hash(password),
                UserStatus.ACTIVE,
                roles,
                Instant.now(),
                0,
                null,
            ),
        )
        val response =
            mvc
                .post("/api/v1/identity/login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"$email","password":"$password"}"""
                }.andReturn()
                .response
        assertThat(response.status).isEqualTo(200)
        return id.value to JsonPath.read(response.contentAsString, "$.token")
    }

    private fun seed(
        capacity: Int = 5,
        unit: Boolean = false,
    ): Pair<UUID, UUID> {
        val tour = UUID.randomUUID()
        val slot = UUID.randomUUID()
        dsl
            .insertInto(TOURS_OPERATOR_TOUR)
            .set(TOURS_OPERATOR_TOUR.ID, tour)
            .set(TOURS_OPERATOR_TOUR.SLUG, "request-${tour.toString().take(8)}")
            .set(TOURS_OPERATOR_TOUR.CATEGORY, "DESERT")
            .set(TOURS_OPERATOR_TOUR.DURATION_TEXT, "4 hours")
            .set(TOURS_OPERATOR_TOUR.PRICE_ADULT_CENTS, 3500L)
            .set(TOURS_OPERATOR_TOUR.PRICE_CHILD_CENTS, 1750L)
            .set(TOURS_OPERATOR_TOUR.CAPACITY, capacity)
            .set(TOURS_OPERATOR_TOUR.AVAILABLE_TIME_SLOTS, "MORNING")
            .set(TOURS_OPERATOR_TOUR.SORT_ORDER, 0)
            .set(TOURS_OPERATOR_TOUR.IS_ACTIVE, true)
            .set(TOURS_OPERATOR_TOUR.CREATED_AT, OffsetDateTime.now())
            .execute()
        dsl
            .insertInto(TOURS_OPERATOR_TOUR_SLOT)
            .set(TOURS_OPERATOR_TOUR_SLOT.ID, slot)
            .set(TOURS_OPERATOR_TOUR_SLOT.TOUR_ID, tour)
            .set(TOURS_OPERATOR_TOUR_SLOT.DATE, day)
            .set(TOURS_OPERATOR_TOUR_SLOT.TIME_SLOT, "MORNING")
            .set(TOURS_OPERATOR_TOUR_SLOT.CAPACITY, capacity)
            .set(TOURS_OPERATOR_TOUR_SLOT.BOOKED_COUNT, 0)
            .set(TOURS_OPERATOR_TOUR_SLOT.IS_BLOCKED, false)
            .set(TOURS_OPERATOR_TOUR_SLOT.CREATED_AT, OffsetDateTime.now())
            .execute()
        if (unit) {
            dsl.execute("UPDATE wego.tours_operator_tour SET price_basis='PER_UNIT', price_child_cents=NULL WHERE id=?", tour)
            dsl.execute(
                "INSERT INTO wego.tours_operator_tour_price_option(tour_id,code,label_en,seats_per_unit,price_cents,sort_order) VALUES (?,'buggy','Buggy',2,3000,0)",
                tour,
            )
        }
        return tour to slot
    }

    private fun body(
        id: UUID,
        tour: UUID,
        name: String = "Test Guest",
        date: LocalDate = day,
        extra: String = "",
    ) = """{
        "clientRequestId":"$id","tourId":"$tour","preferredDate":"$date","adultsCount":1,"childrenCount":0,
        "customer":{"fullName":"$name","phone":"+201000000001","nationality":"EG","email":"guest@example.com"},
        "hotelName":"Test Hotel","locale":"ar"$extra
    }"""

    private fun submit(body: String) =
        mvc
            .post(publicPath) {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andReturn()
            .response

    private fun convert(
        id: UUID,
        slot: UUID,
        token: String,
        cents: Long = 3500,
        revision: Int = 0,
    ) = mvc
        .post("$staffPath/$id/convert") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"expectedRevision":$revision,"slotId":"$slot","expectedTotalCents":$cents}"""
        }.andReturn()
        .response

    private fun seats(slot: UUID) = dsl.fetchOne(TOURS_OPERATOR_TOUR_SLOT, TOURS_OPERATOR_TOUR_SLOT.ID.eq(slot))!!.bookedCount

    private fun countBookings() = dsl.fetchCount(TOURS_OPERATOR_BOOKING)

    private fun countAudit() = dsl.fetchCount(TOURS_OPERATOR_ONLINE_REQUEST_AUDIT)

    private fun countOutbox() =
        dsl.fetchCount(
            org.jooq.impl.DSL
                .table("wego.integration_outbox"),
        )

    private fun <T> parallel(tasks: List<() -> T>): List<T> {
        val pool = Executors.newFixedThreadPool(tasks.size)
        val start = CountDownLatch(1)
        try {
            val futures =
                tasks.map { task ->
                    pool.submit(
                        Callable {
                            start.await(10, TimeUnit.SECONDS)
                            task()
                        },
                    )
                }
            start.countDown()
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test fun `public saves only intent and returns reference without PII`() {
        val (tour, slot) = seed()
        val id = UUID.randomUUID()
        val before = countBookings()
        val payments = dsl.fetchCount(TOURS_OPERATOR_PAYMENT)
        val response = submit(body(id, tour))
        assertThat(response.status).isEqualTo(201)
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store")
        assertThat(response.contentAsString).matches("""\{"reference":"STQ-[A-F0-9]{32}"}""")
        assertThat(seats(slot)).isZero()
        assertThat(countBookings()).isEqualTo(before)
        assertThat(dsl.fetchCount(TOURS_OPERATOR_PAYMENT)).isEqualTo(payments)
        assertThat(
            repository
                .find(id)!!
                .estimatedTotal.amount
                .toPlainString(),
        ).isEqualTo("35.00")
        assertThat(repository.history(id)).hasSize(1)
    }

    @Test fun `concurrent identical submissions save one request and one audit`() {
        val (tour, _) = seed()
        val id = UUID.randomUUID()
        val payload = body(id, tour)
        val before = countAudit()
        val responses = parallel(List(8) { { submit(payload) } })
        assertThat(responses.map { it.status }.sorted()).containsExactly(200, 200, 200, 200, 200, 200, 200, 201)
        assertThat(responses.map { it.contentAsString }.distinct()).hasSize(1)
        assertThat(countAudit()).isEqualTo(before + 1)
    }

    @Test fun `concurrent different payloads with same key cannot replace customer`() {
        val (tour, _) = seed()
        val id = UUID.randomUUID()
        val responses = parallel(listOf({ submit(body(id, tour, "Guest A")) }, { submit(body(id, tour, "Guest B")) }))
        assertThat(responses.map { it.status }.sorted()).containsExactly(201, 409)
        assertThat(
            repository
                .find(id)!!
                .details.customer.fullName,
        ).isIn("Guest A", "Guest B")
    }

    @Test fun `replay after deactivation returns same acknowledgement without new row`() {
        val (tour, _) = seed()
        val id = UUID.randomUUID()
        val payload = body(id, tour)
        val first = submit(payload)
        dsl
            .update(TOURS_OPERATOR_TOUR)
            .set(TOURS_OPERATOR_TOUR.IS_ACTIVE, false)
            .where(TOURS_OPERATOR_TOUR.ID.eq(tour))
            .execute()
        assertThat(submit(payload).status).isEqualTo(200)
        assertThat(submit(payload).contentAsString).isEqualTo(first.contentAsString)
        assertThat(submit(body(UUID.randomUUID(), tour)).status).isEqualTo(422)
    }

    @Test fun `invalid dates phone guest counts honeypot and unit selection rejected`() {
        val (tour, slot) = seed()
        assertThat(submit(body(UUID.randomUUID(), tour, date = day.minusDays(20))).status).isEqualTo(422)
        assertThat(submit(body(UUID.randomUUID(), tour, date = day.plusDays(400))).status).isEqualTo(422)
        assertThat(submit(body(UUID.randomUUID(), tour).replace("+201000000001", "abc")).status).isEqualTo(422)
        assertThat(submit(body(UUID.randomUUID(), tour).replace("\"adultsCount\":1", "\"adultsCount\":0")).status).isEqualTo(400)
        assertThat(submit(body(UUID.randomUUID(), tour, extra = ",\"website\":\"spam\"")).status).isEqualTo(400)
        assertThat(submit(body(UUID.randomUUID(), tour, extra = ",\"priceOptionCode\":\"invented\",\"unitCount\":1")).status).isEqualTo(422)
        assertThat(seats(slot)).isZero()
    }

    @Test fun `staff list detail count history and writes deny anonymous and unprivileged`() {
        val (tour, slot) = seed()
        val id = UUID.randomUUID()
        submit(body(id, tour))
        val (_, plain) = user(false)
        for (path in listOf(staffPath, "$staffPath/$id", "$staffPath/open-count", "$staffPath/$id/history")) {
            assertThat(
                mvc
                    .get(path)
                    .andReturn()
                    .response.status,
            ).isEqualTo(401)
            assertThat(
                mvc
                    .get(path) { header("Authorization", "Bearer $plain") }
                    .andReturn()
                    .response.status,
            ).isEqualTo(403)
        }
        assertThat(convert(id, slot, plain).status).isEqualTo(403)
        assertThat(
            mvc
                .post("$staffPath/$id/follow-up") {
                    header("Authorization", "Bearer $plain")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """{"expectedRevision":0,"status":"CLOSED"}"""
                }.andReturn()
                .response.status,
        ).isEqualTo(403)
        // Only the exact collection POST is public, never /booking-requests/{id}.
        assertThat(
            mvc
                .get("$publicPath/$id")
                .andReturn()
                .response.status,
        ).isEqualTo(401)
        assertThat(
            mvc
                .get(publicPath)
                .andReturn()
                .response.status,
        ).isIn(405, 403)
    }

    @Test fun `minimal request permissions include current catalog context without tour administration`() {
        val (tour, slot) = seed()
        val id = UUID.randomUUID()
        assertThat(submit(body(id, tour)).status).isEqualTo(201)
        val view = "tours-operator.booking:view"
        val create = "tours-operator.booking:create-office"
        val (_, viewer) = user(permissions = setOf(view))
        val (_, creator) = user(permissions = setOf(create))
        val (_, both) = user(permissions = setOf(view, create))
        for (token in listOf(viewer, both)) {
            val response = mvc.get("$staffPath/$id") { header("Authorization", "Bearer $token") }.andReturn().response
            assertThat(response.status).isEqualTo(200)
            assertThat(JsonPath.read<String>(response.contentAsString, "$.tour.id")).isEqualTo(tour.toString())
            assertThat(JsonPath.read<String>(response.contentAsString, "$.tour.priceAdult.amount")).isEqualTo("35.00")
            assertThat(
                mvc
                    .get("$staffPath/$id/history") { header("Authorization", "Bearer $token") }
                    .andReturn()
                    .response.status,
            ).isEqualTo(200)
            assertThat(
                mvc
                    .get("/api/v1/tours-operator/staff/tours/$tour") {
                        header("Authorization", "Bearer $token")
                    }.andReturn()
                    .response.status,
            ).isEqualTo(403)
            assertThat(
                mvc
                    .get("/api/v1/tours-operator/tours/$tour/slots/by-date?date=$day") {
                        header("Authorization", "Bearer $token")
                    }.andReturn()
                    .response.status,
            ).isEqualTo(200)
        }
        assertThat(convert(id, slot, viewer).status).isEqualTo(403)
        assertThat(convert(id, slot, creator).status).isEqualTo(403)
        assertThat(
            mvc
                .get(staffPath) { header("Authorization", "Bearer $creator") }
                .andReturn()
                .response.status,
        ).isEqualTo(403)
        assertThat(convert(id, slot, both).status).isEqualTo(200)
    }

    @Test fun `unknown fields malformed payloads and negative revision fail safely`() {
        val (tour, slot) = seed()
        val id = UUID.randomUUID()
        assertThat(submit(body(id, tour, extra = ",\"inventedPrice\":1")).status).isEqualTo(400)
        assertThat(repository.find(id)).isNull()
        val response = submit(body(UUID.randomUUID(), tour).replace("+201000000001", "sensitive@example.com"))
        assertThat(response.status).isEqualTo(422)
        assertThat(response.contentAsString).doesNotContain("sensitive@example.com")
        assertThat(submit(body(UUID.randomUUID(), tour, name = "Guest\\u0000Name")).status).isEqualTo(422)
        assertThat(submit(body(UUID.randomUUID(), tour).replace("Test Hotel", "Test\\u0000Hotel")).status).isEqualTo(422)
        assertThat(submit(body(id, tour)).status).isEqualTo(201)
        val (_, token) = user()
        assertThat(convert(id, slot, token, revision = -1).status).isEqualTo(400)
        assertThat(seats(slot)).isZero()
    }

    @Test fun `two staff confirming same request reserve once and create one unpaid office booking`() {
        val (tour, slot) = seed()
        val id = UUID.randomUUID()
        submit(body(id, tour))
        val (_, a) = user()
        val (_, b) = user()
        val before = countBookings()
        val results = parallel(listOf({ convert(id, slot, a) }, { convert(id, slot, b) }))
        assertThat(results.map { it.status }).containsOnly(200)
        assertThat(results.map { JsonPath.read<String>(it.contentAsString, "$.bookingId") }.distinct()).hasSize(1)
        assertThat(countBookings()).isEqualTo(before + 1)
        assertThat(seats(slot)).isEqualTo(1)
        val booking = bookings.findById(repository.find(id)!!.bookingId!!)!!
        assertThat(booking.channel.name).isEqualTo("OFFICE")
        assertThat(booking.status.name).isEqualTo("CONFIRMED")
        assertThat(dsl.fetchCount(TOURS_OPERATOR_PAYMENT, TOURS_OPERATOR_PAYMENT.BOOKING_ID.eq(booking.id.value))).isZero()
        assertThat(repository.history(id).map { it.status }).containsExactly(OnlineRequestStatus.NEW, OnlineRequestStatus.CONVERTED)
    }

    @Test fun `two requests racing final seat cannot overbook`() {
        val (tour, slot) = seed(1)
        val a = UUID.randomUUID()
        val b = UUID.randomUUID()
        submit(body(a, tour))
        submit(body(b, tour))
        val (_, token) = user()
        val responses = parallel(listOf({ convert(a, slot, token) }, { convert(b, slot, token) }))
        assertThat(responses.map { it.status }.sorted()).containsExactly(200, 409)
        assertThat(seats(slot)).isEqualTo(1)
        assertThat(
            listOf(repository.find(a)!!.status, repository.find(b)!!.status),
        ).containsExactlyInAnyOrder(OnlineRequestStatus.NEW, OnlineRequestStatus.CONVERTED)
    }

    @Test fun `price mismatch rolls back booking seats audit outbox and request state`() {
        val (tour, slot) = seed()
        val id = UUID.randomUUID()
        submit(body(id, tour))
        val (_, token) = user()
        val before = listOf(countBookings(), countAudit(), countOutbox())
        val response = convert(id, slot, token, 3400)
        assertThat(response.status).isEqualTo(409)
        assertThat(response.contentAsString).contains("price_changed")
        assertThat(listOf(countBookings(), countAudit(), countOutbox())).isEqualTo(before)
        assertThat(seats(slot)).isZero()
        assertThat(repository.find(id)!!.status).isEqualTo(OnlineRequestStatus.NEW)
        assertThat(convert(id, slot, token).status).isEqualTo(200)
    }

    @Test fun `audit failure rolls back the entire conversion`() {
        val (tour, slot) = seed()
        val id = UUID.randomUUID()
        submit(body(id, tour))
        val (actor, _) = user()
        val before = listOf(countBookings(), countAudit(), countOutbox())
        val failing =
            object : OnlineRequestRepository by repository {
                override fun audit(
                    request: OnlineRequest,
                    actorUserId: UUID?,
                ) {
                    if (request.status == OnlineRequestStatus.CONVERTED) error("simulated audit failure")
                    repository.audit(request, actorUserId)
                }
            }
        val service = OnlineRequestService(failing, tours, slots, bookings, createBooking, transactions, clock)
        assertThatThrownBy {
            service.convert(id, 0, TourSlotId(slot), Money.fromCents(3500), actor, null)
        }.hasMessageContaining("simulated audit failure")
        assertThat(listOf(countBookings(), countAudit(), countOutbox())).isEqualTo(before)
        assertThat(seats(slot)).isZero()
        assertThat(repository.find(id)!!.bookingId).isNull()
    }

    @Test fun `stale follow up or confirmation cannot overwrite newer staff action`() {
        val (tour, slot) = seed()
        val id = UUID.randomUUID()
        submit(body(id, tour))
        val (_, token) = user()

        fun follow(
            revision: Int,
            state: String,
        ) = mvc
            .post("$staffPath/$id/follow-up") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"expectedRevision":$revision,"status":"$state"}"""
            }.andReturn()
            .response
        assertThat(follow(0, "IN_PROGRESS").status).isEqualTo(200)
        assertThat(follow(0, "IN_PROGRESS").status).isEqualTo(200)
        assertThat(follow(0, "CLOSED").status).isEqualTo(409)
        assertThat(convert(id, slot, token).status).isEqualTo(409)
        assertThat(follow(1, "CLOSED").status).isEqualTo(200)
        assertThat(convert(id, slot, token, revision = 2).status).isEqualTo(409)
        assertThat(seats(slot)).isZero()
    }

    @Test fun `wrong tour blocked past and different replay departures cannot convert`() {
        val (tour, slot) = seed()
        val (_, wrong) = seed()
        val id = UUID.randomUUID()
        submit(body(id, tour))
        val (_, token) = user()
        assertThat(convert(id, wrong, token).status).isEqualTo(409)
        dsl
            .update(
                TOURS_OPERATOR_TOUR_SLOT,
            ).set(TOURS_OPERATOR_TOUR_SLOT.IS_BLOCKED, true)
            .where(TOURS_OPERATOR_TOUR_SLOT.ID.eq(slot))
            .execute()
        assertThat(convert(id, slot, token).status).isEqualTo(409)
        dsl
            .update(
                TOURS_OPERATOR_TOUR_SLOT,
            ).set(
                TOURS_OPERATOR_TOUR_SLOT.IS_BLOCKED,
                false,
            ).set(TOURS_OPERATOR_TOUR_SLOT.DATE, day.minusDays(20))
            .where(TOURS_OPERATOR_TOUR_SLOT.ID.eq(slot))
            .execute()
        assertThat(convert(id, slot, token).status).isEqualTo(409)
        dsl
            .update(TOURS_OPERATOR_TOUR_SLOT)
            .set(TOURS_OPERATOR_TOUR_SLOT.DATE, day)
            .where(TOURS_OPERATOR_TOUR_SLOT.ID.eq(slot))
            .execute()
        assertThat(convert(id, slot, token).status).isEqualTo(200)
        assertThat(convert(id, wrong, token).status).isEqualTo(409)
        assertThat(convert(id, slot, token, 3400).status).isEqualTo(409)
        assertThat(seats(slot)).isEqualTo(1)
    }

    @Test fun `per unit requests price units and reserve full purchased seats only on confirmation`() {
        val (tour, slot) = seed(4, true)
        val id = UUID.randomUUID()
        val payload = body(id, tour, extra = ",\"priceOptionCode\":\"buggy\",\"unitCount\":1")
        assertThat(submit(payload).status).isEqualTo(201)
        assertThat(
            repository
                .find(id)!!
                .estimatedTotal.amount
                .toPlainString(),
        ).isEqualTo("30.00")
        assertThat(seats(slot)).isZero()
        val (_, token) = user()
        assertThat(convert(id, slot, token, 3000).status).isEqualTo(200)
        assertThat(seats(slot)).isEqualTo(2)
    }
}
