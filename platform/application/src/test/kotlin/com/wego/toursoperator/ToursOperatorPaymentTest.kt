package com.wego.toursoperator

import com.wego.generated.jooq.tables.ToursOperatorBooking.TOURS_OPERATOR_BOOKING
import com.wego.generated.jooq.tables.ToursOperatorPayment.TOURS_OPERATOR_PAYMENT
import com.wego.generated.jooq.tables.ToursOperatorTour.TOURS_OPERATOR_TOUR
import com.wego.generated.jooq.tables.ToursOperatorTourSlot.TOURS_OPERATOR_TOUR_SLOT
import com.wego.toursoperator.application.PaymobClient
import com.wego.toursoperator.application.PaymobOrderItem
import com.wego.toursoperator.application.PaymobOrderResult
import com.wego.toursoperator.application.PaymobRefundResult
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyList
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.anyMap
import org.mockito.Mockito.anyString
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Integration tests for the Payment flow (WEGO-016-D).
 *
 * PaymobClient is mocked — no real HTTP calls to Paymob.
 * Verifies:
 *   D1 — Initiate payment: PENDING record created, checkout URL returned.
 *   D2 — Idempotent initiate: second call returns 200 with same URL.
 *   D3 — Webhook success: booking CONFIRMED, payment PAID.
 *   D4 — Duplicate webhook: idempotent (already_processed).
 *   D5 — Invalid HMAC: rejected 400.
 *   D6 — Amount mismatch: rejected 422.
 *   D7 — Payment status: PENDING before webhook, PAID after.
 *   D8 — Non-existent booking: 404.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.annotation.DirtiesContext
