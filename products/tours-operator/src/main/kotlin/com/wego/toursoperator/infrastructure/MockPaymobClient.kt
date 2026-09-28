package com.wego.toursoperator.infrastructure

import com.wego.toursoperator.application.PaymobClient
import com.wego.toursoperator.application.PaymobOrderItem
import com.wego.toursoperator.application.PaymobOrderResult
import com.wego.toursoperator.application.PaymobRefundResult
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong

/**
 * Mock PaymobClient for E2E and integration testing when real Paymob
 * sandbox credentials are not yet available.
 *
 * Activated by setting:
 *   tours-operator.paymob.mock-enabled=true
 *   (or env var TOURS_OPERATOR_PAYMOB_MOCK_ENABLED=true)
 *
 * Behaviour:
 * - createOrder: returns a deterministic but unique orderId "MOCK-ORDER-{n}"
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
    private val orderCounter = AtomicLong(1)

    override fun createOrder(
        merchantRefNumber: String,
        amountCents: Long,
        currencyCode: String,
        items: List<PaymobOrderItem>,
    ): PaymobOrderResult {
        val orderId = "MOCK-ORDER-${orderCounter.getAndIncrement()}"
        log.info("MockPaymobClient.createOrder: ref={} → orderId={}", merchantRefNumber, orderId)
        return PaymobOrderResult.Success(orderId)
    }

    override fun buildCheckoutUrl(paymobOrderId: String): String {
        // Shape matches the real stub: {iframeBaseUrl}?payment_token={integrationId}_{orderId}
        // We use "MOCKINTEG" (no underscore) as the integration ID so the E2E spec can
        // safely split on "_" and take slice(1).join("_") to recover the orderId,
        // which itself uses "-" as separator (MOCK-ORDER-n).
        return "https://mock.paymob.test/iframe?payment_token=MOCKINTEG_$paymobOrderId"
    }

    override fun verifyWebhookSignature(
        payload: Map<String, String>,
        receivedHmac: String,
    ): Boolean {
        val valid = receivedHmac == "valid-hmac"
        if (!valid) {
            log.warn("MockPaymobClient.verifyWebhookSignature: rejected HMAC={}", receivedHmac)
        }
        return valid
    }

    override fun refund(
        paymobTransactionId: String,
        amountCents: Long,
    ): PaymobRefundResult {
        log.info("MockPaymobClient.refund: txn={} amount={}", paymobTransactionId, amountCents)
        return PaymobRefundResult.Success
    }
}
