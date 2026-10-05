package com.wego.toursoperator

import com.wego.generated.jooq.tables.ToursOperatorBooking.TOURS_OPERATOR_BOOKING
import com.wego.generated.jooq.tables.ToursOperatorPayment.TOURS_OPERATOR_PAYMENT
import com.wego.generated.jooq.tables.ToursOperatorTourSlot.TOURS_OPERATOR_TOUR_SLOT
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
