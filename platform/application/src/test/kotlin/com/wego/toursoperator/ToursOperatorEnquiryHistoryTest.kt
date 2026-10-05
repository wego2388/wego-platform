package com.wego.toursoperator

import com.wego.generated.jooq.tables.ToursOperatorPayment.TOURS_OPERATOR_PAYMENT
import com.wego.generated.jooq.tables.ToursOperatorTour.TOURS_OPERATOR_TOUR
import com.wego.toursoperator.application.BookingMode
import com.wego.toursoperator.application.BookingRepository
import com.wego.toursoperator.application.PaymentRepository
import com.wego.toursoperator.application.PaymobClient
import com.wego.toursoperator.application.TourSlotRepository
import com.wego.toursoperator.application.TransactionRunner
import com.wego.toursoperator.domain.Booking
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.BookingPricing
import com.wego.toursoperator.domain.CustomerContact
import com.wego.toursoperator.domain.Money
import com.wego.toursoperator.domain.Payment
import com.wego.toursoperator.domain.PaymentId
import com.wego.toursoperator.domain.PaymentStatus
import com.wego.toursoperator.domain.TimeSlot
import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.TourSlot
import com.wego.toursoperator.domain.TourSlotId
import com.wego.toursoperator.infrastructure.PaymobClientFactory
import com.wego.toursoperator.infrastructure.PaymobConfig
import com.wego.toursoperator.infrastructure.PaymobHttpClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jooq.DSLContext
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Real signed callbacks, real persistence, ENQUIRY_ONLY and real adapter. Test-only keys, no provider network. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    properties = [
        "tours-operator.booking-mode=ENQUIRY_ONLY", "tours-operator.paymob.mock-enabled=false",
        "tours-operator.paymob.secret-key=test-only-secret", "tours-operator.paymob.public-key=test-only-public",
        "tours-operator.paymob.api-key=test-only-api", "tours-operator.paymob.integration-id=123", "tours-operator.paymob.owner-id=456",
        "tours-operator.paymob.hmac-secret=test-only-hmac",
        "tours-operator.paymob.notification-url=https://safari.test.invalid/callback",
        "tours-operator.paymob.redirection-url=https://safari.test.invalid/result",
    ],
)
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ToursOperatorEnquiryHistoryTest {
    companion object {
        @Container @JvmStatic
        val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:18.4-alpine").withDatabaseName("safari_enquiry_history_test")
        private val day = AtomicInteger(1)

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

    @Autowired private lateinit var tx: TransactionRunner

    @Autowired
    @Qualifier("stoBookingRepositoryImpl")
    private lateinit var bookings: BookingRepository

    @Autowired
    @Qualifier("stoPaymentRepositoryImpl")
    private lateinit var payments: PaymentRepository

    @Autowired
    @Qualifier("stoTourSlotRepositoryImpl")
    private lateinit var slots: TourSlotRepository

    @Autowired
    @Qualifier("stoPaymobClient")
    private lateinit var client: PaymobClient

    @Autowired
    @Qualifier("stoPaymobConfig")
    private lateinit var config: PaymobConfig

    private fun seedHistoricalPending(): Pair<Booking, Payment> =
        tx.runInTransaction {
            val tourId =
                TourId(
                    dsl
                        .select(TOURS_OPERATOR_TOUR.ID)
                        .from(TOURS_OPERATOR_TOUR)
                        .limit(1)
                        .fetchOne()!!
                        .value1()!!,
                )
            val now = Instant.now()
            val slot =
                TourSlot(
                    TourSlotId.generate(),
                    tourId,
                    LocalDate.now().plusDays(day.incrementAndGet().toLong()),
                    TimeSlot.MORNING,
                    10,
                    1,
                    false,
                    now,
                )
            slots.save(slot)
            // Explicit financial fixture, not a commercial catalog edit.
            val booking =
                Booking.createNew(
                    BookingId.generate(),
                    "STR-2026-${bookings.nextReferenceSequence(2026)}",
                    tourId,
                    slot.id,
                    slot.date,
                    slot.timeSlot,
                    BookingPricing.compute(1, 0, Money(BigDecimal("45.00")), null),
                    CustomerContact("Test Guest", "+201000000000", "EG", null),
                    "Test hotel",
                    null,
                    null,
                    "en",
                    now,
                )
            bookings.save(booking)
            val payment = Payment.createPending(PaymentId.generate(), booking.id, BigDecimal("45.00"), 4500, "EUR", now)
            payment.assignPaymobCheckout("order-${payment.id.value}", "test-only-old-checkout-token")
            payments.save(payment)
            booking to payment
        }

    private fun callback(
        payment: Payment,
        refunded: Boolean,
        signatureOverride: String? = null,
    ): String {
        val id = "txn-${payment.id.value}-${if (refunded) "refund" else "paid"}"
        val values =
            listOf(
                "4500",
                "2026-10-05T00:00:00Z",
                "EUR",
                "false",
                "false",
                id,
                "123",
                "true",
                "false",
                "false",
                refunded.toString(),
                "true",
                "false",
                payment.paymobOrderId!!,
                "456",
                "false",
                "1234",
                "MasterCard",
                "card",
                "true",
            )
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec("test-only-hmac".toByteArray(), "HmacSHA512"))
        val hmac = signatureOverride ?: mac.doFinal(values.joinToString("").toByteArray()).joinToString("") { "%02x".format(it) }
        return mvc
            .post("/api/v1/tours-operator/payments/paymob-callback?hmac=$hmac") {
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"obj":{"id":"$id","success":true,"pending":false,"is_refunded":$refunded,"error_occured":false,"has_parent_transaction":false,"is_3d_secure":true,"is_auth":false,"is_capture":false,"is_standalone_payment":true,"is_voided":false,"owner":"456","amount_cents":4500,"currency":"EUR","created_at":"2026-10-05T00:00:00Z","integration_id":"123","order":{"id":"${payment.paymobOrderId}"},"source_data":{"pan":"1234","sub_type":"MasterCard","type":"card"}}}"""
            }.andReturn()
            .response
            .let { response ->
                if (signatureOverride == null) assertThat(response.status).isEqualTo(200) else assertThat(response.status).isEqualTo(400)
                response.contentAsString
            }
    }

    @Test
    @Order(2)
    fun `enquiry mode preserves real signed paid refund and duplicate reconciliation while blocking resume`() {
        assertThat(client).isInstanceOf(PaymobHttpClient::class.java)
        val (booking, payment) = seedHistoricalPending()
        mvc
            .post("/api/v1/tours-operator/bookings/${booking.id.value}/pay") {
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.error") { value("online_payment_unavailable") }
                jsonPath("$.checkoutUrl") { doesNotExist() }
            }
        assertThat(callback(payment, false, "invalid-signature")).contains("invalid_signature")
        assertThat(payments.findById(payment.id)!!.status).isEqualTo(PaymentStatus.PENDING)
        assertThat(callback(payment, false)).contains("confirmed")
        assertThat(payments.findById(payment.id)!!.status).isEqualTo(PaymentStatus.PAID)
        assertThat(callback(payment, false)).contains("already_processed")
        assertThat(callback(payment, true)).contains("refunded")
        assertThat(payments.findById(payment.id)!!.status).isEqualTo(PaymentStatus.REFUNDED)
        assertThat(slots.findById(booking.slotId)!!.bookedCount).isZero()
        assertThat(callback(payment, true)).contains("already_processed")
        assertThat(
            payments.historyForBooking(booking.id).map {
                it.toStatus
            },
        ).containsExactly(PaymentStatus.PENDING, PaymentStatus.PAID, PaymentStatus.REFUNDED)
    }

    @Test
    @Order(1)
    fun `existence guard refuses removing credentials for every stored payment status`() {
        // Starts first against this class's own fresh PostgreSQL container:
        // no other status can accidentally satisfy a narrowed existence query.
        assertThat(dsl.fetchCount(TOURS_OPERATOR_PAYMENT)).isZero()
        val (_, payment) = seedHistoricalPending()
        assertThat(dsl.fetchCount(TOURS_OPERATOR_PAYMENT)).isEqualTo(1)
        val empty =
            config.copy(
                secretKey = "",
                publicKey = "",
                apiKey = "",
                integrationId = "",
                ownerId = "",
                hmacSecret = "",
                notificationUrl = "",
                redirectionUrl = "",
            )
        PaymentStatus.entries.forEach { status ->
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            dsl
                .update(TOURS_OPERATOR_PAYMENT)
                .set(TOURS_OPERATOR_PAYMENT.STATUS, status.name)
                .set(
                    TOURS_OPERATOR_PAYMENT.PAID_AT,
                    if (status in
                        setOf(PaymentStatus.PAID, PaymentStatus.REFUNDED, PaymentStatus.REVIEW_REQUIRED)
                    ) {
                        now
                    } else {
                        null
                    },
                ).set(TOURS_OPERATOR_PAYMENT.FAILED_AT, if (status == PaymentStatus.FAILED) now else null)
                .set(TOURS_OPERATOR_PAYMENT.REFUNDED_AT, if (status == PaymentStatus.REFUNDED) now else null)
                .set(TOURS_OPERATOR_PAYMENT.REVENUE_RECOGNISED_AT, if (status == PaymentStatus.PAID) now else null)
                .where(TOURS_OPERATOR_PAYMENT.ID.eq(payment.id.value))
                .execute()
            assertThat(payments.hasAnyPayments()).isTrue()
            assertThatThrownBy {
                PaymobClientFactory.realOrDisabled(
                    BookingMode.ENQUIRY_ONLY,
                    empty,
                    ObjectMapper(),
                    payments::hasAnyPayments,
                )
            }.hasMessageContaining("Payment history exists")
        }
    }

    @Test
    @Order(3)
    fun `staff status is truthful even while stored switches are open`() {
        mvc
            .get("/api/v1/tours-operator/staff/sales-control") {
                with(
                    user(
                        "test-staff",
                    ).authorities(
                        org.springframework.security.core.authority
                            .SimpleGrantedAuthority("tours-operator.tour:manage"),
                    ),
                )
            }.andExpect {
                status { isOk() }
                jsonPath("$.bookingMode") { value("ENQUIRY_ONLY") }
                jsonPath("$.bookingsPaused") { value(false) }
                jsonPath("$.paymentsPaused") { value(false) }
            }
        mvc.get("/api/v1/tours-operator/staff/sales-control").andExpect { status { isUnauthorized() } }
        mvc.get("/api/v1/tours-operator/sales-status").andExpect {
            jsonPath("$.bookingsOpen") { value(false) }
            jsonPath("$.paymentsOpen") { value(false) }
        }
    }
}
