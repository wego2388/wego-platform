package com.wego.divers.application

import com.wego.divers.domain.Booking
import com.wego.divers.domain.BookingId
import com.wego.divers.domain.BookingStatus
import com.wego.events.IntegrationEventEnvelope
import com.wego.events.OutboxWriter
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed class CancelBookingResult {
    data class Cancelled(
        val booking: Booking,
    ) : CancelBookingResult()

    data object NotFound : CancelBookingResult()

    data object AlreadyCancelled : CancelBookingResult()
}

class CancelBookingService(
    private val bookingRepository: BookingRepository,
    private val bookingAuditRecorder: BookingAuditRecorder,
    private val outboxWriter: OutboxWriter,
    private val transactionRunner: TransactionRunner,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    fun cancel(
        id: BookingId,
        actorUserId: UUID,
        reason: String,
        correlationId: UUID?,
    ): CancelBookingResult =
        transactionRunner.runInTransaction {
            // findByIdForUpdate, not findById: two concurrent cancel
            // requests for the same booking must serialize, or both could
            // read status == CONFIRMED before either writes CANCELLED.
            val booking =
                bookingRepository.findByIdForUpdate(id)
                    ?: return@runInTransaction CancelBookingResult.NotFound

            if (booking.status != BookingStatus.CONFIRMED) {
                return@runInTransaction CancelBookingResult.AlreadyCancelled
            }

            val now = Instant.now(clock)
            booking.cancel(now, reason)
            bookingRepository.save(booking)
            bookingAuditRecorder.recordBookingCancelled(booking.id, actorUserId, now, reason, correlationId)
            outboxWriter.write(cancelledEnvelope(booking, now, reason, correlationId))

            CancelBookingResult.Cancelled(booking)
        }

    private fun cancelledEnvelope(
        booking: Booking,
        now: Instant,
        reason: String,
        correlationId: UUID?,
    ): IntegrationEventEnvelope =
        IntegrationEventEnvelope(
            id = UUID.randomUUID(),
            aggregateType = "booking",
            aggregateId = booking.id.value.toString(),
            eventType = "booking.cancelled",
            eventVersion = 1,
            payloadJson =
                objectMapper.writeValueAsString(
                    mapOf(
                        "bookingId" to booking.id.value.toString(),
                        "offeringId" to booking.offeringId.value.toString(),
                        "reason" to reason,
                    ),
                ),
            occurredAt = now,
            correlationId = correlationId,
            causationId = null,
        )
}
