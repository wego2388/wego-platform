package com.wego.toursoperator.infrastructure

import com.wego.toursoperator.application.BookingMode
import com.wego.toursoperator.application.PaymobRefundResult
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

class PaymobClientFactoryTest {
    private val mapper = ObjectMapper()
    private val empty =
        PaymobConfig(
            "https://accept.paymob.com",
            "",
            "",
            "",
            "",
            "",
            "",
            "https://accept.paymob.com/unifiedcheckout/",
            "",
            "",
            "Sharm El Sheikh",
            "EG",
            1800,
        )
    private val complete =
        empty.copy(
            secretKey = "test-secret",
            publicKey = "test-public",
            apiKey = "test-api",
            integrationId = "123",
            ownerId = "456",
            hmacSecret = "test-hmac",
            notificationUrl = "https://safari.test.invalid/callback",
            redirectionUrl = "https://safari.test.invalid/result",
        )

    @Test
    fun `fresh enquiry-only storage uses a disabled non-mock adapter`() {
        var checks = 0
        val client =
            PaymobClientFactory.realOrDisabled(BookingMode.ENQUIRY_ONLY, empty, mapper) {
                checks++
                false
            }
        assertThat(checks).isEqualTo(1)
        assertThat(client).isInstanceOf(DisabledPaymobClient::class.java)
        assertThat(client.unavailable).isTrue()
        assertThat(client.verifyWebhookSignature(emptyMap(), "anything")).isFalse()
        assertThat(client.acceptsWebhookIdentity("123", "456")).isFalse()
        assertThat(client.refund("123", 100)).isInstanceOf(PaymobRefundResult.Failure::class.java)
        assertThatThrownBy { client.buildCheckoutUrl("secret-checkout-token") }.hasMessage("payment_provider_unavailable")
    }

    @Test
    fun `any history prevents disabled adapter and a database error fails startup`() {
        assertThatThrownBy { PaymobClientFactory.realOrDisabled(BookingMode.ENQUIRY_ONLY, empty, mapper) { true } }
            .hasMessageContaining("Payment history exists")
        assertThatThrownBy { PaymobClientFactory.realOrDisabled(BookingMode.ENQUIRY_ONLY, empty, mapper) { error("db unavailable") } }
            .hasMessage("db unavailable")
    }

    @Test
    fun `each partial credential or callback URL fails rather than becoming disabled`() {
        listOf(
            empty.copy(secretKey = "sensitive-test-value"),
            empty.copy(publicKey = "sensitive-test-value"),
            empty.copy(apiKey = "sensitive-test-value"),
            empty.copy(integrationId = "sensitive-test-value"),
            empty.copy(ownerId = "sensitive-test-value"),
            empty.copy(hmacSecret = "sensitive-test-value"),
            empty.copy(notificationUrl = "https://safari.test.invalid/callback"),
            empty.copy(redirectionUrl = "https://safari.test.invalid/result"),
        ).forEach { config ->
            assertThatThrownBy { PaymobClientFactory.realOrDisabled(BookingMode.ENQUIRY_ONLY, config, mapper) { false } }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageNotContaining("sensitive-test-value")
        }
    }

    @Test
    fun `online mode still refuses missing real provider settings before database access`() {
        assertThatThrownBy {
            PaymobClientFactory.realOrDisabled(
                BookingMode.ONLINE_PAYMENT,
                empty,
                mapper,
            ) { error("must not access database") }
        }.hasMessageContaining("Paymob is not configured")
            .hasMessageNotContaining("must not access database")
    }

    @Test
    fun `complete config preserves real adapter in either mode without rejecting history`() {
        BookingMode.entries.forEach { mode ->
            assertThat(PaymobClientFactory.realOrDisabled(mode, complete, mapper) { error("not required") })
                .isInstanceOf(PaymobHttpClient::class.java)
        }
    }

    @Test
    fun `mode must be exact and enquiry mode can never select mock provider`() {
        val beans = ToursOperatorBeanConfiguration()
        assertThat(beans.bookingMode("ENQUIRY_ONLY")).isEqualTo(BookingMode.ENQUIRY_ONLY)
        listOf("", "enquiry_only", "ONLINE", "unknown-sensitive-value").forEach { value ->
            assertThatThrownBy { beans.bookingMode(value) }.hasMessage("TOURS_OPERATOR_BOOKING_MODE must be ONLINE_PAYMENT or ENQUIRY_ONLY")
        }
        assertThatThrownBy { beans.mockPaymobClient(BookingMode.ENQUIRY_ONLY) }.hasMessageContaining("must not use a mock")
    }
}
