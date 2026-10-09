package com.wego.toursoperator.application

import com.wego.toursoperator.domain.Booking
import com.wego.toursoperator.domain.BookingPricing
import com.wego.toursoperator.domain.Money
import com.wego.toursoperator.domain.OnlineRequest
import com.wego.toursoperator.domain.OnlineRequestAudit
import com.wego.toursoperator.domain.OnlineRequestDetails
import com.wego.toursoperator.domain.OnlineRequestStatus
import com.wego.toursoperator.domain.PriceBasis
import com.wego.toursoperator.domain.Tour
import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.TourSlotId
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

sealed interface OnlineRequestResult {
    data class Saved(
        val request: OnlineRequest,
        val replayed: Boolean = false,
    ) : OnlineRequestResult

    data class Converted(
        val request: OnlineRequest,
        val booking: Booking,
    ) : OnlineRequestResult

    data class Refused(
        val code: String,
        val httpStatus: Int = 409,
    ) : OnlineRequestResult
}

/** Request row → existing office slot lock → booking. Conversion is one transaction. */
class OnlineRequestService(
    private val repository: OnlineRequestRepository,
    private val tours: TourRepository,
    private val slots: TourSlotRepository,
    private val bookings: BookingRepository,
    private val createBooking: CreateBookingService,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) {
    fun submit(
        id: UUID,
        hash: String,
        details: OnlineRequestDetails,
    ): OnlineRequestResult =
        transactions.runInTransaction {
            // Replays remain possible after a date passes or a tour is deactivated.
            repository.find(id)?.let { return@runInTransaction replay(it, hash) }
            val today = LocalDate.now(clock.withZone(ZoneId.of("Africa/Cairo")))
            if (details.preferredDate < today || details.preferredDate > today.plusDays(365)) {
                return@runInTransaction OnlineRequestResult.Refused("invalid_preferred_date", 422)
            }
            val tour = tours.findById(details.tourId)
            if (tour == null || !tour.isActive) return@runInTransaction OnlineRequestResult.Refused("tour_not_active", 422)
            if (details.preferredTime != null && details.preferredTime !in tour.availableTimeSlots) {
                return@runInTransaction OnlineRequestResult.Refused("invalid_preferred_time", 422)
            }
            val pricing =
                requestPricing(tour, details)
                    ?: return@runInTransaction OnlineRequestResult.Refused("invalid_request_pricing", 422)
            val now = Instant.now(clock)
            val request =
                OnlineRequest(
                    id,
                    "STQ-${id.toString().replace("-", "").uppercase()}",
                    hash,
                    details,
                    pricing.totalEur,
                    OnlineRequestStatus.NEW,
                    0,
                    null,
                    now,
                    now,
                )
            if (!repository.insert(request)) {
                return@runInTransaction replay(checkNotNull(repository.find(id)), hash)
            }
            repository.audit(request, null)
            OnlineRequestResult.Saved(request)
        }

    fun list(
        status: OnlineRequestStatus?,
        page: Int,
        size: Int,
    ): List<OnlineRequest> = repository.list(status, page, size)

    fun countOpen(): Int = repository.countOpen()

    fun find(id: UUID): OnlineRequest? = repository.find(id)

    /** Operational catalog context is authorized by booking:view, not tour administration. */
    fun tour(id: TourId): Tour? = tours.findById(id)

    fun history(id: UUID): List<OnlineRequestAudit> = repository.history(id)

    fun followUp(
        id: UUID,
        revision: Int,
        status: OnlineRequestStatus,
        actor: UUID,
    ): OnlineRequestResult =
        transactions.runInTransaction {
            val request =
                repository.find(id, lock = true)
                    ?: return@runInTransaction OnlineRequestResult.Refused("request_not_found", 404)
            if (status !in setOf(OnlineRequestStatus.IN_PROGRESS, OnlineRequestStatus.CLOSED)) {
                return@runInTransaction OnlineRequestResult.Refused("invalid_request_status", 422)
            }
            if (request.status == status && request.revision == revision + 1) {
                return@runInTransaction OnlineRequestResult.Saved(request, true)
            }
            if (request.revision != revision) return@runInTransaction OnlineRequestResult.Refused("request_changed")
            if (request.status in setOf(OnlineRequestStatus.CONVERTED, OnlineRequestStatus.CLOSED)) {
                return@runInTransaction OnlineRequestResult.Refused("request_not_open")
            }
            val changed = request.copy(status = status, revision = revision + 1, updatedAt = Instant.now(clock))
            repository.save(changed)
            repository.audit(changed, actor)
            OnlineRequestResult.Saved(changed)
        }

    fun convert(
        id: UUID,
        revision: Int,
        slotId: TourSlotId,
        expectedTotal: Money,
        actor: UUID,
        correlationId: UUID?,
    ): OnlineRequestResult =
        try {
            transactions.runInTransaction {
                val request =
                    repository.find(id, lock = true)
                        ?: return@runInTransaction OnlineRequestResult.Refused("request_not_found", 404)
                if (request.status == OnlineRequestStatus.CONVERTED) {
                    val booking = checkNotNull(bookings.findById(checkNotNull(request.bookingId)))
                    return@runInTransaction if (booking.slotId == slotId && booking.pricing.totalEur == expectedTotal) {
                        OnlineRequestResult.Converted(request, booking)
                    } else {
                        OnlineRequestResult.Refused("request_already_converted")
                    }
                }
                if (request.revision != revision) return@runInTransaction OnlineRequestResult.Refused("request_changed")
                if (request.status == OnlineRequestStatus.CLOSED) return@runInTransaction OnlineRequestResult.Refused("request_not_open")
                val slot =
                    slots.findById(slotId)
                        ?: return@runInTransaction OnlineRequestResult.Refused("slot_not_found", 404)
                if (slot.tourId != request.details.tourId) return@runInTransaction OnlineRequestResult.Refused("slot_tour_mismatch")
                val d = request.details
                val result =
                    createBooking.createOffice(
                        CreateOfficeBookingCommand(
                            CreateBookingCommand(
                                slotId,
                                d.adultsCount,
                                d.childrenCount,
                                d.priceOptionCode,
                                d.unitCount,
                                d.customer,
                                d.hotelName,
                                null,
                                d.specialRequests,
                                d.locale,
                                actor,
                                correlationId,
                            ),
                            // Not a user-controlled office key: namespace prevents accidental key reuse.
                            UUID.nameUUIDFromBytes("online-request:$id".toByteArray(Charsets.UTF_8)),
                        ),
                    )
                val booking =
                    when (result) {
                        is CreateBookingResult.Created -> result.booking
                        is CreateBookingResult.Replayed -> result.booking
                        CreateBookingResult.SlotNotFound -> return@runInTransaction OnlineRequestResult.Refused("slot_not_found", 404)
                        CreateBookingResult.SlotBlocked -> return@runInTransaction OnlineRequestResult.Refused("slot_blocked")
                        CreateBookingResult.SlotInPast -> return@runInTransaction OnlineRequestResult.Refused("slot_in_past")
                        CreateBookingResult.SlotFullyBooked -> return@runInTransaction OnlineRequestResult.Refused("slot_fully_booked")
                        CreateBookingResult.TourNotActive -> return@runInTransaction OnlineRequestResult.Refused("tour_not_active")
                        is CreateBookingResult.InvalidPricing -> return@runInTransaction OnlineRequestResult.Refused(result.code, 422)
                        else -> error("Unexpected office conversion result")
                    }
                // Re-check actual saved pricing, not a stale browser estimate; rollback ALL writes.
                if (booking.pricing.totalEur != expectedTotal) throw PriceChanged()
                val changed =
                    request.copy(
                        status = OnlineRequestStatus.CONVERTED,
                        revision = request.revision + 1,
                        bookingId = booking.id,
                        updatedAt = Instant.now(clock),
                    )
                repository.save(changed)
                repository.audit(changed, actor)
                OnlineRequestResult.Converted(changed, booking)
            }
        } catch (_: PriceChanged) {
            OnlineRequestResult.Refused("price_changed")
        }

    private fun replay(
        request: OnlineRequest,
        hash: String,
    ): OnlineRequestResult =
        if (request.payloadHash == hash) {
            OnlineRequestResult.Saved(request, true)
        } else {
            OnlineRequestResult.Refused("idempotency_key_reused")
        }

    private class PriceChanged : RuntimeException()
}

private fun requestPricing(
    tour: Tour,
    d: OnlineRequestDetails,
): BookingPricing? =
    when (tour.priceBasis) {
        PriceBasis.PER_PERSON ->
            if (d.priceOptionCode != null ||
                d.unitCount != null ||
                (d.childrenCount > 0 && tour.priceChildCents == null)
            ) {
                null
            } else {
                BookingPricing.compute(
                    d.adultsCount,
                    d.childrenCount,
                    Money.fromCents(tour.priceAdultCents),
                    tour.priceChildCents?.let(Money::fromCents),
                )
            }
        PriceBasis.PER_UNIT -> {
            val option = d.priceOptionCode?.let(tour::priceOption)
            val units = d.unitCount
            val guests = d.adultsCount + d.childrenCount
            if (option == null || units == null || units < 1 || units > guests || guests > units * option.seatsPerUnit) {
                null
            } else {
                BookingPricing.computePerUnit(d.adultsCount, d.childrenCount, option, units)
            }
        }
    }
