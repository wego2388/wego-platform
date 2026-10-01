package com.wego.toursoperator.application

import com.wego.events.IntegrationEventEnvelope
import com.wego.events.OutboxWriter
import com.wego.toursoperator.domain.Booking
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.BookingPricing
import com.wego.toursoperator.domain.CustomerContact
import com.wego.toursoperator.domain.Money
import com.wego.toursoperator.domain.PriceBasis
import com.wego.toursoperator.domain.Tour
import com.wego.toursoperator.domain.TourSlotId
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

data class CreateBookingCommand(
    val slotId: TourSlotId,
    val adultsCount: Int,
    val childrenCount: Int,
    /** Per-unit tours only: which option (e.g. "buggy", "sedan") and how many units. */
    val priceOptionCode: String? = null,
    val unitCount: Int? = null,
    val customer: CustomerContact,
    val hotelName: String,
    val hotelRoom: String?,
    val specialRequests: String?,
    val locale: String,
    /** Null for public (unauthenticated) booking creation. */
    val actorUserId: UUID?,
    val correlationId: UUID?,
)

sealed class CreateBookingResult {
    data class Created(
        val booking: Booking,
    ) : CreateBookingResult()

    data object SlotNotFound : CreateBookingResult()

    data object SlotBlocked : CreateBookingResult()

    data object SlotFullyBooked : CreateBookingResult()

    data object TourNotActive : CreateBookingResult()

    /** The request's pricing does not fit the tour (wrong option, guests don't fit the units, no child price…). */
    data class InvalidPricing(
        val code: String,
    ) : CreateBookingResult()
}

/**
 * Core invariant: a slot must never be oversold.
 * Lock order: slot row first (findByIdForUpdate), then tour (findById, read-only).
 * The slot row lock serializes concurrent bookings — two requests for the
 * same slot always queue behind each other, so both cannot pass the
 * capacity check simultaneously.
 */
class CreateBookingService(
    private val tourRepository: TourRepository,
    private val slotRepository: TourSlotRepository,
    private val bookingRepository: BookingRepository,
    private val bookingAuditRecorder: BookingAuditRecorder,
    private val outboxWriter: OutboxWriter,
    private val transactionRunner: TransactionRunner,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    fun create(command: CreateBookingCommand): CreateBookingResult =
        transactionRunner.runInTransaction {
            // Lock the slot first — serialization point for capacity.
            val slot =
                slotRepository.findByIdForUpdate(command.slotId)
                    ?: return@runInTransaction CreateBookingResult.SlotNotFound

            if (slot.isBlocked) return@runInTransaction CreateBookingResult.SlotBlocked

            val tour =
                tourRepository.findById(slot.tourId)
                    ?: return@runInTransaction CreateBookingResult.SlotNotFound

            if (!tour.isActive) return@runInTransaction CreateBookingResult.TourNotActive

            val pricing =
                when (val priced = price(tour, command)) {
                    is Priced.Ok -> priced.pricing
                    is Priced.Invalid -> return@runInTransaction CreateBookingResult.InvalidPricing(priced.code)
                }

            // reserve() checks the places left and takes one per guest, under
            // the slot row lock taken above.
            if (!slot.reserve(pricing.seats)) return@runInTransaction CreateBookingResult.SlotFullyBooked

            val now = Instant.now(clock)
            val year = now.atOffset(ZoneOffset.UTC).year
            val seq = bookingRepository.nextReferenceSequence(year)
            val reference = "STR-$year-$seq"

            val booking =
                Booking.createNew(
                    id = BookingId.generate(),
                    reference = reference,
                    tourId = tour.id,
                    slotId = slot.id,
                    tourDate = slot.date,
                    timeSlot = slot.timeSlot,
                    pricing = pricing,
                    customer = command.customer,
                    hotelName = command.hotelName,
                    hotelRoom = command.hotelRoom,
                    specialRequests = command.specialRequests,
                    locale = command.locale,
                    now = now,
                )

            slotRepository.save(slot)
            bookingRepository.save(booking)
            bookingAuditRecorder.recordCreated(booking.id, command.actorUserId, now, command.correlationId)
            outboxWriter.write(createdEnvelope(booking, now, command.correlationId))

            CreateBookingResult.Created(booking)
        }

    private sealed interface Priced {
        data class Ok(
            val pricing: BookingPricing,
        ) : Priced

        data class Invalid(
            val code: String,
        ) : Priced
    }

    /** The server alone decides the price, from the tour as stored now. */
    private fun price(
        tour: Tour,
        command: CreateBookingCommand,
    ): Priced =
        when (tour.priceBasis) {
            PriceBasis.PER_PERSON -> {
                if (command.priceOptionCode != null || command.unitCount != null) {
                    Priced.Invalid("price_option_not_applicable")
                } else if (command.childrenCount > 0 && tour.priceChildCents == null) {
                    Priced.Invalid("child_price_not_available")
                } else {
                    Priced.Ok(
                        BookingPricing.compute(
                            adultsCount = command.adultsCount,
                            childrenCount = command.childrenCount,
                            priceAdult = Money.fromCents(tour.priceAdultCents),
                            priceChild = tour.priceChildCents?.let(Money::fromCents),
                        ),
                    )
                }
            }
            PriceBasis.PER_UNIT -> {
                val option = command.priceOptionCode?.let(tour::priceOption)
                val units = command.unitCount
                val guests = command.adultsCount + command.childrenCount
                when {
                    option == null -> Priced.Invalid("price_option_required")
                    units == null || units < 1 -> Priced.Invalid("unit_count_required")
                    guests > units * option.seatsPerUnit -> Priced.Invalid("guests_exceed_units")
                    units > guests -> Priced.Invalid("units_exceed_guests")
                    else ->
                        Priced.Ok(
                            BookingPricing.computePerUnit(
                                adultsCount = command.adultsCount,
                                childrenCount = command.childrenCount,
                                option = option,
                                unitCount = units,
                            ),
                        )
                }
            }
        }

    private fun createdEnvelope(
        booking: Booking,
        now: Instant,
        correlationId: UUID?,
    ): IntegrationEventEnvelope =
        IntegrationEventEnvelope(
            id = UUID.randomUUID(),
            aggregateType = "tours-operator.booking",
            aggregateId = booking.id.value.toString(),
            eventType = "tours-operator.booking.created",
            eventVersion = 1,
            payloadJson =
                objectMapper.writeValueAsString(
                    mapOf(
                        "bookingId" to booking.id.value.toString(),
                        "reference" to booking.reference,
                        "tourId" to booking.tourId.value.toString(),
                        "slotId" to booking.slotId.value.toString(),
                        "tourDate" to booking.tourDate.toString(),
                        "timeSlot" to booking.timeSlot.name,
                        "adultsCount" to booking.pricing.adultsCount,
                        "childrenCount" to booking.pricing.childrenCount,
                        "priceOptionCode" to booking.pricing.unit?.optionCode,
                        "unitCount" to booking.pricing.unit?.unitCount,
                        "totalEur" to
                            booking.pricing.totalEur.amount
                                .toPlainString(),
                        "locale" to booking.locale,
                        "customerPhone" to booking.customer.phone,
                        "customerNationality" to booking.customer.nationality,
                    ),
                ),
            occurredAt = now,
            correlationId = correlationId,
            causationId = null,
        )
}
