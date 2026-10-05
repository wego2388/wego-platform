package com.wego.toursoperator.api

import com.wego.events.CorrelationContext
import com.wego.identity.AuthenticatedUser
import com.wego.toursoperator.application.BookingQueryService
import com.wego.toursoperator.application.CollectionResult
import com.wego.toursoperator.application.CreateBookingCommand
import com.wego.toursoperator.application.CreateBookingResult
import com.wego.toursoperator.application.CreateBookingService
import com.wego.toursoperator.application.CreateOfficeBookingCommand
import com.wego.toursoperator.application.OfficeCollectionService
import com.wego.toursoperator.application.QuoteResult
import com.wego.toursoperator.application.RecordCollectionCommand
import com.wego.toursoperator.application.ReverseCollectionCommand
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.CustomerContact
import com.wego.toursoperator.domain.Money
import com.wego.toursoperator.domain.OfficeCollection
import com.wego.toursoperator.domain.PaidCurrency
import com.wego.toursoperator.domain.Settlement
import com.wego.toursoperator.domain.TourSlotId
import jakarta.validation.Valid
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
import java.math.BigDecimal
import java.util.UUID

/**
 * Staff-created ("office") bookings and their cash collections.
 *
 * Creating a booking and recording cash are separate permissions. Office cash
 * lives in its own append-only ledger and never touches Paymob payment rows.
 */
