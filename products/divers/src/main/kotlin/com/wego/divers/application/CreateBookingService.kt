package com.wego.divers.application

import com.wego.divers.domain.Booking
import com.wego.divers.domain.BookingFingerprint
import com.wego.divers.domain.BookingId
import com.wego.divers.domain.BookingPricing
import com.wego.divers.domain.CustomerContact
import com.wego.divers.domain.OfferingId
import com.wego.events.IntegrationEventEnvelope
import com.wego.events.OutboxWriter
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class CreateBookingCommand(
    val offeringId: OfferingId,
    val partySize: Int,
    val customer: CustomerContact,
    val idempotencyKey: String,
    val createdByUserId: UUID,
    val correlationId: UUID?,
)

sealed class CreateBookingResult {
    data class Created(
        val booking: Booking,
    ) : CreateBookingResult()

    data class Replayed(
        val booking: Booking,
    ) : CreateBookingResult()

    data object OfferingNotFound : CreateBookingResult()

    data object OfferingClosed : CreateBookingResult()

    data object CapacityExceeded : CreateBookingResult()

    /**
     * The same `(createdByUserId, idempotencyKey)` pair was already used
     * for a request whose canonical fingerprint (offering, party size,
     * normalized customer contact) does not match this one. A true retry
     * always repeats the same request; a key collision against different
     * parameters means the client is reusing keys incorrectly — silently
     * returning the old booking would misattribute it to the wrong
     * request.
     */
    data object IdempotencyKeyConflict : CreateBookingResult()
}

/**
 * The Tier 1 invariant of this packet: capacity must never be oversold
 * under concurrent creation attempts against the same offering, and a
 * retried request with the same idempotency key and the same canonical
 * fingerprint must return the original booking rather than double-counting
 * it — while a key reused with *different* parameters is rejected as a
 * conflict rather than silently replayed. All three guarantees depend on
 * every booking-creation path going through this single entry point.
 *
 * Lock order is fixed and always the same across every call:
 * [BookingRepository.lockIdempotencyKey] (actor+key) first, then
 * [OfferingRepository.findByIdForUpdate] (the offering row) — see each
 * method's own doc comment for why. No other code path in this module
 * acquires both locks, so this fixed order is deadlock-free by
 * construction, not just by convention.
 */
class CreateBookingService(
    private val offeringRepository: OfferingRepository,
    private val bookingRepository: BookingRepository,
    private val bookingAuditRecorder: BookingAuditRecorder,
    private val outboxWriter: OutboxWriter,
    private val transactionRunner: TransactionRunner,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    fun create(command: CreateBookingCommand): CreateBookingResult =
        transactionRunner.runInTransaction {
            require(command.idempotencyKey.isNotBlank() && command.idempotencyKey.length <= Booking.MAX_IDEMPOTENCY_KEY_LENGTH) {
                "Idempotency-Key must be between 1 and ${Booking.MAX_IDEMPOTENCY_KEY_LENGTH} characters"
            }

            // Serialization point for the idempotency invariant: acquired
            // before anything else, including the offering lock, so two
            // concurrent requests sharing this actor+key — even against
            // *different* offerings — always serialize against each other
            // instead of both racing past the "no existing booking" check
            // and colliding on the unique constraint as a raw 500.
            bookingRepository.lockIdempotencyKey(command.createdByUserId, command.idempotencyKey)

            val fingerprint = BookingFingerprint.of(command.offeringId, command.partySize, command.customer)
            val existing = bookingRepository.findByIdempotencyKey(command.createdByUserId, command.idempotencyKey)
            if (existing != null) {
                return@runInTransaction if (existing.idempotencyFingerprint == fingerprint) {
                    CreateBookingResult.Replayed(existing)
                } else {
                    CreateBookingResult.IdempotencyKeyConflict
                }
            }

            val offering =
                offeringRepository.findByIdForUpdate(command.offeringId)
                    ?: return@runInTransaction CreateBookingResult.OfferingNotFound

            if (!offering.isActive) {
                return@runInTransaction CreateBookingResult.OfferingClosed
            }

            val capacity = offering.capacity
            if (capacity != null) {
                val currentPartySize = bookingRepository.sumConfirmedPartySize(offering.id)
                // Long, not Int: partySize/capacity are only bounded below (@Positive),
                // so a large-enough pair of requests could otherwise overflow an Int sum
                // to negative and silently pass the capacity check.
                if (currentPartySize.toLong() + command.partySize.toLong() > capacity.toLong()) {
                    return@runInTransaction CreateBookingResult.CapacityExceeded
                }
            }

            val now = Instant.now(clock)
            val booking =
                Booking.confirm(
                    id = BookingId.generate(),
                    offeringId = offering.id,
                    partySize = command.partySize,
                    customer = command.customer,
                    pricing = BookingPricing.forOffering(offering, command.partySize),
                    idempotencyKey = command.idempotencyKey,
                    idempotencyFingerprint = fingerprint,
                    createdByUserId = command.createdByUserId,
                    now = now,
                )
            bookingRepository.save(booking)
            bookingAuditRecorder.recordBookingCreated(booking.id, command.createdByUserId, now, command.correlationId)
            outboxWriter.write(createdEnvelope(booking, now, command.correlationId))

            CreateBookingResult.Created(booking)
        }

    private fun createdEnvelope(
        booking: Booking,
        now: Instant,
        correlationId: UUID?,
    ): IntegrationEventEnvelope =
        IntegrationEventEnvelope(
            id = UUID.randomUUID(),
            aggregateType = "booking",
            aggregateId = booking.id.value.toString(),
            eventType = "booking.created",
            eventVersion = 1,
            payloadJson =
                objectMapper.writeValueAsString(
                    mapOf(
                        "bookingId" to booking.id.value.toString(),
                        "offeringId" to booking.offeringId.value.toString(),
                        "partySize" to booking.partySize,
                        "pricingBasis" to booking.pricing.pricingBasis.name,
                        "unitPrice" to booking.pricing.unitPrice.amount,
                        "billableQuantity" to booking.pricing.billableQuantity,
                        "totalPrice" to booking.pricing.totalPrice.amount,
                        "currencyCode" to booking.pricing.totalPrice.currencyCode,
                    ),
                ),
            occurredAt = now,
            correlationId = correlationId,
            causationId = null,
        )
}
