package com.wego.toursoperator.application

import com.wego.events.IntegrationEventEnvelope
import com.wego.events.OutboxWriter
import com.wego.toursoperator.domain.Booking
import com.wego.toursoperator.domain.BookingChannel
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
import java.time.LocalDate
import java.time.ZoneId
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

/**
 * A booking created by staff for a customer at the office. The same pricing
 * and capacity rules as the public path apply; [clientRequestId] makes the
 * request safely retryable.
 */
data class CreateOfficeBookingCommand(
    val booking: CreateBookingCommand,
    val clientRequestId: UUID,
) {
    init {
        require(booking.actorUserId != null) { "An office booking records the staff actor" }
    }
}

sealed class CreateBookingResult {
    data class Created(
        val booking: Booking,
    ) : CreateBookingResult()

    /** The same staff actor already created this booking with this request id; nothing new was written. */
    data class Replayed(
        val booking: Booking,
    ) : CreateBookingResult()

    /** The request id was already used for a different booking request. */
    data object IdempotencyKeyReused : CreateBookingResult()

    data object SlotNotFound : CreateBookingResult()

    data object SlotBlocked : CreateBookingResult()

    /** The departure date has already passed (Sharm El Sheikh local date). */
    data object SlotInPast : CreateBookingResult()

    data object SlotFullyBooked : CreateBookingResult()

    data object TourNotActive : CreateBookingResult()

    /** A manager paused online bookings or payments (emergency sales control). */
    data object BookingsPaused : CreateBookingResult()

    /** Enquiries must not create bookings or hold inventory. */
    data object OnlineBookingUnavailable : CreateBookingResult()

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
    private val salesControlRepository: SalesControlRepository,
    private val outboxWriter: OutboxWriter,
    private val transactionRunner: TransactionRunner,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
    private val bookingMode: BookingMode = BookingMode.ONLINE_PAYMENT,
) {
    fun create(command: CreateBookingCommand): CreateBookingResult =
        if (!bookingMode.onlineEnabled) {
            CreateBookingResult.OnlineBookingUnavailable
        } else {
            createOnline(command)
        }

    /**
     * Staff-created booking. Decisions, deliberately:
     * - Sales control (bookingsPaused/paymentsPaused) and [BookingMode] gate
     *   only the public online path. Staff operations are never affected by
     *   the emergency switch, and an enquiry-only deployment is exactly where
     *   the office confirms bookings by hand.
     * - Everything that protects capacity and price is shared with the public
     *   path: slot row lock, blocked slot, past date, active tour, pricing.
     */
    fun createOffice(command: CreateOfficeBookingCommand): CreateBookingResult = createInTransaction(command.booking, command.clientRequestId)

    private fun createOnline(command: CreateBookingCommand): CreateBookingResult =
        createInTransaction(command, officeRequestId = null)

    private fun createInTransaction(
        command: CreateBookingCommand,
        officeRequestId: UUID?,
    ): CreateBookingResult =
        transactionRunner.runInTransaction {
            if (officeRequestId == null) {
                // A public booking can only be paid online, so paused payments
                // also stop new bookings — otherwise they would hold places for
                // 30 minutes with no way to pay.
                val sales = salesControlRepository.current()
                if (sales.bookingsPaused || sales.paymentsPaused) {
                    return@runInTransaction CreateBookingResult.BookingsPaused
                }
            }

            // Lock the slot first — serialization point for capacity.
            val slot =
                slotRepository.findByIdForUpdate(command.slotId)
                    ?: return@runInTransaction CreateBookingResult.SlotNotFound

            // A retried staff request returns the booking it already created.
            // Looked up under the slot lock so two concurrent retries serialize.
            if (officeRequestId != null) {
                val actor = checkNotNull(command.actorUserId)
                val existing = bookingRepository.findByClientRequest(actor, officeRequestId)
                if (existing != null) {
                    return@runInTransaction if (isSameRequest(existing, command)) {
                        CreateBookingResult.Replayed(existing)
                    } else {
                        CreateBookingResult.IdempotencyKeyReused
                    }
                }
            }

            if (slot.isBlocked) return@runInTransaction CreateBookingResult.SlotBlocked
            if (slot.date.isBefore(LocalDate.now(clock.withZone(SHARM_ZONE)))) {
                return@runInTransaction CreateBookingResult.SlotInPast
            }

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
                if (officeRequestId == null) {
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
                } else {
                    Booking.createOffice(
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
                        createdByUserId = checkNotNull(command.actorUserId),
                        clientRequestId = officeRequestId,
                    )
                }

            slotRepository.save(slot)
            bookingRepository.save(booking)
            if (booking.channel == BookingChannel.OFFICE) {
                bookingAuditRecorder.recordCreatedOffice(booking.id, checkNotNull(command.actorUserId), now, command.correlationId)
            } else {
                bookingAuditRecorder.recordCreated(booking.id, command.actorUserId, now, command.correlationId)
            }
            outboxWriter.write(createdEnvelope(booking, now, command.correlationId))

            CreateBookingResult.Created(booking)
        }

    private fun isSameRequest(
        existing: Booking,
        command: CreateBookingCommand,
    ): Boolean =
        existing.slotId == command.slotId &&
            existing.pricing.adultsCount == command.adultsCount &&
            existing.pricing.childrenCount == command.childrenCount &&
            existing.pricing.unit?.optionCode == command.priceOptionCode &&
            existing.pricing.unit?.unitCount == command.unitCount &&
            existing.customer.phone == command.customer.phone &&
            existing.customer.fullName == command.customer.fullName

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
                        "channel" to booking.channel.name,
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

private val SHARM_ZONE: ZoneId = ZoneId.of("Africa/Cairo")
