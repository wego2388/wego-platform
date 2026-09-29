package com.wego.toursoperator.infrastructure

import com.wego.toursoperator.application.PaymobCheckoutCommand
import com.wego.toursoperator.application.PaymobCheckoutResult
import com.wego.toursoperator.application.PaymobClient
import com.wego.toursoperator.application.PaymobRefundResult
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.ObjectMapper
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
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

    private val restClient: RestClient =
        RestClient
            .builder()
            .baseUrl(config.baseUrl)
            .build()

    // ── Intention / hosted checkout creation ─────────────────────────────────

    override fun createCheckout(command: PaymobCheckoutCommand): PaymobCheckoutResult {
        return try {
            val names =
                command.billing.fullName
                    .trim()
                    .split(Regex("\\s+"), limit = 2)
            val billingData =
                mutableMapOf<String, Any>(
                    "first_name" to names.first(),
                    "last_name" to names.getOrElse(1) { names.first() },
                    "phone_number" to command.billing.phone,
                    "city" to config.billingCity,
                    "country" to config.billingCountryCode,
                )
            command.billing.email
                ?.takeIf(String::isNotBlank)
                ?.let { billingData["email"] = it }

            val integration: Any = config.integrationId.toLongOrNull() ?: config.integrationId
            val intentionRequest =
                mapOf(
                    "amount" to command.amountCents,
                    "currency" to command.currencyCode,
                    "payment_methods" to listOf(integration),
                    "items" to
                        command.items.map { item ->
                            mapOf(
                                "name" to item.name,
                                "amount" to item.amountCents,
                                "description" to item.name,
                                "quantity" to item.quantity,
                            )
                        },
                    "billing_data" to billingData,
                    "special_reference" to command.merchantRefNumber,
                    "expiration" to config.checkoutExpirationSeconds,
                    "notification_url" to config.notificationUrl,
                    "redirection_url" to config.redirectionUrl,
                )

            val response =
                restClient
                    .post()
                    .uri("/v1/intention/")
                    .header("Authorization", "Token ${config.secretKey}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(intentionRequest))
                    .retrieve()
                    .body(Map::class.java)

            @Suppress("UNCHECKED_CAST")
            val body = response as? Map<String, Any>
            val orderId =
                body?.get("intention_order_id")?.toString()
                    ?: return PaymobCheckoutResult.Failure("Intention response missing order id")
            val checkoutToken =
                body["client_secret"]?.toString()
                    ?: return PaymobCheckoutResult.Failure("Intention response missing client secret")

            PaymobCheckoutResult.Success(orderId, checkoutToken)
        } catch (ex: RestClientException) {
            log.error("Paymob intention creation failed", ex)
            PaymobCheckoutResult.Failure("Provider checkout creation failed")
        }
    }

    // ── Checkout URL ──────────────────────────────────────────────────────────

    override fun buildCheckoutUrl(checkoutToken: String): String =
        "${config.checkoutBaseUrl}?publicKey=${encode(config.publicKey)}&clientSecret=${encode(checkoutToken)}"

    override fun acceptsWebhookIdentity(
        integrationId: String,
        ownerId: String,
    ): Boolean {
        val integrationMatches =
            MessageDigest.isEqual(
                config.integrationId.toByteArray(StandardCharsets.UTF_8),
                integrationId.toByteArray(StandardCharsets.UTF_8),
            )
        val ownerMatches =
            MessageDigest.isEqual(
                config.ownerId.toByteArray(StandardCharsets.UTF_8),
                ownerId.toByteArray(StandardCharsets.UTF_8),
            )
        return integrationMatches && ownerMatches
    }

    // ── Webhook HMAC verification ─────────────────────────────────────────────

    override fun verifyWebhookSignature(
        payload: Map<String, String>,
        receivedHmac: String,
    ): Boolean {
        // Paymob HMAC-SHA512: concatenate specific field values in order, then SHA512 with the HMAC secret.
        val hmacFields =
            listOf(
                "amount_cents",
                "created_at",
                "currency",
                "error_occured",
                "has_parent_transaction",
                "id",
                "integration_id",
                "is_3d_secure",
                "is_auth",
                "is_capture",
                "is_refunded",
                "is_standalone_payment",
                "is_voided",
                "order",
                "owner",
                "pending",
                "source_data.pan",
                "source_data.sub_type",
                "source_data.type",
                "success",
            )
        val concatenated = hmacFields.joinToString("") { payload[it] ?: "" }
        val computed = hmacSha512(concatenated, config.hmacSecret)
        val received = receivedHmac.hexToBytesOrNull() ?: return false
        return MessageDigest.isEqual(computed, received)
    }

    // ── Refund ────────────────────────────────────────────────────────────────

    override fun refund(
        paymobTransactionId: String,
        amountCents: Long,
    ): PaymobRefundResult {
        return try {
            val authToken = authenticate() ?: return PaymobRefundResult.Failure("Authentication failed")

            val refundRequest =
                mapOf(
                    "auth_token" to authToken,
                    "transaction_id" to paymobTransactionId,
                    "amount_cents" to amountCents,
                )

            val response =
                restClient
                    .post()
                    .uri("/api/acceptance/void_refund/refund")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(refundRequest))
                    .retrieve()
                    .body(Map::class.java)

            @Suppress("UNCHECKED_CAST")
            val body = response as? Map<String, Any>
            val success = body?.get("success")?.toString()?.toBoolean() ?: false
            if (success) {
                PaymobRefundResult.Success
            } else {
                PaymobRefundResult.Failure("Refund declined by provider")
            }
        } catch (ex: RestClientException) {
            log.error("Paymob refund failed", ex)
            PaymobRefundResult.Failure("Provider refund failed")
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun authenticate(): String? =
        try {
            val authRequest = mapOf("api_key" to config.apiKey)
            val response =
                restClient
                    .post()
                    .uri("/api/auth/tokens")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(authRequest))
                    .retrieve()
                    .body(Map::class.java)

            @Suppress("UNCHECKED_CAST")
            (response as? Map<String, Any>)?.get("token")?.toString()
        } catch (ex: RestClientException) {
            log.error("Paymob authentication failed", ex)
            null
        }

    private fun hmacSha512(
        data: String,
        secret: String,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA512"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }

    private fun String.hexToBytesOrNull(): ByteArray? {
        if (length % 2 != 0 || any { it.digitToIntOrNull(16) == null }) return null
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}

/**
 * Paymob configuration — all values must come from environment variables.
 * Never commit real credentials.
 *
 * @param baseUrl Paymob regional origin (for Egypt: https://accept.paymob.com)
 */
data class PaymobConfig(
    val baseUrl: String,
    val secretKey: String,
    val publicKey: String,
    val apiKey: String,
    val integrationId: String,
    val ownerId: String,
    val hmacSecret: String,
    val checkoutBaseUrl: String,
    val notificationUrl: String,
    val redirectionUrl: String,
    val billingCity: String,
    val billingCountryCode: String,
    val checkoutExpirationSeconds: Int,
)
