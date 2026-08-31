package com.wego.divers.application
import com.wego.divers.domain.Booking
import com.wego.divers.domain.BookingId
import com.wego.divers.domain.PaymentTransitionResult
import com.wego.events.IntegrationEventEnvelope
import com.wego.events.OutboxWriter
import com.wego.transaction.TransactionRunner
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed class MarkBookingPaidResult {
    /** The transition happened, or the booking was already PAID (a safe replay). Either way, this is the current booking. */
    data class Ok(
        val booking: Booking,
    ) : MarkBookingPaidResult()

    data object NotFound : MarkBookingPaidResult()

    data class Rejected(
        val message: String,
    ) : MarkBookingPaidResult()
}

/**
 * `UNPAID -> PAID` only — deliberately separate from [RefundBookingService]
 * so `booking:payment-update` (this service's required permission) can
 * never move a booking into `REFUNDED`, per `SECURITY_MODEL.md` treating
 * refund as a distinctly more sensitive action than recording payment.
 */
class MarkBookingPaidService(
    private val bookingRepository: BookingRepository,
    private val bookingAuditRecorder: BookingAuditRecorder,
    private val outboxWriter: OutboxWriter,
    private val transactionRunner: TransactionRunner,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    fun markPaid(
        id: BookingId,
        actorUserId: UUID,
        correlationId: UUID?,
    ): MarkBookingPaidResult =
        transactionRunner.runInTransaction {
            val booking =
                bookingRepository.findByIdForUpdate(id)
                    ?: return@runInTransaction MarkBookingPaidResult.NotFound

            when (val transition = booking.markPaid()) {
                is PaymentTransitionResult.Rejected -> return@runInTransaction MarkBookingPaidResult.Rejected(transition.message)
                PaymentTransitionResult.AlreadyInTargetState -> return@runInTransaction MarkBookingPaidResult.Ok(booking)
                PaymentTransitionResult.Applied -> Unit
            }

            val now = Instant.now(clock)
            bookingRepository.save(booking)
            bookingAuditRecorder.recordPaymentMarkedPaid(booking.id, actorUserId, now, correlationId)
            outboxWriter.write(paidEnvelope(booking, now, correlationId))

            MarkBookingPaidResult.Ok(booking)
        }

    private fun paidEnvelope(
        booking: Booking,
        now: Instant,
        correlationId: UUID?,
    ): IntegrationEventEnvelope =
        IntegrationEventEnvelope(
            id = UUID.randomUUID(),
            aggregateType = "booking",
            aggregateId = booking.id.value.toString(),
            eventType = "booking.payment_marked_paid",
            eventVersion = 1,
            payloadJson = objectMapper.writeValueAsString(mapOf("bookingId" to booking.id.value.toString())),
            occurredAt = now,
            correlationId = correlationId,
            causationId = null,
        )
}
