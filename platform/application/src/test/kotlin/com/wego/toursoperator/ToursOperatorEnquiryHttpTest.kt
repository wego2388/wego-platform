package com.wego.toursoperator

import com.wego.generated.jooq.tables.ToursOperatorBooking.TOURS_OPERATOR_BOOKING
import com.wego.generated.jooq.tables.ToursOperatorPayment.TOURS_OPERATOR_PAYMENT
import com.wego.generated.jooq.tables.ToursOperatorTourSlot.TOURS_OPERATOR_TOUR_SLOT
import com.jayway.jsonpath.JsonPath
import com.wego.generated.jooq.tables.ToursOperatorTour.TOURS_OPERATOR_TOUR
import com.wego.identity.application.PasswordHasher
import com.wego.identity.application.UserRepository
import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.User
import com.wego.identity.domain.UserId
import com.wego.identity.domain.UserStatus
import com.wego.toursoperator.application.PaymobClient
import com.wego.toursoperator.infrastructure.DisabledPaymobClient
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
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
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/** Real DB + Spring composition, no Paymob mocks or external network. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = ["tours-operator.booking-mode=ENQUIRY_ONLY", "tours-operator.paymob.mock-enabled=false"])
@AutoConfigureMockMvc
class ToursOperatorEnquiryHttpTest {
    companion object {
        @Container @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:18.4-alpine").withDatabaseName("safari_enquiry_test")

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

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var passwordHasher: PasswordHasher

    @Autowired
    @Qualifier("stoPaymobClient")
    private lateinit var client: PaymobClient

    @Test
    fun `fresh enquiry installation boots without keys and reports effective capability`() {
        assertThat(client).isInstanceOf(DisabledPaymobClient::class.java)
        mvc.get("/api/v1/tours-operator/sales-status").andExpect {
            status { isOk() }
            jsonPath("$.bookingMode") { value("ENQUIRY_ONLY") }
            jsonPath("$.bookingsOpen") { value(false) }
            jsonPath("$.paymentsOpen") { value(false) }
            jsonPath("$.reason") { doesNotExist() }
            jsonPath("$.updatedByUserId") { doesNotExist() }
        }
    }

    @Test
    fun `stale checkout HTTP cannot create bookings payments or seat holds`() {
        val bookings = dsl.fetchCount(TOURS_OPERATOR_BOOKING)
        val payments = dsl.fetchCount(TOURS_OPERATOR_PAYMENT)
        val reserved = dsl.select(TOURS_OPERATOR_TOUR_SLOT.BOOKED_COUNT.sum()).from(TOURS_OPERATOR_TOUR_SLOT).fetchOne(0, Int::class.java)
        mvc
            .post("/api/v1/tours-operator/bookings") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"slotId":"${UUID.randomUUID()}","adultsCount":1,"childrenCount":0,"customer":{"fullName":"Test Guest","phone":"+201000000000","nationality":"EG"},"hotelName":"Test hotel","locale":"en"}"""
            }.andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.error") { value("online_booking_unavailable") }
            }
        mvc
            .post("/api/v1/tours-operator/bookings/${UUID.randomUUID()}/pay") {
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.error") { value("online_payment_unavailable") }
            }
        assertThat(dsl.fetchCount(TOURS_OPERATOR_BOOKING)).isEqualTo(bookings)
        assertThat(dsl.fetchCount(TOURS_OPERATOR_PAYMENT)).isEqualTo(payments)
        assertThat(
            dsl.select(TOURS_OPERATOR_TOUR_SLOT.BOOKED_COUNT.sum()).from(TOURS_OPERATOR_TOUR_SLOT).fetchOne(0, Int::class.java),
        ).isEqualTo(reserved)
    }

    @Test
    fun `staff office bookings still work in enquiry-only mode while online booking stays refused`() {
        val email = "enquiry-office-admin@example.com"
        val password = "a-very-long-enquiry-password-123"
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
        val token =
            JsonPath.read<String>(
                mvc
                    .post("/api/v1/identity/login") {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"email":"$email","password":"$password"}"""
                    }.andReturn()
                    .response.contentAsString,
                "$.token",
            )
        val tourId = UUID.randomUUID()
        val slotId = UUID.randomUUID()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        dsl
            .insertInto(TOURS_OPERATOR_TOUR)
            .set(TOURS_OPERATOR_TOUR.ID, tourId)
            .set(TOURS_OPERATOR_TOUR.SLUG, "enquiry-office-tour")
            .set(TOURS_OPERATOR_TOUR.CATEGORY, "DESERT")
            .set(TOURS_OPERATOR_TOUR.DURATION_TEXT, "4 hours")
            .set(TOURS_OPERATOR_TOUR.PRICE_ADULT_CENTS, 3500L)
            .set(TOURS_OPERATOR_TOUR.CAPACITY, 10)
            .set(TOURS_OPERATOR_TOUR.AVAILABLE_TIME_SLOTS, "MORNING")
            .set(TOURS_OPERATOR_TOUR.SORT_ORDER, 1)
            .set(TOURS_OPERATOR_TOUR.IS_ACTIVE, true)
            .set(TOURS_OPERATOR_TOUR.CREATED_AT, now)
            .execute()
        dsl
            .insertInto(TOURS_OPERATOR_TOUR_SLOT)
            .set(TOURS_OPERATOR_TOUR_SLOT.ID, slotId)
            .set(TOURS_OPERATOR_TOUR_SLOT.TOUR_ID, tourId)
            .set(TOURS_OPERATOR_TOUR_SLOT.DATE, java.time.LocalDate.of(2027, 6, 15))
            .set(TOURS_OPERATOR_TOUR_SLOT.TIME_SLOT, "MORNING")
            .set(TOURS_OPERATOR_TOUR_SLOT.CAPACITY, 10)
            .set(TOURS_OPERATOR_TOUR_SLOT.BOOKED_COUNT, 0)
            .set(TOURS_OPERATOR_TOUR_SLOT.IS_BLOCKED, false)
            .set(TOURS_OPERATOR_TOUR_SLOT.CREATED_AT, now)
            .execute()
        val customer =
            "\"customer\":{\"fullName\":\"Test Guest\",\"phone\":\"+201000000000\",\"nationality\":\"EG\"},\"hotelName\":\"Test hotel\",\"locale\":\"en\""
        mvc
            .post("/api/v1/tours-operator/bookings") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"slotId":"$slotId","adultsCount":2,"childrenCount":0,$customer}"""
            }.andExpect { status { isServiceUnavailable() } }
        mvc
            .post("/api/v1/tours-operator/staff/bookings") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"clientRequestId":"${UUID.randomUUID()}","slotId":"$slotId","adultsCount":2,"childrenCount":0,$customer}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.channel") { value("OFFICE") }
                jsonPath("$.officePayment.state") { value("UNPAID") }
            }
        // Online payment stays unavailable; the office books and records payment by hand instead.
        assertThat(dsl.fetchCount(TOURS_OPERATOR_PAYMENT)).isZero()
    }

    @Test
    fun `disabled callback is retryable not a fake successful acknowledgement`() {
        mvc
            .post("/api/v1/tours-operator/payments/paymob-callback?hmac=test-invalid") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"obj":{"id":1,"order":{"id":1},"amount_cents":100,"currency":"EUR","integration_id":"123","owner":"456","success":true}}"""
            }.andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.error") { value("payment_provider_unavailable") }
            }
        assertThat(dsl.fetchCount(TOURS_OPERATOR_PAYMENT)).isZero()
    }
}
