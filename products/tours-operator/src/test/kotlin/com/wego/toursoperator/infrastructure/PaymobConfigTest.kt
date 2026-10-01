package com.wego.toursoperator.infrastructure

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PaymobConfigTest {
    private fun config(
        hmacSecret: String = "real-hmac",
        notificationUrl: String = "https://safaritourssharm.com/api/v1/tours-operator/payments/paymob-callback",
    ) = PaymobConfig(
        baseUrl = "https://accept.paymob.com",
        secretKey = "sk",
        publicKey = "pk",
        apiKey = "ak",
        integrationId = "123",
        ownerId = "456",
        hmacSecret = hmacSecret,
        checkoutBaseUrl = "https://accept.paymob.com/unifiedcheckout/",
        notificationUrl = notificationUrl,
        redirectionUrl = "https://safaritourssharm.com/booking/payment-result",
        billingCity = "Sharm El Sheikh",
        billingCountryCode = "EG",
        checkoutExpirationSeconds = 1800,
    )

    @Test
    fun `complete configuration is production ready`() {
        assertTrue(config().missingProductionSettings().isEmpty())
    }

    @Test
    fun `placeholder or blank secrets and non-https urls are reported`() {
        assertEquals(
            listOf("TOURS_OPERATOR_PAYMOB_HMAC_SECRET"),
            config(hmacSecret = "PLACEHOLDER_HMAC_SECRET").missingProductionSettings(),
        )
        assertEquals(listOf("TOURS_OPERATOR_PAYMOB_HMAC_SECRET"), config(hmacSecret = " ").missingProductionSettings())
        assertEquals(
            listOf("TOURS_OPERATOR_PAYMOB_NOTIFICATION_URL"),
            config(notificationUrl = "https://example.invalid/x").missingProductionSettings(),
        )
        assertEquals(
            listOf("TOURS_OPERATOR_PAYMOB_NOTIFICATION_URL"),
            config(notificationUrl = "http://safaritourssharm.com/x").missingProductionSettings(),
        )
    }
}
