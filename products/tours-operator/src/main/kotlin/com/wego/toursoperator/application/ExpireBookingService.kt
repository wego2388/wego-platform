package com.wego.toursoperator.application

import com.wego.events.IntegrationEventEnvelope
import com.wego.events.OutboxWriter
import com.wego.toursoperator.domain.Booking
import com.wego.toursoperator.domain.BookingChannel
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.BookingStatus
import com.wego.toursoperator.domain.PaymentStatus
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed class ExpireBookingResult {
    data class Expired(
        val booking: Booking,
    ) : ExpireBookingResult()

    data object NotFound : ExpireBookingResult()

    data object AlreadyExpired : ExpireBookingResult()

    data object CannotExpire : ExpireBookingResult()
}

/**
 * Called by a scheduled job after the 30-minute payment window elapses.
 * Releases the slot seat back so it can be booked again.
 */
class ExpireBookingService(
    private val bookingRepository: BookingRepository,
    private val paymentRepository: PaymentRepository,
    private val slotRepository: TourSlotRepository,
    private val bookingAuditRecorder: BookingAuditRecorder,
    private val outboxWriter: OutboxWriter,
    private val transactionRunner: TransactionRunner,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    fun expire(
        bookingId: BookingId,
        correlationId: UUID?,
    ): ExpireBookingResult =
        transactionRunner.runInTransaction {
            val booking =
                bookingRepository.findByIdForUpdate(bookingId)
                    ?: return@runInTransaction ExpireBookingResult.NotFound

            if (booking.status == BookingStatus.EXPIRED) {
                return@runInTransaction ExpireBookingResult.AlreadyExpired
            }
            if (booking.status != BookingStatus.NEW) {
                return@runInTransaction ExpireBookingResult.CannotExpire
            }
            // The 30-minute window belongs to online checkout only.
            if (booking.channel != BookingChannel.ONLINE) {
                return@runInTransaction ExpireBookingResult.CannotExpire
            }

            val now = Instant.now(clock)
            val payment = paymentRepository.findByBookingIdForUpdate(booking.id)
            if (payment?.status in setOf(PaymentStatus.PAID, PaymentStatus.REVIEW_REQUIRED)) {
                return@runInTransaction ExpireBookingResult.CannotExpire
            }
            if (payment?.status == PaymentStatus.PENDING) {
                payment.markFailed(
                    transactionId = null,
                    providerStatus = "EXPIRED",
                    callbackAudit = "{\"reason\":\"payment_window_expired\"}",
                    now = now,
                )
                paymentRepository.save(payment)
            }
            booking.expire(now)

            val slot = slotRepository.findByIdForUpdate(booking.slotId)
            if (slot != null) {
                slot.release(booking.pricing.seats)
                slotRepository.save(slot)
            }

            bookingRepository.save(booking)
            bookingAuditRecorder.recordExpired(booking.id, now)
            outboxWriter.write(expiredEnvelope(booking, now, correlationId))

            ExpireBookingResult.Expired(booking)
        }

    private fun expiredEnvelope(
        booking: Booking,
        now: Instant,
        correlationId: UUID?,
    ): IntegrationEventEnvelope =
        IntegrationEventEnvelope(
            id = UUID.randomUUID(),
            aggregateType = "tours-operator.booking",
            aggregateId = booking.id.value.toString(),
            eventType = "tours-operator.booking.expired",
            eventVersion = 1,
            payloadJson =
                objectMapper.writeValueAsString(
                    mapOf(
                        "bookingId" to booking.id.value.toString(),
                        "reference" to booking.reference,
                    ),
                ),
            occurredAt = now,
            correlationId = correlationId,
            causationId = null,
        )
}
