package com.wego.divers.application

import com.wego.divers.domain.Booking
import com.wego.divers.domain.BookingId
import com.wego.divers.domain.PaymentTransitionResult
import com.wego.events.IntegrationEventEnvelope
import com.wego.events.OutboxWriter
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed class RefundBookingResult {
    /** The transition happened, or the booking was already REFUNDED (a safe replay). Either way, this is the current booking. */
    data class Ok(
        val booking: Booking,
    ) : RefundBookingResult()

    data object NotFound : RefundBookingResult()

    data class Rejected(
        val message: String,
    ) : RefundBookingResult()
}

/**
 * `PAID -> REFUNDED` only, and only with a non-blank reason — deliberately
 * separate from [MarkBookingPaidService] and gated on `booking:refund`, a
 * distinct permission from `booking:payment-update`, per
 * `SECURITY_MODEL.md`'s treatment of refund as a sensitive action
 * requiring its own explicit grant. Allowed even on a cancelled booking —
 * cancellation and payment are independent axes.
 */
class RefundBookingService(
    private val bookingRepository: BookingRepository,
    private val bookingAuditRecorder: BookingAuditRecorder,
    private val outboxWriter: OutboxWriter,
    private val transactionRunner: TransactionRunner,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    fun refund(
        id: BookingId,
        actorUserId: UUID,
        reason: String,
        correlationId: UUID?,
    ): RefundBookingResult =
        transactionRunner.runInTransaction {
            require(reason.isNotBlank()) { "Refund requires a non-blank reason" }

            val booking =
                bookingRepository.findByIdForUpdate(id)
                    ?: return@runInTransaction RefundBookingResult.NotFound

            when (val transition = booking.refund(reason)) {
                is PaymentTransitionResult.Rejected -> return@runInTransaction RefundBookingResult.Rejected(transition.message)
                PaymentTransitionResult.AlreadyInTargetState -> return@runInTransaction RefundBookingResult.Ok(booking)
                PaymentTransitionResult.Applied -> Unit
            }

            val now = Instant.now(clock)
            bookingRepository.save(booking)
            bookingAuditRecorder.recordPaymentRefunded(booking.id, actorUserId, now, reason, correlationId)
            outboxWriter.write(refundedEnvelope(booking, now, reason, correlationId))

            RefundBookingResult.Ok(booking)
        }

    private fun refundedEnvelope(
        booking: Booking,
        now: Instant,
        reason: String,
        correlationId: UUID?,
    ): IntegrationEventEnvelope =
        IntegrationEventEnvelope(
            id = UUID.randomUUID(),
            aggregateType = "booking",
            aggregateId = booking.id.value.toString(),
            eventType = "booking.payment_refunded",
            eventVersion = 1,
            payloadJson =
                objectMapper.writeValueAsString(mapOf("bookingId" to booking.id.value.toString(), "reason" to reason)),
            occurredAt = now,
            correlationId = correlationId,
            causationId = null,
        )
}
