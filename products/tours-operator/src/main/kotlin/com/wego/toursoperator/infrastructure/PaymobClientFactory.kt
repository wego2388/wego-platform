package com.wego.toursoperator.infrastructure

import com.wego.toursoperator.application.BookingMode
import com.wego.toursoperator.application.PaymobClient
import tools.jackson.databind.ObjectMapper

/** No schema changes. Throws without echoing credentials or provider payloads. */
object PaymobClientFactory {
    fun realOrDisabled(
        mode: BookingMode,
        config: PaymobConfig,
        objectMapper: ObjectMapper,
        hasPaymentHistory: () -> Boolean,
    ): PaymobClient {
        val missing = config.missingProductionSettings()
        // Keep the real adapter even in enquiry mode: old signed transactions
        // and refunds must still reconcile. Never turn history into a mock.
        if (missing.isEmpty()) return PaymobHttpClient(config, objectMapper)
        check(mode == BookingMode.ENQUIRY_ONLY) {
            "Paymob is not configured (mock disabled): set ${missing.joinToString()}"
        }
        val credentials = listOf(config.secretKey, config.publicKey, config.apiKey, config.integrationId, config.ownerId, config.hmacSecret)
        check(credentials.none { it.isNotBlank() && !it.startsWith("PLACEHOLDER") }) {
            "Incomplete Paymob configuration: supply all production settings or remove all credentials for a fresh enquiry-only installation"
        }
        check(listOf(config.notificationUrl, config.redirectionUrl).none { it.isNotBlank() && !it.contains("example.invalid") }) {
            "Incomplete Paymob configuration: callback/return URLs require the complete real adapter"
        }
        check(!hasPaymentHistory()) {
            "Payment history exists: retain complete real Paymob configuration for callbacks and refunds"
        }
        return DisabledPaymobClient()
    }
}
