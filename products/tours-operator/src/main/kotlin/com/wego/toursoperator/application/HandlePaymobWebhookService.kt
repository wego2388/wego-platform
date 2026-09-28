package com.wego.toursoperator.application

import com.wego.events.IntegrationEventEnvelope
import com.wego.events.OutboxWriter
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.PaymentStatus
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * Paymob webhook payload fields used for verification and processing.
 */
data class PaymobWebhookPayload(
    /** Raw JSON string of the full webhook body — stored verbatim for audit. */
    val rawJson: String,
    /** Extracted fields used for verification and routing. */
    val orderId: String,
    val transactionId: String,
    /** "true" / "false" as string — Paymob sends booleans as strings. */
    val success: String,
    val pending: String,
    val isRefund: String,
    val amountCents: Long,
    val currencyCode: String,
    /** HMAC-SHA512 from the `hmac` query param — NOT from the body. */
    val hmac: String,
    val providerResponseCode: String?,
    val providerResponseMessage: String?,
)

sealed class HandlePaymobWebhookResult {
    data object PaymentConfirmed : HandlePaymobWebhookResult()
    data object PaymentFailed : HandlePaymobWebhookResult()
    data object RefundRecorded : HandlePaymobWebhookResult()
    data object AlreadyProcessed : HandlePaymobWebhookResult()
    data object InvalidSignature : HandlePaymobWebhookResult()
    data object OrderNotFound : HandlePaymobWebhookResult()
    data object AmountMismatch : HandlePaymobWebhookResult()
}

/**
 * Processes a verified Paymob transaction webhook.
 *
 * Security invariants:
 * 1. HMAC-SHA512 signature verified FIRST — any invalid signature returns
 *    InvalidSignature and the rest of the payload is ignored.
 * 2. Amount verification — the webhook amount must match the server-side
 *    payment record amount. A mismatch is logged and rejected.
 * 3. Idempotent — if the payment is already PAID/FAILED, returns AlreadyProcessed.
 * 4. Order ID routing — never accepts a booking/payment ID from the client.
 *    Routing is always via the Paymob orderId we generated.
 */
class HandlePaymobWebhookService(
    private val paymentRepository: PaymentRepository,
    private val bookingRepository: BookingRepository,
    private val confirmBookingService: ConfirmBookingService,
    private val paymobClient: PaymobClient,
    private val outboxWriter: OutboxWriter,
    private val transactionRunner: TransactionRunner,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    fun handle(payload: PaymobWebhookPayload): HandlePaymobWebhookResult {
        // ── 1. Verify HMAC signature ──────────────────────────────────────────
        val signatureFields = buildSignatureFields(payload)
        if (!paymobClient.verifyWebhookSignature(signatureFields, payload.hmac)) {
            return HandlePaymobWebhookResult.InvalidSignature
        }

        // ── 2. Look up payment by Paymob order ID ─────────────────────────────
        return transactionRunner.runInTransaction {
            val payment = paymentRepository.findByPaymobOrderIdForUpdate(payload.orderId)
                ?: return@runInTransaction HandlePaymobWebhookResult.OrderNotFound

            // ── 3. Idempotency check ──────────────────────────────────────────
            if (payment.status != PaymentStatus.PENDING) {
                return@runInTransaction HandlePaymobWebhookResult.AlreadyProcessed
            }

            // ── 4. Amount verification ────────────────────────────────────────
            if (payload.amountCents != payment.amountMinorUnits) {
                // Log mismatch to outbox for alerting — still return mismatch
                outboxWriter.write(amountMismatchEnvelope(payment.bookingId, payload, payment.amountMinorUnits))
                return@runInTransaction HandlePaymobWebhookResult.AmountMismatch
            }

            val now = Instant.now(clock)
            val providerStatus = payload.providerResponseMessage ?: "UNKNOWN"

            // ── 5. Handle refund webhook ──────────────────────────────────────
            if (payload.isRefund == "true") {
                payment.markRefunded(payload.rawJson, now)
                paymentRepository.save(payment)
                return@runInTransaction HandlePaymobWebhookResult.RefundRecorded
            }

            // ── 6. Handle success / failure ───────────────────────────────────
            if (payload.success == "true" && payload.pending == "false") {
                payment.markPaid(payload.transactionId, providerStatus, payload.rawJson, now)
                paymentRepository.save(payment)

                // Confirm the booking — AlreadyConfirmed is safe (idempotent)
                confirmBookingService.confirm(payment.bookingId, UUID.randomUUID())

                HandlePaymobWebhookResult.PaymentConfirmed
            } else {
                payment.markFailed(payload.transactionId, providerStatus, payload.rawJson, now)
                paymentRepository.save(payment)
                HandlePaymobWebhookResult.PaymentFailed
            }
        }
    }

    /**
     * Builds the field map Paymob uses for HMAC-SHA512 computation.
     * Field order and values must match Paymob's documentation exactly.
     */
    private fun buildSignatureFields(p: PaymobWebhookPayload): Map<String, String> =
        mapOf(
            "amount_cents" to p.amountCents.toString(),
            "created_at" to "",          // Paymob includes created_at; value varies
            "currency" to p.currencyCode,
            "error_occured" to "false",
            "has_parent_transaction" to "false",
            "id" to p.transactionId,
            "integration_id" to "",      // filled by real implementation from config
            "is_3d_secure" to "false",
            "is_auth" to "false",
            "is_capture" to "false",
            "is_refunded" to p.isRefund,
            "is_standalone_payment" to "true",
            "is_voided" to "false",
            "order" to p.orderId,
            "owner" to "",
            "pending" to p.pending,
            "source_data.pan" to "",
            "source_data.sub_type" to "",
            "source_data.type" to "",
            "success" to p.success,
        )

    private fun amountMismatchEnvelope(
        bookingId: BookingId,
        payload: PaymobWebhookPayload,
        expectedCents: Long,
    ): IntegrationEventEnvelope =
        IntegrationEventEnvelope(
            id = UUID.randomUUID(),
            aggregateType = "tours-operator.payment",
            aggregateId = payload.orderId,
            eventType = "tours-operator.payment.amount-mismatch",
            eventVersion = 1,
            payloadJson =
                objectMapper.writeValueAsString(
                    mapOf(
                        "bookingId" to bookingId.value.toString(),
                        "paymobOrderId" to payload.orderId,
                        "receivedCents" to payload.amountCents,
                        "expectedCents" to expectedCents,
                    ),
                ),
            occurredAt = Instant.now(clock),
            correlationId = null,
            causationId = null,
        )
}
