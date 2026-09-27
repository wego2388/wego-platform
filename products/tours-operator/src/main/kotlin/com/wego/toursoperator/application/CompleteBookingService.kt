package com.wego.toursoperator.application

import com.wego.events.IntegrationEventEnvelope
import com.wego.events.OutboxWriter
import com.wego.toursoperator.domain.Booking
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.BookingStatus
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed class CompleteBookingResult {
    data class Completed(
        val booking: Booking,
    ) : CompleteBookingResult()

    data object NotFound : CompleteBookingResult()

    data object CannotComplete : CompleteBookingResult()
}

class CompleteBookingService(
    private val bookingRepository: BookingRepository,
    private val bookingAuditRecorder: BookingAuditRecorder,
    private val outboxWriter: OutboxWriter,
    private val transactionRunner: TransactionRunner,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    fun complete(
        bookingId: BookingId,
        actorUserId: UUID,
        correlationId: UUID?,
    ): CompleteBookingResult =
        transactionRunner.runInTransaction {
            val booking =
                bookingRepository.findByIdForUpdate(bookingId)
                    ?: return@runInTransaction CompleteBookingResult.NotFound

            if (booking.status != BookingStatus.CONFIRMED) {
                return@runInTransaction CompleteBookingResult.CannotComplete
            }

            val now = Instant.now(clock)
            booking.complete(now)
            bookingRepository.save(booking)
            bookingAuditRecorder.recordCompleted(booking.id, actorUserId, now, correlationId)
            outboxWriter.write(completedEnvelope(booking, now, correlationId))

            CompleteBookingResult.Completed(booking)
        }

    private fun completedEnvelope(
        booking: Booking,
        now: Instant,
        correlationId: UUID?,
    ): IntegrationEventEnvelope =
        IntegrationEventEnvelope(
            id = UUID.randomUUID(),
            aggregateType = "tours-operator.booking",
            aggregateId = booking.id.value.toString(),
            eventType = "tours-operator.booking.completed",
            eventVersion = 1,
            payloadJson =
                objectMapper.writeValueAsString(
                    mapOf(
                        "bookingId" to booking.id.value.toString(),
                        "reference" to booking.reference,
                        "tourId" to booking.tourId.value.toString(),
                    ),
                ),
            occurredAt = now,
            correlationId = correlationId,
            causationId = null,
        )
}
