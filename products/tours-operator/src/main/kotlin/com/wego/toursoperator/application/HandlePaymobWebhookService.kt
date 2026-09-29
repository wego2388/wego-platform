package com.wego.toursoperator.application

import com.wego.events.IntegrationEventEnvelope
import com.wego.events.OutboxWriter
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.BookingStatus
import com.wego.toursoperator.domain.PaymentId
import com.wego.toursoperator.domain.PaymentStatus
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** The exact Paymob transaction fields used for HMAC verification and routing. */
data class PaymobWebhookPayload(
    val orderId: String,
    val transactionId: String,
    val success: String,
    val pending: String,
    val isRefund: String,
    val amountCents: Long,
    val currencyCode: String,
    val hmac: String,
    val providerResponseCode: String?,
    val createdAt: String,
    val integrationId: String,
    val errorOccurred: String,
    val hasParentTransaction: String,
    val is3dSecure: String,
    val isAuth: String,
    val isCapture: String,
    val isStandalonePayment: String,
    val isVoided: String,
    val owner: String,
    val sourceDataPan: String,
    val sourceDataSubType: String,
    val sourceDataType: String,
)

sealed class HandlePaymobWebhookResult {
    data object PaymentConfirmed : HandlePaymobWebhookResult()

    data object PaymentFailed : HandlePaymobWebhookResult()

    data object PendingAcknowledged : HandlePaymobWebhookResult()

    data object RefundRecorded : HandlePaymobWebhookResult()

    data object ReviewRequired : HandlePaymobWebhookResult()

    data object AlreadyProcessed : HandlePaymobWebhookResult()

    data object InvalidSignature : HandlePaymobWebhookResult()

    data object IntegrationMismatch : HandlePaymobWebhookResult()

    data object OrderNotFound : HandlePaymobWebhookResult()

    data object AmountMismatch : HandlePaymobWebhookResult()

    data object CurrencyMismatch : HandlePaymobWebhookResult()
}

