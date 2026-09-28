package com.wego.toursoperator.api

import com.wego.toursoperator.application.HandlePaymobWebhookResult
import com.wego.toursoperator.application.HandlePaymobWebhookService
import com.wego.toursoperator.application.PaymobWebhookPayload
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Receives Paymob transaction callbacks (webhooks).
 *
 * Security model:
 * - No authentication required (Paymob cannot authenticate as our users).
 * - Protection is entirely via HMAC-SHA512 signature verification in
 *   HandlePaymobWebhookService.
 * - Invalid signature → 400 (do not expose 401/403 to avoid information leakage).
 * - The endpoint path is not guessable (includes a product-specific segment).
 *
 * Paymob delivers the HMAC in the `hmac` query parameter, NOT in the body.
 * The body is the raw JSON transaction object.
 *
 * Paymob may retry delivery — all processing is idempotent.
 */
@RestController("toursOperatorPaymobWebhookController")
@RequestMapping("/api/v1/tours-operator/payments/paymob-callback")
class PaymobWebhookController(
    private val handlePaymobWebhookService: HandlePaymobWebhookService,
) {
    private val log = LoggerFactory.getLogger(PaymobWebhookController::class.java)

    @PostMapping
    fun handleCallback(
        @RequestParam hmac: String,
        @RequestBody body: Map<String, Any>,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        val rawJson = extractRawJson(body)

        val payload = try {
            parsePayload(body, hmac, rawJson)
        } catch (ex: Exception) {
            log.warn("PaymobWebhookController: failed to parse payload: ${ex.message}")
            return ResponseEntity.badRequest().body(mapOf("error" to "invalid_payload"))
        }

        return when (val result = handlePaymobWebhookService.handle(payload)) {
            HandlePaymobWebhookResult.PaymentConfirmed -> {
                log.info("PaymobWebhookController: payment confirmed for order ${payload.orderId}")
                ResponseEntity.ok(mapOf("status" to "confirmed"))
            }
            HandlePaymobWebhookResult.PaymentFailed -> {
                log.info("PaymobWebhookController: payment failed for order ${payload.orderId}")
                ResponseEntity.ok(mapOf("status" to "failed"))
            }
            HandlePaymobWebhookResult.RefundRecorded -> {
                log.info("PaymobWebhookController: refund recorded for order ${payload.orderId}")
                ResponseEntity.ok(mapOf("status" to "refunded"))
            }
            HandlePaymobWebhookResult.AlreadyProcessed -> {
                // Idempotent — return 200 so Paymob stops retrying.
                ResponseEntity.ok(mapOf("status" to "already_processed"))
            }
            HandlePaymobWebhookResult.InvalidSignature -> {
                log.warn("PaymobWebhookController: invalid HMAC signature for order ${payload.orderId}")
                ResponseEntity.badRequest().body(mapOf("error" to "invalid_signature"))
            }
            HandlePaymobWebhookResult.OrderNotFound -> {
                log.warn("PaymobWebhookController: order not found: ${payload.orderId}")
                // Return 200 to prevent Paymob retries for unknown orders.
                ResponseEntity.ok(mapOf("status" to "order_not_found"))
            }
            HandlePaymobWebhookResult.AmountMismatch -> {
                log.error(
                    "PaymobWebhookController: AMOUNT MISMATCH for order ${payload.orderId} " +
                        "— received ${payload.amountCents} cents",
                )
                ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(mapOf("error" to "amount_mismatch"))
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parsePayload(
        body: Map<String, Any>,
        hmac: String,
        rawJson: String,
    ): PaymobWebhookPayload {
        val obj = body["obj"] as? Map<String, Any>
            ?: error("Missing 'obj' in webhook body")
        val order = obj["order"] as? Map<String, Any>
            ?: error("Missing 'order' in webhook obj")
        val sourceData = obj["source_data"] as? Map<String, Any> ?: emptyMap()

        return PaymobWebhookPayload(
            rawJson = rawJson,
            orderId = order["id"]?.toString() ?: error("Missing order.id"),
            transactionId = obj["id"]?.toString() ?: error("Missing transaction id"),
            success = obj["success"]?.toString() ?: "false",
            pending = obj["pending"]?.toString() ?: "false",
            isRefund = obj["is_refunded"]?.toString() ?: "false",
            amountCents = obj["amount_cents"]?.toString()?.toLong() ?: 0L,
            currencyCode = obj["currency"]?.toString() ?: "EUR",
            hmac = hmac,
            providerResponseCode = obj["data"]?.let {
                (it as? Map<String, Any>)?.get("gateway_integration_pk")?.toString()
            },
            providerResponseMessage = obj["data"]?.let {
                (it as? Map<String, Any>)?.get("message")?.toString()
            },
            createdAt       = obj["created_at"]?.toString() ?: "",
            integrationId   = obj["integration_id"]?.toString() ?: "",
            sourceDataPan       = sourceData["pan"]?.toString() ?: "",
            sourceDataSubType   = sourceData["sub_type"]?.toString() ?: "",
            sourceDataType      = sourceData["type"]?.toString() ?: "",
        )
    }

    private fun extractRawJson(body: Map<String, Any>): String {
        return try {
            tools.jackson.databind.ObjectMapper().writeValueAsString(body)
        } catch (ex: Exception) {
            "{}"
        }
    }
}