@Validated
@RestController("toursOperatorStaffBookingController")
@RequestMapping("/api/v1/tours-operator/staff/bookings")
class StaffBookingController(
    private val createBookingService: CreateBookingService,
    private val officeCollectionService: OfficeCollectionService,
    private val bookingQueryService: BookingQueryService,
) {
    @PostMapping
    @PreAuthorize("hasAuthority('tours-operator.booking:create-office')")
    fun createOffice(
        @Valid @RequestBody request: CreateOfficeBookingRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        val result =
            createBookingService.createOffice(
                CreateOfficeBookingCommand(
                    booking =
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
                            actorUserId = actorUserId,
                            correlationId = CorrelationContext.currentCorrelationId(),
                        ),
                    clientRequestId = request.clientRequestId,
                ),
            )
        return when (result) {
            is CreateBookingResult.Created ->
                ResponseEntity.status(HttpStatus.CREATED).body(listOf(result.booking).toResponses(officeCollectionService).single())
            is CreateBookingResult.Replayed ->
                ResponseEntity.ok(listOf(result.booking).toResponses(officeCollectionService).single())
            CreateBookingResult.IdempotencyKeyReused -> conflict("idempotency_key_reused")
            CreateBookingResult.SlotNotFound -> ResponseEntity.notFound().build()
            CreateBookingResult.SlotBlocked -> conflict("slot_blocked")
            CreateBookingResult.SlotInPast -> conflict("slot_in_past")
            CreateBookingResult.SlotFullyBooked -> conflict("slot_fully_booked")
            CreateBookingResult.TourNotActive -> conflict("tour_not_active")
            is CreateBookingResult.InvalidPricing -> ResponseEntity.unprocessableEntity().body(ErrorResponse(result.code))
            // Never produced by the office path (sales control and booking mode gate online sales only).
            CreateBookingResult.BookingsPaused,
            CreateBookingResult.OnlineBookingUnavailable,
            -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse("unexpected_result"))
        }
    }

    @GetMapping("/{id}/collections")
    @PreAuthorize("hasAuthority('tours-operator.booking:view')")
    fun collections(
        @PathVariable id: UUID,
    ): ResponseEntity<List<OfficeCollectionResponse>> {
        bookingQueryService.findById(BookingId(id)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(officeCollectionService.entries(BookingId(id)).map { it.toResponse() })
    }

    @PostMapping("/{id}/collections")
    @PreAuthorize("hasAuthority('tours-operator.booking:collect-cash')")
    fun record(
        @PathVariable id: UUID,
        @Valid @RequestBody request: RecordCollectionRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return collectionResponse(
            id,
            officeCollectionService.record(
                RecordCollectionCommand(
                    bookingId = BookingId(id),
                    method = request.method,
                    amountPaid = Money(request.amount.setScale(Money.REQUIRED_SCALE)),
                    currency = request.currency,
                    reference = request.reference?.trim()?.takeIf { it.isNotEmpty() },
                    actorUserId = actorUserId,
                    clientRequestId = request.clientRequestId,
                ),
            ),
        )
    }

    /** What a payment would settle at today's manager-set rate, so staff see the EUR before saving. */
    @GetMapping("/{id}/collections/quote")
    @PreAuthorize("hasAuthority('tours-operator.booking:collect-cash')")
    fun quote(
        @PathVariable id: UUID,
        @RequestParam currency: PaidCurrency,
        @RequestParam @jakarta.validation.constraints.DecimalMin("0.01") @jakarta.validation.constraints.Digits(
            integer = 10,
            fraction = 2,
        ) amount: BigDecimal,
    ): ResponseEntity<Any> =
        when (val quote = officeCollectionService.quote(BookingId(id), Money(amount.setScale(Money.REQUIRED_SCALE)), currency)) {
            QuoteResult.BookingNotFound -> ResponseEntity.notFound().build()
            QuoteResult.NotAnOfficeBooking -> conflict("not_an_office_booking")
            is QuoteResult.Quoted -> {
                val outstanding = MoneyResponse(quote.outstanding.amount.toPlainString())
                ResponseEntity.ok(
                    when (val s = quote.settlement) {
                        is Settlement.Settled ->
                            CollectionQuoteResponse(
                                "OK",
                                MoneyResponse(s.eur.amount.toPlainString()),
                                s.rate?.egpPerEur?.toPlainString(),
                                outstanding,
                            )
                        Settlement.RateMissing -> CollectionQuoteResponse("RATE_MISSING", null, null, outstanding)
                        Settlement.TooSmall -> CollectionQuoteResponse("TOO_SMALL", null, null, outstanding)
                        is Settlement.ExceedsOutstanding -> CollectionQuoteResponse("EXCEEDS_OUTSTANDING", null, null, outstanding)
                    },
                )
            }
        }

    @PostMapping("/{id}/collections/{collectionId}/reverse")
    @PreAuthorize("hasAuthority('tours-operator.booking:collect-cash')")
    fun reverse(
        @PathVariable id: UUID,
        @PathVariable collectionId: UUID,
        @Valid @RequestBody request: ReverseCollectionRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return collectionResponse(
            id,
            officeCollectionService.reverse(
                ReverseCollectionCommand(BookingId(id), collectionId, request.reason, actorUserId, request.clientRequestId),
            ),
        )
    }

    private fun collectionResponse(
        bookingId: UUID,
        result: CollectionResult,
    ): ResponseEntity<Any> =
        when (result) {
            is CollectionResult.Recorded ->
                ResponseEntity.status(HttpStatus.CREATED).body(outcome(result.entry, result.summary.collected.amount, bookingId))
            is CollectionResult.Replayed ->
                ResponseEntity.ok(outcome(result.entry, result.summary.collected.amount, bookingId))
            CollectionResult.BookingNotFound, CollectionResult.CollectionNotFound -> ResponseEntity.notFound().build()
            CollectionResult.NotAnOfficeBooking -> conflict("not_an_office_booking")
            is CollectionResult.BookingNotOpen -> conflict("booking_not_open_status_${result.status.name.lowercase()}")
            is CollectionResult.ExceedsOutstanding -> conflict("amount_exceeds_outstanding")
            CollectionResult.ReferenceRequired -> badRequest("reference_required")
            CollectionResult.ReferenceNotAllowed -> badRequest("reference_not_allowed")
            CollectionResult.ReferenceAlreadyUsed -> conflict("reference_already_used")
            CollectionResult.FxRateNotSet -> conflict("fx_rate_not_set")
            CollectionResult.AmountTooSmall -> ResponseEntity.unprocessableEntity().body(ErrorResponse("amount_too_small"))
            CollectionResult.AlreadyReversed -> conflict("collection_already_reversed")
            CollectionResult.NotReversible -> conflict("collection_not_reversible")
            CollectionResult.IdempotencyKeyReused -> conflict("idempotency_key_reused")
        }

    private fun outcome(
        entry: OfficeCollection,
        netAfter: BigDecimal,
        bookingId: UUID,
    ): OfficeCollectionOutcomeResponse {
        val booking = checkNotNull(bookingQueryService.findById(BookingId(bookingId)))
        return OfficeCollectionOutcomeResponse(
            entry = entry.toResponse(),
            officePayment = checkNotNull(booking.officePaymentResponse(netAfter)),
        )
    }

    private fun badRequest(code: String): ResponseEntity<Any> = ResponseEntity.badRequest().body(ErrorResponse(code))

    private fun conflict(code: String): ResponseEntity<Any> = ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(code))
}

private fun OfficeCollection.toResponse() =
    OfficeCollectionResponse(
        id = id,
        kind = kind,
        method = method,
        amount = MoneyResponse(amount.amount.toPlainString()),
        amountPaid = MoneyResponse(amountPaid.amount.toPlainString(), currencyPaid.name),
        fxRate = fxRate?.egpPerEur?.toPlainString(),
        reference = reference,
        reversesCollectionId = reversesCollectionId,
        reason = reason,
        recordedByUserId = recordedByUserId,
        recordedAt = recordedAt,
    )
