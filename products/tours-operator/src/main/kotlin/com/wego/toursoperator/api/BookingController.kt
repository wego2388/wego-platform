package com.wego.toursoperator.api

import com.wego.events.CorrelationContext
import com.wego.identity.AuthenticatedUser
import com.wego.toursoperator.application.BookingHistoryEntry
import com.wego.toursoperator.application.BookingQueryService
import com.wego.toursoperator.application.CancelBookingResult
import com.wego.toursoperator.application.CancelBookingService
import com.wego.toursoperator.application.CompleteBookingResult
import com.wego.toursoperator.application.CompleteBookingService
import com.wego.toursoperator.application.CreateBookingCommand
import com.wego.toursoperator.application.CreateBookingResult
import com.wego.toursoperator.application.CreateBookingService
import com.wego.toursoperator.domain.Booking
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.BookingStatus
import com.wego.toursoperator.domain.CustomerContact
import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.TourSlotId
import com.wego.toursoperator.domain.UnitPurchase
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@Validated
@RestController("toursOperatorBookingController")
@RequestMapping("/api/v1/tours-operator/bookings")
class BookingController(
    private val createBookingService: CreateBookingService,
    private val cancelBookingService: CancelBookingService,
    private val completeBookingService: CompleteBookingService,
    private val bookingQueryService: BookingQueryService,
) {
    /**
     * Public — no authentication. Creates a NEW booking and reserves one place
     * per guest in the slot; the price is computed here, never taken from the client.
     * Payment must complete within 30 minutes or the booking expires.
     */
    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateBookingRequest,
    ): ResponseEntity<Any> {
        val result =
            createBookingService.create(
                CreateBookingCommand(
                    slotId = TourSlotId(request.slotId),
                    adultsCount = request.adultsCount,
                    childrenCount = request.childrenCount,
                    priceOptionCode = request.priceOptionCode,
                    unitCount = request.unitCount,
                    customer =
                        CustomerContact(
                            fullName = request.customer.fullName,
                            phone = request.customer.phone,
                            nationality = request.customer.nationality,
                            email = request.customer.email,
                        ),
                    hotelName = request.hotelName,
                    hotelRoom = request.hotelRoom,
                    specialRequests = request.specialRequests,
                    locale = request.locale,
                    actorUserId = null,
                    correlationId = CorrelationContext.currentCorrelationId(),
                ),
            )
        return when (result) {
            is CreateBookingResult.Created ->
                ResponseEntity.status(HttpStatus.CREATED).body(result.booking.toResponse())
            CreateBookingResult.SlotNotFound ->
                ResponseEntity.notFound().build()
            CreateBookingResult.SlotBlocked ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("slot_blocked"))
            CreateBookingResult.SlotFullyBooked ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("slot_fully_booked"))
            CreateBookingResult.TourNotActive ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("tour_not_active"))
            CreateBookingResult.BookingsPaused ->
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponse("bookings_paused"))
            is CreateBookingResult.InvalidPricing ->
                ResponseEntity.unprocessableEntity().body(ErrorResponse(result.code))
        }
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('tours-operator.booking:cancel')")
    fun cancel(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CancelBookingRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                cancelBookingService.cancel(
                    BookingId(id),
                    request.reason,
                    actorUserId,
                    CorrelationContext.currentCorrelationId(),
                )
        ) {
            is CancelBookingResult.Cancelled ->
                ResponseEntity.ok(result.booking.toResponse())
            CancelBookingResult.NotFound ->
                ResponseEntity.notFound().build()
            CancelBookingResult.AlreadyCancelled ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("already_cancelled"))
            CancelBookingResult.CannotCancel ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("cannot_cancel"))
        }
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('tours-operator.booking:complete')")
    fun complete(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                completeBookingService.complete(
                    BookingId(id),
                    actorUserId,
                    CorrelationContext.currentCorrelationId(),
                )
        ) {
            is CompleteBookingResult.Completed ->
                ResponseEntity.ok(result.booking.toResponse())
            CompleteBookingResult.NotFound ->
                ResponseEntity.notFound().build()
            CompleteBookingResult.CannotComplete ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("cannot_complete"))
        }
    }

    /** Staff list — requires authentication. */
    @GetMapping
    @PreAuthorize("hasAuthority('tours-operator.booking:view')")
    fun list(
        @RequestParam(required = false) tourId: UUID?,
        @RequestParam(required = false) status: BookingStatus?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate?,
        @RequestParam(required = false, defaultValue = "0") @Min(0) page: Int,
        @RequestParam(required = false, defaultValue = "50") @Min(1) @Max(200) size: Int,
    ): List<BookingResponse> =
        bookingQueryService
            .list(tourId?.let(::TourId), status, date, page, size)
            .map { it.toResponse() }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('tours-operator.booking:view')")
    fun findById(
        @PathVariable id: UUID,
    ): ResponseEntity<BookingResponse> {
        val booking = bookingQueryService.findById(BookingId(id)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(booking.toResponse())
    }

    /** Staff-only lifecycle history: who changed the booking, when, and why. */
    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('tours-operator.booking:view')")
    fun history(
        @PathVariable id: UUID,
    ): ResponseEntity<List<BookingHistoryEntryResponse>> {
        bookingQueryService.findById(BookingId(id)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(bookingQueryService.history(BookingId(id)).map { it.toResponse() })
    }

    /**
     * Public lookup — customer retrieves their booking by reference + phone.
     * No authentication required.
     */
    @PostMapping("/lookup")
    fun lookup(
        @Valid @RequestBody request: LookupBookingRequest,
    ): ResponseEntity<PublicBookingLookupResponse> {
        val booking =
            bookingQueryService.findByReferenceAndPhone(
                request.reference.trim().uppercase(),
                request.phone.trim(),
            )
                ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(booking.toPublicLookupResponse())
    }
}

internal fun Booking.toResponse() =
    BookingResponse(
        id = id.value,
        reference = reference,
        tourId = tourId.value,
        slotId = slotId.value,
        tourDate = tourDate,
        timeSlot = timeSlot,
        adultsCount = pricing.adultsCount,
        childrenCount = pricing.childrenCount,
        priceAdult = MoneyResponse(pricing.priceAdult.amount.toPlainString()),
        priceChild = pricing.priceChild?.let { MoneyResponse(it.amount.toPlainString()) },
        totalPrice = MoneyResponse(pricing.totalEur.amount.toPlainString()),
        unit = pricing.unit?.toResponse(),
        customer =
            BookingCustomerResponse(
                fullName = customer.fullName,
                phone = customer.phone,
                nationality = customer.nationality,
                email = customer.email,
            ),
        hotelName = hotelName,
        hotelRoom = hotelRoom,
        specialRequests = specialRequests,
        locale = locale,
        status = status,
        createdAt = createdAt,
        confirmedAt = confirmedAt,
        cancelledAt = cancelledAt,
        cancellationReason = cancellationReason,
        completedAt = completedAt,
        expiredAt = expiredAt,
    )

private fun Booking.toPublicLookupResponse() =
    PublicBookingLookupResponse(
        reference = reference,
        tourDate = tourDate,
        timeSlot = timeSlot,
        adultsCount = pricing.adultsCount,
        childrenCount = pricing.childrenCount,
        totalPrice = MoneyResponse(pricing.totalEur.amount.toPlainString()),
        unit = pricing.unit?.toResponse(),
        hotelName = hotelName,
        status = status,
        cancellationReason = cancellationReason,
    )

internal fun UnitPurchase.toResponse() =
    BookingUnitResponse(
        optionCode = optionCode,
        optionLabel = optionLabel,
        seatsPerUnit = seatsPerUnit,
        unitCount = unitCount,
        unitPrice = MoneyResponse(unitPrice.amount.toPlainString()),
    )

data class BookingHistoryEntryResponse(
    val eventType: String,
    val fromStatus: String?,
    val toStatus: String?,
    val reason: String?,
    val actorUserId: UUID?,
    val actorEmail: String?,
    val occurredAt: java.time.Instant,
)

private fun BookingHistoryEntry.toResponse() =
    BookingHistoryEntryResponse(
        eventType = eventType,
        fromStatus = fromStatus,
        toStatus = toStatus,
        reason = reason,
        actorUserId = actorUserId,
        actorEmail = actorEmail,
        occurredAt = occurredAt,
    )