class ToursOperatorPaymentTest {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("wego_test_pay")
                .withUsername("wego_test")
                .withPassword("wego_test")

        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.enabled") { true }
        }
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var dsl: DSLContext

    // Replace the real PaymobHttpClient with a Mockito mock in the Spring context.
    @MockitoBean(name = "stoPaymobClient")
    private lateinit var paymobClient: PaymobClient

    private lateinit var tourId: UUID
    private lateinit var slotId: UUID

    @BeforeEach
    fun setup() {
        // Configure stub behaviours using plain Mockito
        @Suppress("UNCHECKED_CAST")
        `when`(paymobClient.createOrder(anyString(), anyLong(), anyString(), anyList()))
            .thenReturn(PaymobOrderResult.Success("ORDER-TEST-123"))
        `when`(paymobClient.buildCheckoutUrl(anyString()))
            .thenAnswer { inv -> "https://accept.paymob.com/test/checkout/${inv.arguments[0]}" }
        // Delegate HMAC check to a simple lambda — avoids Mockito matcher nullability issues
        `when`(paymobClient.verifyWebhookSignature(anyMap(), anyString()))
            .thenAnswer { inv -> inv.arguments[1] == "valid-hmac" }
        `when`(paymobClient.refund(anyString(), anyLong()))
            .thenReturn(PaymobRefundResult.Success)

        // Clean tables
        dsl.deleteFrom(TOURS_OPERATOR_PAYMENT).execute()
        dsl.deleteFrom(TOURS_OPERATOR_BOOKING).execute()
        dsl.deleteFrom(TOURS_OPERATOR_TOUR_SLOT).execute()
        dsl.deleteFrom(TOURS_OPERATOR_TOUR).execute()

        // Seed tour + slot
        tourId = UUID.randomUUID()
        dsl.insertInto(TOURS_OPERATOR_TOUR)
            .set(TOURS_OPERATOR_TOUR.ID, tourId)
            .set(TOURS_OPERATOR_TOUR.SLUG, "test-desert-tour-pay")
            .set(TOURS_OPERATOR_TOUR.CATEGORY, "DESERT")
            .set(TOURS_OPERATOR_TOUR.DURATION_TEXT, "Full day")
            .set(TOURS_OPERATOR_TOUR.PRICE_ADULT_CENTS, 4500L)
            .set(TOURS_OPERATOR_TOUR.CAPACITY, 20)
            .set(TOURS_OPERATOR_TOUR.AVAILABLE_TIME_SLOTS, "MORNING")
            .set(TOURS_OPERATOR_TOUR.SORT_ORDER, 1)
            .set(TOURS_OPERATOR_TOUR.IS_ACTIVE, true)
            .set(TOURS_OPERATOR_TOUR.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .execute()

        slotId = UUID.randomUUID()
        dsl.insertInto(TOURS_OPERATOR_TOUR_SLOT)
            .set(TOURS_OPERATOR_TOUR_SLOT.ID, slotId)
            .set(TOURS_OPERATOR_TOUR_SLOT.TOUR_ID, tourId)
            .set(TOURS_OPERATOR_TOUR_SLOT.DATE, LocalDate.now().plusDays(5))
            .set(TOURS_OPERATOR_TOUR_SLOT.TIME_SLOT, "MORNING")
            .set(TOURS_OPERATOR_TOUR_SLOT.CAPACITY, 10)
            .set(TOURS_OPERATOR_TOUR_SLOT.BOOKED_COUNT, 0)
            .set(TOURS_OPERATOR_TOUR_SLOT.IS_BLOCKED, false)
            .set(TOURS_OPERATOR_TOUR_SLOT.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .execute()
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun createBooking(): String {
        val result = mockMvc.post("/api/v1/tours-operator/bookings") {
            contentType = MediaType.APPLICATION_JSON
            content = """
                {"slotId":"$slotId","adultsCount":1,"childrenCount":0,
                 "customer":{"fullName":"Test User","phone":"+20100000001",
                             "nationality":"EG","email":null},
                 "hotelName":"Test Hotel","hotelRoom":null,
                 "specialRequests":null,"locale":"en"}
            """.trimIndent()
        }.andReturn()
        assertThat(result.response.status).isEqualTo(201)
        val idPattern = Regex(""""id"\s*:\s*"([0-9a-f\-]{36})"""")
        return idPattern.find(result.response.contentAsString)?.groupValues?.get(1)
            ?: error("Could not parse booking id from: ${result.response.contentAsString}")
    }

    private fun webhookBody(
        orderId: String, transactionId: String,
        success: String, pending: String,
        isRefund: String, amountCents: Long,
    ) = """{"obj":{"id":"$transactionId","success":$success,"pending":$pending,
              "is_refunded":$isRefund,"amount_cents":$amountCents,"currency":"EUR",
              "order":{"id":"$orderId"},"source_data":{},"data":{}}}"""

    // ── D1: Initiate creates PENDING record ────────────────────────────────

    @Test
    fun `D1 - initiating payment creates PENDING record and returns checkout URL`() {
        val bookingId = createBooking()

        mockMvc.post("/api/v1/tours-operator/bookings/$bookingId/pay") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isCreated() }
            jsonPath("$.checkoutUrl") { value("https://accept.paymob.com/test/checkout/ORDER-TEST-123") }
            jsonPath("$.status") { value("PENDING") }
            jsonPath("$.amountEur") { value("45.00") }
            jsonPath("$.currencyCode") { value("EUR") }
        }

        val payment = dsl.selectFrom(TOURS_OPERATOR_PAYMENT)
            .where(TOURS_OPERATOR_PAYMENT.BOOKING_ID.eq(UUID.fromString(bookingId)))
            .fetchOne()
        assertThat(payment).isNotNull
        assertThat(payment!!.status).isEqualTo("PENDING")
        assertThat(payment.paymobOrderId).isEqualTo("ORDER-TEST-123")
    }

    // ── D2: Idempotent initiate ────────────────────────────────────────────

    @Test
    fun `D2 - second pay call is idempotent and returns existing checkout URL`() {
        val bookingId = createBooking()

        mockMvc.post("/api/v1/tours-operator/bookings/$bookingId/pay") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect { status { isCreated() } }

        mockMvc.post("/api/v1/tours-operator/bookings/$bookingId/pay") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.checkoutUrl") { value("https://accept.paymob.com/test/checkout/ORDER-TEST-123") }
        }

        val count = dsl.selectCount().from(TOURS_OPERATOR_PAYMENT)
            .where(TOURS_OPERATOR_PAYMENT.BOOKING_ID.eq(UUID.fromString(bookingId)))
            .fetchOne(0, Int::class.java)
        assertThat(count).isEqualTo(1)
    }

    // ── D3: Successful webhook confirms booking ────────────────────────────

    @Test
    fun `D3 - valid success webhook confirms booking and marks payment PAID`() {
        val bookingId = createBooking()
        mockMvc.post("/api/v1/tours-operator/bookings/$bookingId/pay") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect { status { isCreated() } }

        mockMvc.post("/api/v1/tours-operator/payments/paymob-callback?hmac=valid-hmac") {
            contentType = MediaType.APPLICATION_JSON
            content = webhookBody("ORDER-TEST-123", "TXN-001", "true", "false", "false", 4500)
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("confirmed") }
        }

        val payment = dsl.selectFrom(TOURS_OPERATOR_PAYMENT)
            .where(TOURS_OPERATOR_PAYMENT.BOOKING_ID.eq(UUID.fromString(bookingId)))
            .fetchOne()
        assertThat(payment!!.status).isEqualTo("PAID")

        val booking = dsl.selectFrom(TOURS_OPERATOR_BOOKING)
            .where(TOURS_OPERATOR_BOOKING.ID.eq(UUID.fromString(bookingId)))
            .fetchOne()
        assertThat(booking!!.status).isEqualTo("CONFIRMED")
    }

    // ── D4: Duplicate webhook is idempotent ───────────────────────────────

    @Test
    fun `D4 - duplicate webhook returns already_processed`() {
        val bookingId = createBooking()
        mockMvc.post("/api/v1/tours-operator/bookings/$bookingId/pay") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect { status { isCreated() } }

        val body = webhookBody("ORDER-TEST-123", "TXN-001", "true", "false", "false", 4500)
        mockMvc.post("/api/v1/tours-operator/payments/paymob-callback?hmac=valid-hmac") {
            contentType = MediaType.APPLICATION_JSON; content = body
        }.andExpect { status { isOk() } }

        mockMvc.post("/api/v1/tours-operator/payments/paymob-callback?hmac=valid-hmac") {
            contentType = MediaType.APPLICATION_JSON; content = body
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("already_processed") }
        }
    }

    // ── D5: Invalid HMAC → 400 ────────────────────────────────────────────

    @Test
    fun `D5 - invalid HMAC returns 400 and does not change payment status`() {
        val bookingId = createBooking()
        mockMvc.post("/api/v1/tours-operator/bookings/$bookingId/pay") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect { status { isCreated() } }

        mockMvc.post("/api/v1/tours-operator/payments/paymob-callback?hmac=WRONG-HMAC") {
            contentType = MediaType.APPLICATION_JSON
            content = webhookBody("ORDER-TEST-123", "TXN-BAD", "true", "false", "false", 4500)
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("invalid_signature") }
        }

        val payment = dsl.selectFrom(TOURS_OPERATOR_PAYMENT)
            .where(TOURS_OPERATOR_PAYMENT.BOOKING_ID.eq(UUID.fromString(bookingId)))
            .fetchOne()
        assertThat(payment!!.status).isEqualTo("PENDING")
    }

    // ── D6: Amount mismatch → 422 ─────────────────────────────────────────

    @Test
    fun `D6 - amount mismatch returns 422 and booking stays NEW`() {
        val bookingId = createBooking()
        mockMvc.post("/api/v1/tours-operator/bookings/$bookingId/pay") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect { status { isCreated() } }

        mockMvc.post("/api/v1/tours-operator/payments/paymob-callback?hmac=valid-hmac") {
            contentType = MediaType.APPLICATION_JSON
            content = webhookBody("ORDER-TEST-123", "TXN-MISMATCH", "true", "false", "false", 1)
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.error") { value("amount_mismatch") }
        }

        val booking = dsl.selectFrom(TOURS_OPERATOR_BOOKING)
            .where(TOURS_OPERATOR_BOOKING.ID.eq(UUID.fromString(bookingId)))
            .fetchOne()
        assertThat(booking!!.status).isEqualTo("NEW")
    }

    // ── D7: Payment status endpoint ───────────────────────────────────────

    @Test
    fun `D7a - payment status is PENDING before webhook`() {
        val bookingId = createBooking()
        mockMvc.post("/api/v1/tours-operator/bookings/$bookingId/pay") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect { status { isCreated() } }

        mockMvc.get("/api/v1/tours-operator/bookings/$bookingId/payment-status")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("PENDING") }
            }
    }

    @Test
    fun `D7b - payment status is PAID after successful webhook`() {
        val bookingId = createBooking()
        mockMvc.post("/api/v1/tours-operator/bookings/$bookingId/pay") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect { status { isCreated() } }

        mockMvc.post("/api/v1/tours-operator/payments/paymob-callback?hmac=valid-hmac") {
            contentType = MediaType.APPLICATION_JSON
            content = webhookBody("ORDER-TEST-123", "TXN-002", "true", "false", "false", 4500)
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/v1/tours-operator/bookings/$bookingId/payment-status")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("PAID") }
                jsonPath("$.paidAt") { isNotEmpty() }
            }
    }

    // ── D8: Booking not found ─────────────────────────────────────────────

    @Test
    fun `D8 - pay for non-existent booking returns 404`() {
        mockMvc.post("/api/v1/tours-operator/bookings/${UUID.randomUUID()}/pay") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect { status { isNotFound() } }
    }
}
