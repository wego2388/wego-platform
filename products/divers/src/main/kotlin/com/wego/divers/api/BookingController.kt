package com.wego.divers.api

import com.wego.divers.application.BookingQueryService
import com.wego.divers.application.CancelBookingResult
import com.wego.divers.application.CancelBookingService
import com.wego.divers.application.CreateBookingCommand
import com.wego.divers.application.CreateBookingResult
import com.wego.divers.application.CreateBookingService
import com.wego.divers.application.MarkBookingPaidResult
import com.wego.divers.application.MarkBookingPaidService
import com.wego.divers.application.RefundBookingResult
import com.wego.divers.application.RefundBookingService
import com.wego.divers.domain.Booking
import com.wego.divers.domain.BookingId
import com.wego.divers.domain.BookingStatus
import com.wego.divers.domain.CustomerContact
import com.wego.divers.domain.OfferingId
import com.wego.events.CorrelationContext
import com.wego.identity.AuthenticatedUser
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

private const val MAX_IDEMPOTENCY_KEY_LENGTH = 128

@Validated
@RestController
@RequestMapping("/api/v1/divers/bookings")
class BookingController(
    private val createBookingService: CreateBookingService,
    private val cancelBookingService: CancelBookingService,
    private val markBookingPaidService: MarkBookingPaidService,
    private val refundBookingService: RefundBookingService,
    private val bookingQueryService: BookingQueryService,
) {
    @PostMapping
    @PreAuthorize("hasAuthority('booking:create')")
    fun create(
        @Valid @RequestBody request: CreateBookingRequest,
        @RequestHeader("Idempotency-Key") @Size(min = 1, max = MAX_IDEMPOTENCY_KEY_LENGTH) idempotencyKey: String,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId

        val result =
            createBookingService.create(
                CreateBookingCommand(
                    offeringId = OfferingId(request.offeringId),
                    partySize = request.partySize,
                    customer = CustomerContact(request.customerName, request.customerEmail, request.customerPhone),
                    idempotencyKey = idempotencyKey,
                    createdByUserId = actorUserId,
                    correlationId = CorrelationContext.currentCorrelationId(),
                ),
            )

        return when (result) {
            is CreateBookingResult.Created -> ResponseEntity.status(HttpStatus.CREATED).body(result.booking.toResponse())
            is CreateBookingResult.Replayed -> ResponseEntity.ok(result.booking.toResponse())
            CreateBookingResult.OfferingNotFound -> ResponseEntity.notFound().build()
            CreateBookingResult.OfferingClosed ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(BookingErrorResponse("offering_closed"))
            CreateBookingResult.CapacityExceeded ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(BookingErrorResponse("capacity_exceeded"))
            CreateBookingResult.IdempotencyKeyConflict ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(BookingErrorResponse("idempotency_key_conflict"))
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('booking:view')")
    fun list(
        @RequestParam(required = false) offeringId: UUID?,
        @RequestParam(required = false) status: BookingStatus?,
        @RequestParam(required = false, defaultValue = "0") @Min(0) page: Int,
        @RequestParam(required = false, defaultValue = "50") @Min(1) @Max(200) size: Int,
    ): List<BookingResponse> = bookingQueryService.list(offeringId?.let(::OfferingId), status, page, size).map { it.toResponse() }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('booking:view')")
    fun findById(
        @PathVariable id: UUID,
    ): ResponseEntity<BookingResponse> {
        val booking = bookingQueryService.findById(BookingId(id)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(booking.toResponse())
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('booking:cancel')")
    fun cancel(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CancelBookingRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId

        return when (
            val result =
                cancelBookingService.cancel(BookingId(id), actorUserId, request.reason, CorrelationContext.currentCorrelationId())
        ) {
            is CancelBookingResult.Cancelled -> ResponseEntity.ok(result.booking.toResponse())
            CancelBookingResult.NotFound -> ResponseEntity.notFound().build()
            CancelBookingResult.AlreadyCancelled ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(BookingErrorResponse("already_cancelled"))
        }
    }

    /**
     * `UNPAID -> PAID` only. Deliberately a distinct endpoint/permission
     * from [refund] — see `SECURITY_MODEL.md`'s treatment of refund as the
     * more sensitive of the two actions.
     */
    @PatchMapping("/{id}/mark-paid")
    @PreAuthorize("hasAuthority('booking:payment-update')")
    fun markPaid(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result = markBookingPaidService.markPaid(BookingId(id), actorUserId, CorrelationContext.currentCorrelationId())
        ) {
            is MarkBookingPaidResult.Ok -> ResponseEntity.ok(result.booking.toResponse())
            MarkBookingPaidResult.NotFound -> ResponseEntity.notFound().build()
            is MarkBookingPaidResult.Rejected ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(BookingErrorResponse("invalid_payment_transition"))
        }
    }

    /** `PAID -> REFUNDED` only, and only with a non-blank reason. */
    @PatchMapping("/{id}/refund")
    @PreAuthorize("hasAuthority('booking:refund')")
    fun refund(
        @PathVariable id: UUID,
        @Valid @RequestBody request: RefundBookingRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                refundBookingService.refund(
                    BookingId(id),
                    actorUserId,
                    request.reason,
                    CorrelationContext.currentCorrelationId(),
                )
        ) {
            is RefundBookingResult.Ok -> ResponseEntity.ok(result.booking.toResponse())
            RefundBookingResult.NotFound -> ResponseEntity.notFound().build()
            is RefundBookingResult.Rejected ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(BookingErrorResponse("invalid_payment_transition"))
        }
    }
}

private fun Booking.toResponse() =
    BookingResponse(
        id = id.value,
        offeringId = offeringId.value,
        partySize = partySize,
        customerName = customer.name,
        customerEmail = customer.email,
        customerPhone = customer.phone,
        status = status,
        paymentStatus = paymentStatus,
        pricingBasis = pricing.pricingBasis,
        unitPrice = MoneyDto(pricing.unitPrice.amount.toPlainString(), pricing.unitPrice.currencyCode),
        billableQuantity = pricing.billableQuantity,
        totalPrice = MoneyDto(pricing.totalPrice.amount.toPlainString(), pricing.totalPrice.currencyCode),
        createdAt = createdAt,
        cancelledAt = cancelledAt,
        cancellationReason = cancellationReason,
    )
