package com.wego.toursoperator.api

import com.wego.toursoperator.application.HandlePaymobWebhookResult
import com.wego.toursoperator.application.HandlePaymobWebhookService
import com.wego.toursoperator.application.PaymobWebhookPayload
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
    ): ResponseEntity<Any> {
        val payload =
            try {
                parsePayload(body, hmac)
            } catch (_: Exception) {
                // Never copy attacker-controlled parse errors into production logs.
                log.warn("PaymobWebhookController: rejected malformed payload")
                return ResponseEntity.badRequest().body(mapOf("error" to "invalid_payload"))
            }

        return when (val result = handlePaymobWebhookService.handle(payload)) {
            HandlePaymobWebhookResult.PaymentConfirmed -> {
                log.info("PaymobWebhookController: payment confirmed")
                ResponseEntity.ok(mapOf("status" to "confirmed"))
            }
            HandlePaymobWebhookResult.PaymentFailed -> {
                log.info("PaymobWebhookController: payment failed")
                ResponseEntity.ok(mapOf("status" to "failed"))
            }
            HandlePaymobWebhookResult.PendingAcknowledged ->
                ResponseEntity.ok(mapOf("status" to "pending"))
            HandlePaymobWebhookResult.RefundRecorded -> {
                log.info("PaymobWebhookController: refund recorded")
                ResponseEntity.ok(mapOf("status" to "refunded"))
            }
            HandlePaymobWebhookResult.ReviewRequired -> {
                log.error("PaymobWebhookController: payment requires reconciliation")
                ResponseEntity.ok(mapOf("status" to "review_required"))
            }
            HandlePaymobWebhookResult.AlreadyProcessed -> {
                // Idempotent — return 200 so Paymob stops retrying.
                ResponseEntity.ok(mapOf("status" to "already_processed"))
            }
            HandlePaymobWebhookResult.InvalidSignature -> {
                log.warn("PaymobWebhookController: invalid HMAC signature")
                ResponseEntity.badRequest().body(mapOf("error" to "invalid_signature"))
            }
            HandlePaymobWebhookResult.IntegrationMismatch -> {
                log.warn("PaymobWebhookController: integration mismatch")
                ResponseEntity.badRequest().body(mapOf("error" to "integration_mismatch"))
            }
            HandlePaymobWebhookResult.OrderNotFound -> {
                log.warn("PaymobWebhookController: provider order not found")
                // Return 200 to prevent Paymob retries for unknown orders.
                ResponseEntity.ok(mapOf("status" to "order_not_found"))
            }
            HandlePaymobWebhookResult.AmountMismatch -> {
                log.error(
                    "PaymobWebhookController: payment amount mismatch",
                )
                ResponseEntity
                    .status(HttpStatus.UNPROCESSABLE_CONTENT)
                    .body(mapOf("error" to "amount_mismatch"))
            }
            HandlePaymobWebhookResult.CurrencyMismatch -> {
                log.error("PaymobWebhookController: payment currency mismatch")
                ResponseEntity
                    .status(HttpStatus.UNPROCESSABLE_CONTENT)
                    .body(mapOf("error" to "currency_mismatch"))
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parsePayload(
        body: Map<String, Any>,
        hmac: String,
    ): PaymobWebhookPayload {
        val obj =
            body["obj"] as? Map<String, Any>
                ?: error("Missing 'obj' in webhook body")
        val order =
            obj["order"] as? Map<String, Any>
                ?: error("Missing 'order' in webhook obj")
        val sourceData = obj["source_data"] as? Map<String, Any> ?: emptyMap()

        return PaymobWebhookPayload(
            orderId = order["id"]?.toString() ?: error("Missing order.id"),
            transactionId = obj["id"]?.toString() ?: error("Missing transaction id"),
            success = obj["success"].wireValue(),
            pending = obj["pending"].wireValue(),
            isRefund = obj["is_refunded"].wireValue(),
            amountCents = obj["amount_cents"]?.toString()?.toLong() ?: error("Missing amount_cents"),
            currencyCode = obj["currency"]?.toString() ?: error("Missing currency"),
            hmac = hmac,
            providerResponseCode =
                obj["data"]?.let {
                    (it as? Map<String, Any>)?.get("txn_response_code")?.toString()
                },
            createdAt = obj["created_at"].wireValue(),
            integrationId = obj["integration_id"]?.toString() ?: error("Missing integration_id"),
            errorOccurred = obj["error_occured"].wireValue(),
            hasParentTransaction = obj["has_parent_transaction"].wireValue(),
            is3dSecure = obj["is_3d_secure"].wireValue(),
            isAuth = obj["is_auth"].wireValue(),
            isCapture = obj["is_capture"].wireValue(),
            isStandalonePayment = obj["is_standalone_payment"].wireValue(),
            isVoided = obj["is_voided"].wireValue(),
            owner = obj["owner"].wireValue(),
            sourceDataPan = sourceData["pan"].wireValue(),
            sourceDataSubType = sourceData["sub_type"].wireValue(),
            sourceDataType = sourceData["type"].wireValue(),
        )
    }

    private fun Any?.wireValue(): String = this?.toString() ?: ""
}