/**
 * HMAC-verified and idempotent Paymob transaction callback handler.
 *
 * Lock ordering is always booking -> payment, matching expiry and payment
 * initiation. A provider success that arrives after the booking became
 * terminal is recorded as REVIEW_REQUIRED and never silently consumes or
 * recreates inventory.
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
        if (!paymobClient.verifyWebhookSignature(buildSignatureFields(payload), payload.hmac)) {
            return HandlePaymobWebhookResult.InvalidSignature
        }
        if (!paymobClient.acceptsWebhookIdentity(payload.integrationId, payload.owner)) {
            return HandlePaymobWebhookResult.IntegrationMismatch
        }

        val candidate =
            paymentRepository.findByPaymobOrderId(payload.orderId)
                ?: return HandlePaymobWebhookResult.OrderNotFound

        return transactionRunner.runInTransaction {
            val booking =
                bookingRepository.findByIdForUpdate(candidate.bookingId)
                    ?: return@runInTransaction HandlePaymobWebhookResult.OrderNotFound
            val payment =
                paymentRepository.findByIdForUpdate(candidate.id)
                    ?: return@runInTransaction HandlePaymobWebhookResult.OrderNotFound
            if (payment.paymobOrderId != payload.orderId) {
                return@runInTransaction HandlePaymobWebhookResult.OrderNotFound
            }

            if (payload.amountCents != payment.amountMinorUnits) {
                outboxWriter.write(mismatchEnvelope(payment.id, "amount", payload.amountCents.toString()))
                return@runInTransaction HandlePaymobWebhookResult.AmountMismatch
            }
            if (payload.currencyCode.uppercase() != payment.currencyCode) {
                outboxWriter.write(mismatchEnvelope(payment.id, "currency", payload.currencyCode.uppercase()))
                return@runInTransaction HandlePaymobWebhookResult.CurrencyMismatch
            }

            val now = Instant.now(clock)
            val providerStatus =
                payload.providerResponseCode?.take(32)
                    ?: when {
                        payload.isRefund.isTrue() -> "REFUNDED"
                        payload.pending.isTrue() -> "PENDING"
                        payload.success.isTrue() -> "SUCCESS"
                        else -> "FAILED"
                    }
            val audit = redactedAudit(payload)

            if (payload.isRefund.isTrue()) {
                return@runInTransaction when (payment.status) {
                    PaymentStatus.REFUNDED -> HandlePaymobWebhookResult.AlreadyProcessed
                    PaymentStatus.PAID, PaymentStatus.REVIEW_REQUIRED -> {
                        payment.markRefunded(audit, now)
                        paymentRepository.save(payment)
                        HandlePaymobWebhookResult.RefundRecorded
                    }
                    PaymentStatus.PENDING,
                    PaymentStatus.FAILED,
                    PaymentStatus.RECONCILIATION_REQUIRED,
                    -> {
                        payment.markReviewRequired(payload.transactionId, providerStatus, audit, now)
                        payment.markRefunded(audit, now)
                        paymentRepository.save(payment)
                        HandlePaymobWebhookResult.RefundRecorded
                    }
                }
            }

            if (payload.pending.isTrue()) {
                if (payment.status in setOf(PaymentStatus.PENDING, PaymentStatus.RECONCILIATION_REQUIRED)) {
                    payment.recordPendingCallback(providerStatus, audit)
                    paymentRepository.save(payment)
                    return@runInTransaction HandlePaymobWebhookResult.PendingAcknowledged
                }
                return@runInTransaction HandlePaymobWebhookResult.AlreadyProcessed
            }

            if (payload.success.isTrue()) {
                if (payment.status in setOf(PaymentStatus.PAID, PaymentStatus.REFUNDED, PaymentStatus.REVIEW_REQUIRED)) {
                    return@runInTransaction HandlePaymobWebhookResult.AlreadyProcessed
                }

                if (booking.status != BookingStatus.NEW) {
                    payment.markReviewRequired(payload.transactionId, providerStatus, audit, now)
                    paymentRepository.save(payment)
                    outboxWriter.write(reviewRequiredEnvelope(payment.id, booking.id, booking.status))
                    return@runInTransaction HandlePaymobWebhookResult.ReviewRequired
                }

                payment.markPaid(payload.transactionId, providerStatus, audit, now)
                paymentRepository.save(payment)
                return@runInTransaction when (confirmBookingService.confirm(payment.bookingId, UUID.randomUUID())) {
                    is ConfirmBookingResult.Confirmed,
                    is ConfirmBookingResult.AlreadyConfirmed,
                    -> HandlePaymobWebhookResult.PaymentConfirmed
                    ConfirmBookingResult.NotFound,
                    ConfirmBookingResult.CannotConfirm,
                    ConfirmBookingResult.PaymentNotCaptured,
                    -> error("Paid payment could not confirm its locked NEW booking")
                }
            }

            return@runInTransaction when (payment.status) {
                PaymentStatus.PENDING, PaymentStatus.RECONCILIATION_REQUIRED -> {
                    payment.markFailed(payload.transactionId, providerStatus, audit, now)
                    paymentRepository.save(payment)
                    HandlePaymobWebhookResult.PaymentFailed
                }
                else -> HandlePaymobWebhookResult.AlreadyProcessed
            }
        }
    }

    private fun buildSignatureFields(p: PaymobWebhookPayload): Map<String, String> =
        mapOf(
            "amount_cents" to p.amountCents.toString(),
            "created_at" to p.createdAt,
            "currency" to p.currencyCode,
            "error_occured" to p.errorOccurred,
            "has_parent_transaction" to p.hasParentTransaction,
            "id" to p.transactionId,
            "integration_id" to p.integrationId,
            "is_3d_secure" to p.is3dSecure,
            "is_auth" to p.isAuth,
            "is_capture" to p.isCapture,
            "is_refunded" to p.isRefund,
            "is_standalone_payment" to p.isStandalonePayment,
            "is_voided" to p.isVoided,
            "order" to p.orderId,
            "owner" to p.owner,
            "pending" to p.pending,
            "source_data.pan" to p.sourceDataPan,
            "source_data.sub_type" to p.sourceDataSubType,
            "source_data.type" to p.sourceDataType,
            "success" to p.success,
        )

    private fun redactedAudit(p: PaymobWebhookPayload): String =
        objectMapper.writeValueAsString(
            mapOf(
                "amountCents" to p.amountCents,
                "currency" to p.currencyCode.uppercase(),
                "createdAt" to p.createdAt,
                "integrationId" to p.integrationId,
                "success" to p.success.isTrue(),
                "pending" to p.pending.isTrue(),
                "refunded" to p.isRefund.isTrue(),
                "responseCode" to p.providerResponseCode?.take(32),
            ),
        )

    private fun mismatchEnvelope(
        paymentId: PaymentId,
        field: String,
        received: String,
    ): IntegrationEventEnvelope =
        IntegrationEventEnvelope(
            id = UUID.randomUUID(),
            aggregateType = "tours-operator.payment",
            aggregateId = paymentId.value.toString(),
            eventType = "tours-operator.payment.$field-mismatch",
            eventVersion = 1,
            payloadJson = objectMapper.writeValueAsString(mapOf("field" to field, "received" to received)),
            occurredAt = Instant.now(clock),
            correlationId = null,
            causationId = null,
        )

    private fun reviewRequiredEnvelope(
        paymentId: PaymentId,
        bookingId: BookingId,
        bookingStatus: BookingStatus,
    ): IntegrationEventEnvelope =
        IntegrationEventEnvelope(
            id = UUID.randomUUID(),
            aggregateType = "tours-operator.payment",
            aggregateId = paymentId.value.toString(),
            eventType = "tours-operator.payment.review-required",
            eventVersion = 1,
            payloadJson =
                objectMapper.writeValueAsString(
                    mapOf("bookingId" to bookingId.value.toString(), "bookingStatus" to bookingStatus.name),
                ),
            occurredAt = Instant.now(clock),
            correlationId = null,
            causationId = null,
        )

    private fun String.isTrue(): Boolean = equals("true", ignoreCase = true)
}
