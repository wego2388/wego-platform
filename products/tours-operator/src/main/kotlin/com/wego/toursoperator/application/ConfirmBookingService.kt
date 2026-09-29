package com.wego.toursoperator.application

import com.wego.events.IntegrationEventEnvelope
import com.wego.events.OutboxWriter
import com.wego.toursoperator.domain.Booking
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.BookingStatus
import com.wego.toursoperator.domain.PaymentStatus
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed class ConfirmBookingResult {
    data class Confirmed(
        val booking: Booking,
    ) : ConfirmBookingResult()

    data object NotFound : ConfirmBookingResult()

    /** Already confirmed — safe replay for Paymob webhook retries. */
    data class AlreadyConfirmed(
        val booking: Booking,
    ) : ConfirmBookingResult()

    data object CannotConfirm : ConfirmBookingResult()

    /** A booking can never be manually confirmed without captured provider truth. */
    data object PaymentNotCaptured : ConfirmBookingResult()
}

/**
 * Called by HandlePaymobWebhookService on successful HMAC-verified payment.
 * AlreadyConfirmed is a safe replay path — Paymob may deliver the
 * webhook more than once. The caller must treat it as success.
 */
class ConfirmBookingService(
    private val bookingRepository: BookingRepository,
    private val paymentRepository: PaymentRepository,
    private val bookingAuditRecorder: BookingAuditRecorder,
    private val outboxWriter: OutboxWriter,
    private val transactionRunner: TransactionRunner,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    fun confirm(
        bookingId: BookingId,
        correlationId: UUID?,
    ): ConfirmBookingResult =
        transactionRunner.runInTransaction {
            val booking =
                bookingRepository.findByIdForUpdate(bookingId)
                    ?: return@runInTransaction ConfirmBookingResult.NotFound

            if (booking.status == BookingStatus.CONFIRMED) {
                return@runInTransaction ConfirmBookingResult.AlreadyConfirmed(booking)
            }

            if (booking.status != BookingStatus.NEW) {
                return@runInTransaction ConfirmBookingResult.CannotConfirm
            }

            val payment =
                paymentRepository.findByBookingIdForUpdate(bookingId)
                    ?: return@runInTransaction ConfirmBookingResult.PaymentNotCaptured
            if (payment.status != PaymentStatus.PAID) {
                return@runInTransaction ConfirmBookingResult.PaymentNotCaptured
            }

            val now = Instant.now(clock)
            booking.confirm(now)
            bookingRepository.save(booking)
            bookingAuditRecorder.recordConfirmed(booking.id, null, now, correlationId)
            outboxWriter.write(confirmedEnvelope(booking, now, correlationId))

            ConfirmBookingResult.Confirmed(booking)
        }

    private fun confirmedEnvelope(
        booking: Booking,
        now: Instant,
        correlationId: UUID?,
    ): IntegrationEventEnvelope =
        IntegrationEventEnvelope(
            id = UUID.randomUUID(),
            aggregateType = "tours-operator.booking",
            aggregateId = booking.id.value.toString(),
            eventType = "tours-operator.booking.confirmed",
            eventVersion = 1,
            payloadJson =
                objectMapper.writeValueAsString(
                    mapOf(
                        "bookingId" to booking.id.value.toString(),
                        "reference" to booking.reference,
                        "tourId" to booking.tourId.value.toString(),
                        "tourDate" to booking.tourDate.toString(),
                        "timeSlot" to booking.timeSlot.name,
                        "totalEur" to
                            booking.pricing.totalEur.amount
                                .toPlainString(),
                        "customerPhone" to booking.customer.phone,
                        "locale" to booking.locale,
                    ),
                ),
            occurredAt = now,
            correlationId = correlationId,
            causationId = null,
        )
}
