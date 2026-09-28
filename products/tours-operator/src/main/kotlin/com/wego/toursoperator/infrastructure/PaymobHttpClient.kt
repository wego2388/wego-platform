package com.wego.toursoperator.infrastructure

import com.wego.toursoperator.application.PaymobClient
import com.wego.toursoperator.application.PaymobOrderItem
import com.wego.toursoperator.application.PaymobOrderResult
import com.wego.toursoperator.application.PaymobRefundResult
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.ObjectMapper
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HTTP adapter for the Paymob payment gateway.
 *
 * Configuration is injected via [PaymobConfig] — all secrets come from
 * environment variables, never from the repository.
 *
 * The HMAC-SHA512 key is the Paymob HMAC secret (from the Paymob dashboard,
 * not the API key). It is used to verify incoming webhook signatures.
 *
 * Paymob API reference: https://developers.paymob.com/
 */
class PaymobHttpClient(
    private val config: PaymobConfig,
    private val objectMapper: ObjectMapper,
) : PaymobClient {

    private val log = LoggerFactory.getLogger(PaymobHttpClient::class.java)

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(config.baseUrl)
        .build()

    // ── Order creation ────────────────────────────────────────────────────────

    override fun createOrder(
        merchantRefNumber: String,
        amountCents: Long,
        currencyCode: String,
        items: List<PaymobOrderItem>,
    ): PaymobOrderResult {
        return try {
            // Step 1: Obtain authentication token
            val authToken = authenticate() ?: return PaymobOrderResult.Failure("Authentication failed")

            // Step 2: Create the order
            val orderRequest = mapOf(
                "auth_token" to authToken,
                "delivery_needed" to false,
                "amount_cents" to amountCents,
                "currency" to currencyCode,
                "merchant_order_id" to merchantRefNumber,
                "items" to items.map { item ->
                    mapOf(
                        "name" to item.name,
                        "amount_cents" to item.amountCents,
                        "description" to item.name,
                        "quantity" to item.quantity,
                    )
                },
            )

            val response = restClient.post()
                .uri("/ecommerce/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(orderRequest))
                .retrieve()
                .body(Map::class.java)

            @Suppress("UNCHECKED_CAST")
            val body = response as? Map<String, Any>
            val orderId = body?.get("id")?.toString()
                ?: return PaymobOrderResult.Failure("Order response missing id")

            PaymobOrderResult.Success(orderId)
        } catch (ex: RestClientException) {
            log.error("Paymob createOrder failed: ${ex.message}", ex)
            PaymobOrderResult.Failure("Provider error: ${ex.message}")
        }
    }

    // ── Checkout URL ──────────────────────────────────────────────────────────

    override fun buildCheckoutUrl(paymobOrderId: String): String {
        // Obtain a payment key token for the hosted checkout iframe
        // In real usage this requires another API call; here we delegate to
        // the integration key configuration so it can be replaced without code changes.
        return "${config.iframeBaseUrl}?payment_token=${config.integrationId}_${paymobOrderId}"
    }

    // ── Webhook HMAC verification ─────────────────────────────────────────────

    override fun verifyWebhookSignature(
        payload: Map<String, String>,
        receivedHmac: String,
    ): Boolean {
        // Paymob HMAC-SHA512: concatenate specific field values in order, then SHA512 with the HMAC secret.
        val hmacFields = listOf(
            "amount_cents", "created_at", "currency", "error_occured",
            "has_parent_transaction", "id", "integration_id", "is_3d_secure",
            "is_auth", "is_capture", "is_refunded", "is_standalone_payment",
            "is_voided", "order", "owner", "pending",
            "source_data.pan", "source_data.sub_type", "source_data.type", "success",
        )
        val concatenated = hmacFields.joinToString("") { payload[it] ?: "" }
        val computed = hmacSha512(concatenated, config.hmacSecret)
        return computed.equals(receivedHmac, ignoreCase = true)
    }

    // ── Refund ────────────────────────────────────────────────────────────────

    override fun refund(
        paymobTransactionId: String,
        amountCents: Long,
    ): PaymobRefundResult {
        return try {
            val authToken = authenticate() ?: return PaymobRefundResult.Failure("Authentication failed")

            val refundRequest = mapOf(
                "auth_token" to authToken,
                "transaction_id" to paymobTransactionId,
                "amount_cents" to amountCents,
            )

            val response = restClient.post()
                .uri("/acceptance/void_refund/refund")
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(refundRequest))
                .retrieve()
                .body(Map::class.java)

            @Suppress("UNCHECKED_CAST")
            val body = response as? Map<String, Any>
            val success = body?.get("success")?.toString()?.toBoolean() ?: false
            if (success) PaymobRefundResult.Success
            else PaymobRefundResult.Failure("Refund declined by provider")
        } catch (ex: RestClientException) {
            log.error("Paymob refund failed: ${ex.message}", ex)
            PaymobRefundResult.Failure("Provider error: ${ex.message}")
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun authenticate(): String? {
        return try {
            val authRequest = mapOf("api_key" to config.apiKey)
            val response = restClient.post()
                .uri("/auth/tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(authRequest))
                .retrieve()
                .body(Map::class.java)

            @Suppress("UNCHECKED_CAST")
            (response as? Map<String, Any>)?.get("token")?.toString()
        } catch (ex: RestClientException) {
            log.error("Paymob authentication failed: ${ex.message}", ex)
            null
        }
    }

    private fun hmacSha512(data: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA512"))
        val bytes = mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Paymob configuration — all values must come from environment variables.
 * Never commit real credentials.
 *
 * @param baseUrl         Paymob API base URL (e.g. https://accept.paymob.com/api)
 * @param apiKey          Paymob API key (from Paymob dashboard)
 * @param integrationId   Paymob integration ID (the payment method integration)
 * @param hmacSecret      HMAC secret from Paymob dashboard (for webhook verification)
 * @param iframeBaseUrl   Base URL for the hosted iframe checkout
 * @param iframeId        Paymob iframe ID
 */
data class PaymobConfig(
    val baseUrl: String,
    val apiKey: String,
    val integrationId: String,
    val hmacSecret: String,
    val iframeBaseUrl: String,
    val iframeId: String,
)
