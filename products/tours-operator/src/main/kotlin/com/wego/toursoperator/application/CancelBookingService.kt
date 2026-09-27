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

sealed class CancelBookingResult {
    data class Cancelled(
        val booking: Booking,
    ) : CancelBookingResult()

    data object NotFound : CancelBookingResult()

    data object AlreadyCancelled : CancelBookingResult()

    data object CannotCancel : CancelBookingResult()
}

class CancelBookingService(
    private val bookingRepository: BookingRepository,
    private val slotRepository: TourSlotRepository,
    private val bookingAuditRecorder: BookingAuditRecorder,
    private val outboxWriter: OutboxWriter,
    private val transactionRunner: TransactionRunner,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    fun cancel(
        bookingId: BookingId,
        reason: String,
        actorUserId: UUID?,
        correlationId: UUID?,
    ): CancelBookingResult =
        transactionRunner.runInTransaction {
            val booking =
                bookingRepository.findByIdForUpdate(bookingId)
                    ?: return@runInTransaction CancelBookingResult.NotFound

            if (booking.status == BookingStatus.CANCELLED) {
                return@runInTransaction CancelBookingResult.AlreadyCancelled
            }

            if (booking.status != BookingStatus.NEW && booking.status != BookingStatus.CONFIRMED) {
                return@runInTransaction CancelBookingResult.CannotCancel
            }

            val now = Instant.now(clock)
            val fromStatus = booking.status
            booking.cancel(now, reason)

            // Release the seat back to the slot.
            val slot = slotRepository.findByIdForUpdate(booking.slotId)
            if (slot != null) {
                slot.releaseOne()
                slotRepository.save(slot)
            }

            bookingRepository.save(booking)
            bookingAuditRecorder.recordCancelled(booking.id, fromStatus, reason, actorUserId, now, correlationId)
            outboxWriter.write(cancelledEnvelope(booking, now, correlationId))

            CancelBookingResult.Cancelled(booking)
        }

    private fun cancelledEnvelope(
        booking: Booking,
        now: Instant,
        correlationId: UUID?,
    ): IntegrationEventEnvelope =
        IntegrationEventEnvelope(
            id = UUID.randomUUID(),
            aggregateType = "tours-operator.booking",
            aggregateId = booking.id.value.toString(),
            eventType = "tours-operator.booking.cancelled",
            eventVersion = 1,
            payloadJson =
                objectMapper.writeValueAsString(
                    mapOf(
                        "bookingId" to booking.id.value.toString(),
                        "reference" to booking.reference,
                        "reason" to booking.cancellationReason,
                        "customerPhone" to booking.customer.phone,
                        "locale" to booking.locale,
                        "totalEur" to
                            booking.pricing.totalEur.amount
                                .toPlainString(),
                    ),
                ),
            occurredAt = now,
            correlationId = correlationId,
            causationId = null,
        )
}
