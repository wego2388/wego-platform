package com.wego.toursoperator.infrastructure

import com.wego.toursoperator.application.PaymobCheckoutCommand
import com.wego.toursoperator.application.PaymobCheckoutResult
import com.wego.toursoperator.application.PaymobClient
import com.wego.toursoperator.application.PaymobRefundResult
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Mock PaymobClient for E2E and integration testing when real Paymob
 * sandbox credentials are not yet available.
 *
 * Activated by setting:
 *   tours-operator.paymob.mock-enabled=true
 *   (or env var TOURS_OPERATOR_PAYMOB_MOCK_ENABLED=true)
 *
 * Behaviour:
 * - createCheckout: returns process/restart-safe unique identifiers.
 * - buildCheckoutUrl: returns a URL containing payment_token=MOCK_INTEG_{orderId}
 *   matching the shape the E2E test expects to parse.
 * - verifyWebhookSignature: accepts exactly "valid-hmac" (the E2E constant) and
 *   rejects everything else.
 * - refund: always succeeds.
 *
 * DO NOT register this bean in production.
 * The ToursOperatorBeanConfiguration @ConditionalOnProperty guard ensures it
 * is only active when mock-enabled=true. The real PaymobHttpClient bean is
 * only registered when mock-enabled is false (the default).
 */
class MockPaymobClient : PaymobClient {
    private val log = LoggerFactory.getLogger(MockPaymobClient::class.java)

    override fun createCheckout(command: PaymobCheckoutCommand): PaymobCheckoutResult {
        // A process-local counter restarts at 1 whenever Compose recreates the
        // backend and can then collide with payment rows retained in Postgres.
        val orderId = "MOCK-ORDER-${UUID.randomUUID()}"
        // Booking references and provider order ids are recovery/payment
        // identifiers. Correlation IDs already provide request traceability;
        // never persist either identifier in application logs.
        log.info("MockPaymobClient.createCheckout: mock checkout created")
        return PaymobCheckoutResult.Success(orderId, "MOCKINTEG_$orderId")
    }

    override fun buildCheckoutUrl(checkoutToken: String): String = "https://mock.paymob.test/unified-checkout?clientSecret=$checkoutToken"

    override fun acceptsWebhookIdentity(
        integrationId: String,
        ownerId: String,
    ): Boolean = integrationId == "100001" && ownerId == "100001"

    override fun verifyWebhookSignature(
        payload: Map<String, String>,
        receivedHmac: String,
    ): Boolean {
        val valid = receivedHmac == "valid-hmac"
        if (!valid) {
            log.warn("MockPaymobClient.verifyWebhookSignature: rejected invalid signature")
        }
        return valid
    }

    override fun refund(
        paymobTransactionId: String,
        amountCents: Long,
    ): PaymobRefundResult {
        log.info("MockPaymobClient.refund: mock refund accepted")
        return PaymobRefundResult.Success
    }
}
