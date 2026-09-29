package com.wego.toursoperator

import com.wego.generated.jooq.tables.ToursOperatorBooking.TOURS_OPERATOR_BOOKING
import com.wego.generated.jooq.tables.ToursOperatorPayment.TOURS_OPERATOR_PAYMENT
import com.wego.generated.jooq.tables.ToursOperatorTour.TOURS_OPERATOR_TOUR
import com.wego.generated.jooq.tables.ToursOperatorTourSlot.TOURS_OPERATOR_TOUR_SLOT
import com.wego.toursoperator.application.PaymobCheckoutCommand
import com.wego.toursoperator.application.PaymobCheckoutResult
import com.wego.toursoperator.application.PaymobClient
import com.wego.toursoperator.application.PaymobRefundResult
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.anyMap
import org.mockito.Mockito.anyString
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
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
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

    @Suppress("UNCHECKED_CAST")
    private fun <T> anyObject(): T {
        org.mockito.Mockito.any<T>()
        return null as T
    }

    @BeforeEach
    fun setup() {
        // Configure stub behaviours using plain Mockito
        `when`(paymobClient.createCheckout(anyObject<PaymobCheckoutCommand>()))
            .thenReturn(PaymobCheckoutResult.Success("ORDER-TEST-123", "TOKEN-TEST-123"))
        `when`(paymobClient.buildCheckoutUrl(anyString()))
            .thenAnswer { inv -> "https://accept.paymob.com/test/checkout/${inv.arguments[0]}" }
        `when`(paymobClient.acceptsWebhookIdentity(anyString(), anyString()))
            .thenAnswer { inv -> inv.arguments[0] == "100001" && inv.arguments[1] == "100001" }
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
        dsl
            .insertInto(TOURS_OPERATOR_TOUR)
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
        dsl
            .insertInto(TOURS_OPERATOR_TOUR_SLOT)
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
        val result =
            mockMvc
                .post("/api/v1/tours-operator/bookings") {
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
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
        orderId: String,
        transactionId: String,
        success: String,
        pending: String,
        isRefund: String,
        amountCents: Long,
        currency: String = "EUR",
        integrationId: String = "100001",
    ) = """{"obj":{"id":"$transactionId","success":$success,"pending":$pending,
              "is_refunded":$isRefund,"error_occured":false,"has_parent_transaction":false,
              "is_3d_secure":true,"is_auth":false,"is_capture":false,
              "is_standalone_payment":true,"is_voided":false,"owner":"100001",
              "amount_cents":$amountCents,"currency":"$currency",
              "created_at":"2026-09-28T10:00:00Z","integration_id":"$integrationId",
              "order":{"id":"$orderId"},
              "source_data":{"pan":"1234","sub_type":"MasterCard","type":"card"},
              "data":{}}}"""

    private fun cairoMidnight(date: LocalDate): OffsetDateTime =
        date.atStartOfDay(ZoneId.of("Africa/Cairo")).toOffsetDateTime()

    /** Inserts a ledger row directly so tests can place lifecycle events at exact instants. */
    private fun insertPayment(
        status: String,
        createdAt: OffsetDateTime,
        paidAt: OffsetDateTime? = null,
        refundedAt: OffsetDateTime? = null,
        revenueRecognisedAt: OffsetDateTime? = null,
    ): UUID {
        val paymentId = UUID.randomUUID()
        dsl
            .insertInto(TOURS_OPERATOR_PAYMENT)
            .set(TOURS_OPERATOR_PAYMENT.ID, paymentId)
            .set(TOURS_OPERATOR_PAYMENT.BOOKING_ID, UUID.fromString(createBooking()))
            .set(TOURS_OPERATOR_PAYMENT.AMOUNT_EUR, java.math.BigDecimal("45.00"))
            .set(TOURS_OPERATOR_PAYMENT.AMOUNT_MINOR_UNITS, 4500L)
            .set(TOURS_OPERATOR_PAYMENT.CURRENCY_CODE, "EUR")
            .set(TOURS_OPERATOR_PAYMENT.PROVIDER_REFERENCE, "sts-$paymentId")
            .set(TOURS_OPERATOR_PAYMENT.STATUS, status)
            .set(TOURS_OPERATOR_PAYMENT.CREATED_AT, createdAt)
            .set(TOURS_OPERATOR_PAYMENT.PAID_AT, paidAt)
            .set(TOURS_OPERATOR_PAYMENT.REFUNDED_AT, refundedAt)
            .set(TOURS_OPERATOR_PAYMENT.REVENUE_RECOGNISED_AT, revenueRecognisedAt)
            .execute()
        return paymentId
    }

    private fun ledger(query: String) =
        mockMvc.get("/api/v1/tours-operator/staff/payments?$query") {
            with(user("finance-operator").authorities(SimpleGrantedAuthority("tours-operator.payment:view")))
        }

    // ── D1: Initiate creates PENDING record ────────────────────────────────

    @Test
    fun `D1 - initiating payment creates PENDING record and returns checkout URL`() {
        val bookingId = createBooking()

        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isCreated() }
                jsonPath("$.checkoutUrl") { value("https://accept.paymob.com/test/checkout/TOKEN-TEST-123") }
                jsonPath("$.status") { value("PENDING") }
                jsonPath("$.amountEur") { value("45.00") }
                jsonPath("$.currencyCode") { value("EUR") }
            }

        val payment =
            dsl
                .selectFrom(TOURS_OPERATOR_PAYMENT)
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

        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect { status { isCreated() } }

        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isOk() }
                jsonPath("$.checkoutUrl") { value("https://accept.paymob.com/test/checkout/TOKEN-TEST-123") }
            }

        val count =
            dsl
                .selectCount()
                .from(TOURS_OPERATOR_PAYMENT)
                .where(TOURS_OPERATOR_PAYMENT.BOOKING_ID.eq(UUID.fromString(bookingId)))
                .fetchOne(0, Int::class.java)
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `D2b - concurrent pay calls create one provider checkout`() {
        val bookingId = createBooking()
        val providerCallStarted = CountDownLatch(1)
        val releaseProviderCall = CountDownLatch(1)
        `when`(paymobClient.createCheckout(anyObject<PaymobCheckoutCommand>()))
            .thenAnswer {
                providerCallStarted.countDown()
                check(releaseProviderCall.await(10, TimeUnit.SECONDS)) { "Timed out waiting to release provider call" }
                PaymobCheckoutResult.Success("ORDER-CONCURRENT", "TOKEN-CONCURRENT")
            }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val first =
                executor.submit(
                    Callable {
                        mockMvc
                            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                                contentType = MediaType.APPLICATION_JSON
                            }.andReturn()
                            .response.status
                    },
                )
            check(providerCallStarted.await(10, TimeUnit.SECONDS)) { "Provider call did not start" }
            val second =
                executor.submit(
                    Callable {
                        mockMvc
                            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                                contentType = MediaType.APPLICATION_JSON
                            }.andReturn()
                            .response.status
                    },
                )

            assertThat(second.get()).isEqualTo(409)
            releaseProviderCall.countDown()
            assertThat(first.get()).isEqualTo(201)
            verify(paymobClient, times(1)).createCheckout(anyObject<PaymobCheckoutCommand>())
            val count =
                dsl
                    .selectCount()
                    .from(TOURS_OPERATOR_PAYMENT)
                    .where(TOURS_OPERATOR_PAYMENT.BOOKING_ID.eq(UUID.fromString(bookingId)))
                    .fetchOne(0, Int::class.java)
            assertThat(count).isEqualTo(1)
        } finally {
            releaseProviderCall.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `D2c - ambiguous provider failure is never retried automatically`() {
        val bookingId = createBooking()
        `when`(paymobClient.createCheckout(anyObject<PaymobCheckoutCommand>()))
            .thenReturn(PaymobCheckoutResult.Failure("network timeout"))

        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isBadGateway() }
                jsonPath("$.error") { value("payment_provider_error") }
            }

        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("payment_reconciliation_required") }
            }

        verify(paymobClient, times(1)).createCheckout(anyObject<PaymobCheckoutCommand>())
        val payment =
            dsl
                .selectFrom(TOURS_OPERATOR_PAYMENT)
                .where(TOURS_OPERATOR_PAYMENT.BOOKING_ID.eq(UUID.fromString(bookingId)))
                .fetchOne()!!
        assertThat(payment.status).isEqualTo("RECONCILIATION_REQUIRED")
        assertThat(payment.providerReference).startsWith("sts-")
        assertThat(payment.paymobOrderId).isNull()
    }

    // ── D3: Successful webhook confirms booking ────────────────────────────

    @Test
    fun `D3 - valid success webhook confirms booking and marks payment PAID`() {
        val bookingId = createBooking()
        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect { status { isCreated() } }

        mockMvc
            .post("/api/v1/tours-operator/payments/paymob-callback?hmac=valid-hmac") {
                contentType = MediaType.APPLICATION_JSON
                content = webhookBody("ORDER-TEST-123", "TXN-001", "true", "false", "false", 4500)
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("confirmed") }
            }

        val payment =
            dsl
                .selectFrom(TOURS_OPERATOR_PAYMENT)
                .where(TOURS_OPERATOR_PAYMENT.BOOKING_ID.eq(UUID.fromString(bookingId)))
                .fetchOne()
        assertThat(payment!!.status).isEqualTo("PAID")
        assertThat(payment.lastCallbackAudit).doesNotContain("pan", "Test User", "+20100000001")
        assertThat(payment.providerCheckoutToken).isNull()

        val booking =
            dsl
                .selectFrom(TOURS_OPERATOR_BOOKING)
                .where(TOURS_OPERATOR_BOOKING.ID.eq(UUID.fromString(bookingId)))
                .fetchOne()
        assertThat(booking!!.status).isEqualTo("CONFIRMED")
    }

    // ── D4: Duplicate webhook is idempotent ───────────────────────────────

    @Test
    fun `D4 - duplicate webhook returns already_processed`() {
        val bookingId = createBooking()
        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect { status { isCreated() } }

        val body = webhookBody("ORDER-TEST-123", "TXN-001", "true", "false", "false", 4500)
        mockMvc
            .post("/api/v1/tours-operator/payments/paymob-callback?hmac=valid-hmac") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect { status { isOk() } }

        mockMvc
            .post("/api/v1/tours-operator/payments/paymob-callback?hmac=valid-hmac") {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("already_processed") }
            }
    }

    // ── D5: Invalid HMAC → 400 ────────────────────────────────────────────

    @Test
    fun `D5 - invalid HMAC returns 400 and does not change payment status`() {
        val bookingId = createBooking()
        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect { status { isCreated() } }

        mockMvc
            .post("/api/v1/tours-operator/payments/paymob-callback?hmac=WRONG-HMAC") {
                contentType = MediaType.APPLICATION_JSON
                content = webhookBody("ORDER-TEST-123", "TXN-BAD", "true", "false", "false", 4500)
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("invalid_signature") }
            }

        val payment =
            dsl
                .selectFrom(TOURS_OPERATOR_PAYMENT)
                .where(TOURS_OPERATOR_PAYMENT.BOOKING_ID.eq(UUID.fromString(bookingId)))
                .fetchOne()
        assertThat(payment!!.status).isEqualTo("PENDING")
    }

    // ── D6: Amount mismatch → 422 ─────────────────────────────────────────

    @Test
    fun `D6 - amount mismatch returns 422 and booking stays NEW`() {
        val bookingId = createBooking()
        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect { status { isCreated() } }

        mockMvc
            .post("/api/v1/tours-operator/payments/paymob-callback?hmac=valid-hmac") {
                contentType = MediaType.APPLICATION_JSON
                content = webhookBody("ORDER-TEST-123", "TXN-MISMATCH", "true", "false", "false", 1)
            }.andExpect {
                status { isUnprocessableContent() }
                jsonPath("$.error") { value("amount_mismatch") }
            }

        val booking =
            dsl
                .selectFrom(TOURS_OPERATOR_BOOKING)
                .where(TOURS_OPERATOR_BOOKING.ID.eq(UUID.fromString(bookingId)))
                .fetchOne()
        assertThat(booking!!.status).isEqualTo("NEW")
    }

    @Test
    fun `D6b - currency mismatch is rejected`() {
        val bookingId = createBooking()
        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect { status { isCreated() } }

        mockMvc
            .post("/api/v1/tours-operator/payments/paymob-callback?hmac=valid-hmac") {
                contentType = MediaType.APPLICATION_JSON
                content = webhookBody("ORDER-TEST-123", "TXN-CURRENCY", "true", "false", "false", 4500, "EGP")
            }.andExpect {
                status { isUnprocessableContent() }
                jsonPath("$.error") { value("currency_mismatch") }
            }
    }

    @Test
    fun `D6c - callback from another integration is rejected`() {
        val bookingId = createBooking()
        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect { status { isCreated() } }

        mockMvc
            .post("/api/v1/tours-operator/payments/paymob-callback?hmac=valid-hmac") {
                contentType = MediaType.APPLICATION_JSON
                content = webhookBody("ORDER-TEST-123", "TXN-INTEGRATION", "true", "false", "false", 4500, integrationId = "999999")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("integration_mismatch") }
            }
    }

    @Test
    fun `D6d - pending callback remains PENDING`() {
        val bookingId = createBooking()
        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect { status { isCreated() } }

        mockMvc
            .post("/api/v1/tours-operator/payments/paymob-callback?hmac=valid-hmac") {
                contentType = MediaType.APPLICATION_JSON
                content = webhookBody("ORDER-TEST-123", "TXN-PENDING", "false", "true", "false", 4500)
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("pending") }
            }

        val paymentStatus =
            dsl
                .select(TOURS_OPERATOR_PAYMENT.STATUS)
                .from(TOURS_OPERATOR_PAYMENT)
                .where(TOURS_OPERATOR_PAYMENT.BOOKING_ID.eq(UUID.fromString(bookingId)))
                .fetchOne(TOURS_OPERATOR_PAYMENT.STATUS)
        assertThat(paymentStatus).isEqualTo("PENDING")
    }

    @Test
    fun `D6e - refund callback transitions captured payment to REFUNDED`() {
        val bookingId = createBooking()
        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect { status { isCreated() } }
        mockMvc
            .post("/api/v1/tours-operator/payments/paymob-callback?hmac=valid-hmac") {
                contentType = MediaType.APPLICATION_JSON
                content = webhookBody("ORDER-TEST-123", "TXN-PAID", "true", "false", "false", 4500)
            }.andExpect { status { isOk() } }

        mockMvc
            .post("/api/v1/tours-operator/payments/paymob-callback?hmac=valid-hmac") {
                contentType = MediaType.APPLICATION_JSON
                content = webhookBody("ORDER-TEST-123", "TXN-PAID", "false", "false", "true", 4500)
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("refunded") }
            }

        val payment =
            dsl
                .selectFrom(TOURS_OPERATOR_PAYMENT)
                .where(TOURS_OPERATOR_PAYMENT.BOOKING_ID.eq(UUID.fromString(bookingId)))
                .fetchOne()!!
        assertThat(payment.status).isEqualTo("REFUNDED")
        assertThat(payment.paidAt).isNotNull()
        assertThat(payment.refundedAt).isNotNull()
    }

    @Test
    fun `D6f - provider success after expiry requires review and never confirms booking`() {
        val bookingId = createBooking()
        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect { status { isCreated() } }
        dsl
            .update(TOURS_OPERATOR_BOOKING)
            .set(TOURS_OPERATOR_BOOKING.STATUS, "EXPIRED")
            .set(TOURS_OPERATOR_BOOKING.EXPIRED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(TOURS_OPERATOR_BOOKING.ID.eq(UUID.fromString(bookingId)))
            .execute()

        mockMvc
            .post("/api/v1/tours-operator/payments/paymob-callback?hmac=valid-hmac") {
                contentType = MediaType.APPLICATION_JSON
                content = webhookBody("ORDER-TEST-123", "TXN-LATE", "true", "false", "false", 4500)
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("review_required") }
            }

        val paymentStatus =
            dsl
                .select(TOURS_OPERATOR_PAYMENT.STATUS)
                .from(TOURS_OPERATOR_PAYMENT)
                .where(TOURS_OPERATOR_PAYMENT.BOOKING_ID.eq(UUID.fromString(bookingId)))
                .fetchOne(TOURS_OPERATOR_PAYMENT.STATUS)
        val bookingStatus =
            dsl
                .select(TOURS_OPERATOR_BOOKING.STATUS)
                .from(TOURS_OPERATOR_BOOKING)
                .where(TOURS_OPERATOR_BOOKING.ID.eq(UUID.fromString(bookingId)))
                .fetchOne(TOURS_OPERATOR_BOOKING.STATUS)
        assertThat(paymentStatus).isEqualTo("REVIEW_REQUIRED")
        assertThat(bookingStatus).isEqualTo("EXPIRED")
    }

    @Test
    fun `D6g - failed provider identity is retained so a late success can confirm`() {
        val bookingId = createBooking()
        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect { status { isCreated() } }

        val transactionId = "TXN-LATE-SUCCESS"
        mockMvc
            .post("/api/v1/tours-operator/payments/paymob-callback?hmac=valid-hmac") {
                contentType = MediaType.APPLICATION_JSON
                content = webhookBody("ORDER-TEST-123", transactionId, "false", "false", "false", 4500)
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("failed") }
            }

        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("payment_not_payable_status_failed") }
            }

        mockMvc
            .post("/api/v1/tours-operator/payments/paymob-callback?hmac=valid-hmac") {
                contentType = MediaType.APPLICATION_JSON
                content = webhookBody("ORDER-TEST-123", transactionId, "true", "false", "false", 4500)
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("confirmed") }
            }

        val payment =
            dsl
                .selectFrom(TOURS_OPERATOR_PAYMENT)
                .where(TOURS_OPERATOR_PAYMENT.BOOKING_ID.eq(UUID.fromString(bookingId)))
                .fetchOne()!!
        assertThat(payment.status).isEqualTo("PAID")
        assertThat(payment.paymobOrderId).isEqualTo("ORDER-TEST-123")
        assertThat(payment.paymobTransactionId).isEqualTo(transactionId)
        assertThat(
            dsl
                .select(TOURS_OPERATOR_BOOKING.STATUS)
                .from(TOURS_OPERATOR_BOOKING)
                .where(TOURS_OPERATOR_BOOKING.ID.eq(UUID.fromString(bookingId)))
                .fetchOne(TOURS_OPERATOR_BOOKING.STATUS),
        ).isEqualTo("CONFIRMED")
    }

    // ── D7: Payment status endpoint ───────────────────────────────────────

    @Test
    fun `D7a - payment status is PENDING before webhook`() {
        val bookingId = createBooking()
        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect { status { isCreated() } }

        mockMvc
            .get("/api/v1/tours-operator/bookings/$bookingId/payment-status")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("PENDING") }
                jsonPath("$.amountEur") { value("45.00") }
                jsonPath("$.currencyCode") { value("EUR") }
            }
    }

    @Test
    fun `D7b - payment status is PAID after successful webhook`() {
        val bookingId = createBooking()
        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect { status { isCreated() } }

        mockMvc
            .post("/api/v1/tours-operator/payments/paymob-callback?hmac=valid-hmac") {
                contentType = MediaType.APPLICATION_JSON
                content = webhookBody("ORDER-TEST-123", "TXN-002", "true", "false", "false", 4500)
            }.andExpect { status { isOk() } }

        mockMvc
            .get("/api/v1/tours-operator/bookings/$bookingId/payment-status")
            .andExpect {
                status { isOk() }
                jsonPath("$.status") { value("PAID") }
                jsonPath("$.paidAt") { isNotEmpty() }
            }
    }

    // ── D8: Booking not found ─────────────────────────────────────────────

    @Test
    fun `D8 - pay for non-existent booking returns 404`() {
        mockMvc
            .post("/api/v1/tours-operator/bookings/${UUID.randomUUID()}/pay") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect { status { isNotFound() } }
    }

    // ── D9: Staff payment ledger ──────────────────────────────────────────

    @Test
    fun `D9a - payment ledger requires authentication`() {
        val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
        mockMvc
            .get("/api/v1/tours-operator/staff/payments?from=$today&to=$today")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `D9b - payment ledger rejects staff without payment view permission`() {
        val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
        mockMvc
            .get("/api/v1/tours-operator/staff/payments?from=$today&to=$today") {
                with(user("booking-operator").authorities(SimpleGrantedAuthority("tours-operator.booking:view")))
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `D9c - payment ledger exposes immutable captured and refunded timestamps`() {
        val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
        val bookingId = createBooking()
        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect { status { isCreated() } }
        mockMvc
            .post("/api/v1/tours-operator/payments/paymob-callback?hmac=valid-hmac") {
                contentType = MediaType.APPLICATION_JSON
                content = webhookBody("ORDER-TEST-123", "TXN-LEDGER", "true", "false", "false", 4500)
            }.andExpect { status { isOk() } }
        mockMvc
            .post("/api/v1/tours-operator/payments/paymob-callback?hmac=valid-hmac") {
                contentType = MediaType.APPLICATION_JSON
                content = webhookBody("ORDER-TEST-123", "TXN-LEDGER", "false", "false", "true", 4500)
            }.andExpect { status { isOk() } }

        mockMvc
            .get("/api/v1/tours-operator/staff/payments?from=$today&to=$today&status=REFUNDED") {
                with(user("finance-operator").authorities(SimpleGrantedAuthority("tours-operator.payment:view")))
            }.andExpect {
                status { isOk() }
                jsonPath("$[0].bookingId") { value(bookingId) }
                jsonPath("$[0].tourId") { value(tourId.toString()) }
                jsonPath("$[0].adultsCount") { value(1) }
                jsonPath("$[0].childrenCount") { value(0) }
                jsonPath("$[0].amount.amount") { value("45.00") }
                jsonPath("$[0].amount.currencyCode") { value("EUR") }
                jsonPath("$[0].status") { value("REFUNDED") }
                jsonPath("$[0].paidAt") { isNotEmpty() }
                jsonPath("$[0].refundedAt") { isNotEmpty() }
                jsonPath("$[0].revenueRecognisedAt") { isNotEmpty() }
                jsonPath("$[0].customer") { doesNotExist() }
            }
    }

    @Test
    fun `D9d - payment ledger rejects an inverted date range`() {
        mockMvc
            .get("/api/v1/tours-operator/staff/payments?from=2026-10-02&to=2026-10-01") {
                with(user("finance-operator").authorities(SimpleGrantedAuthority("tours-operator.payment:view")))
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("invalid_date_range") }
            }
    }

    @Test
    fun `D9e - payment ledger enforces page size bounds`() {
        val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
        listOf("size=0", "size=201").forEach { bound ->
            mockMvc
                .get("/api/v1/tours-operator/staff/payments?from=$today&to=$today&$bound") {
                    with(user("finance-operator").authorities(SimpleGrantedAuthority("tours-operator.payment:view")))
                }.andExpect { status { isBadRequest() } }
        }
    }

    @Test
    fun `D9f - a refunded review capture is never recognised as revenue`() {
        val today = LocalDate.now(ZoneId.of("Africa/Cairo"))
        val bookingId = createBooking()
        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                contentType = MediaType.APPLICATION_JSON
            }.andExpect { status { isCreated() } }
        dsl
            .update(TOURS_OPERATOR_BOOKING)
            .set(TOURS_OPERATOR_BOOKING.STATUS, "EXPIRED")
            .set(TOURS_OPERATOR_BOOKING.EXPIRED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(TOURS_OPERATOR_BOOKING.ID.eq(UUID.fromString(bookingId)))
            .execute()
        mockMvc
            .post("/api/v1/tours-operator/payments/paymob-callback?hmac=valid-hmac") {
                contentType = MediaType.APPLICATION_JSON
                content = webhookBody("ORDER-TEST-123", "TXN-REVIEW", "true", "false", "false", 4500)
            }.andExpect { jsonPath("$.status") { value("review_required") } }
        mockMvc
            .post("/api/v1/tours-operator/payments/paymob-callback?hmac=valid-hmac") {
                contentType = MediaType.APPLICATION_JSON
                content = webhookBody("ORDER-TEST-123", "TXN-REVIEW", "false", "false", "true", 4500)
            }.andExpect { status { isOk() } }

        ledger("from=$today&to=$today").andExpect {
            status { isOk() }
            jsonPath("$[0].status") { value("REFUNDED") }
            jsonPath("$[0].paidAt") { isNotEmpty() }
            jsonPath("$[0].revenueRecognisedAt") { isEmpty() }
        }
    }

    @Test
    fun `D9g - ledger includes cross-period refunds and honours the half-open Cairo range`() {
        val from = LocalDate.of(2026, 10, 1)
        val to = LocalDate.of(2026, 10, 31)
        val septemberSale = cairoMidnight(LocalDate.of(2026, 9, 15))
        val crossPeriodRefund =
            insertPayment(
                status = "REFUNDED",
                createdAt = septemberSale,
                paidAt = septemberSale,
                refundedAt = cairoMidnight(LocalDate.of(2026, 10, 5)),
                revenueRecognisedAt = septemberSale,
            )
        val lastInstantInRange = cairoMidnight(to.plusDays(1)).minusNanos(1_000)
        val endOfRange =
            insertPayment("PAID", lastInstantInRange, lastInstantInRange, revenueRecognisedAt = lastInstantInRange)
        val nextDay = cairoMidnight(to.plusDays(1))
        insertPayment("PAID", nextDay, nextDay, revenueRecognisedAt = nextDay)

        val body = ledger("from=$from&to=$to&size=200").andExpect { status { isOk() } }.andReturn().response.contentAsString
        val ids = Regex(""""paymentId"\s*:\s*"([0-9a-f\-]{36})"""").findAll(body).map { it.groupValues[1] }.toSet()
        assertThat(ids).containsExactlyInAnyOrder(crossPeriodRefund.toString(), endOfRange.toString())
    }

    @Test
    fun `D9h - keyset pages never overlap and cover every row`() {
        val day = LocalDate.of(2026, 10, 10)
        val at = cairoMidnight(day).plusHours(12)
        val inserted = (1..5).map { insertPayment("PAID", at, at, revenueRecognisedAt = at).toString() }
        val idPattern = Regex(""""paymentId"\s*:\s*"([0-9a-f\-]{36})"""")
        val seen = mutableListOf<String>()
        var after: String? = null
        do {
            val cursor = after?.let { "&after=$it" } ?: ""
            val body = ledger("from=$day&to=$day&size=2$cursor").andExpect { status { isOk() } }.andReturn().response.contentAsString
            val page = idPattern.findAll(body).map { it.groupValues[1] }.toList()
            seen += page
            after = page.lastOrNull()
        } while (page.size == 2)
        assertThat(seen).doesNotHaveDuplicates()
        assertThat(seen).containsExactlyInAnyOrderElementsOf(inserted)
        assertThat(seen).isSorted()
    }
}
