package com.wego.toursoperator.api

import com.wego.events.CorrelationContext
import com.wego.identity.AuthenticatedUser
import com.wego.toursoperator.application.OnlineRequestResult
import com.wego.toursoperator.application.OnlineRequestService
import com.wego.toursoperator.domain.CustomerContact
import com.wego.toursoperator.domain.Money
import com.wego.toursoperator.domain.OnlineRequest
import com.wego.toursoperator.domain.OnlineRequestDetails
import com.wego.toursoperator.domain.OnlineRequestStatus
import com.wego.toursoperator.domain.TimeSlot
import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.TourSlotId
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class SubmitOnlineRequest(
    val clientRequestId: UUID,
    val tourId: UUID,
    val preferredDate: LocalDate,
    val preferredTime: TimeSlot? = null,
    @field:Min(1) @field:Max(50) val adultsCount: Int,
    @field:Min(0) @field:Max(50) val childrenCount: Int,
    @field:Pattern(regexp = "^[a-z0-9][a-z0-9-]{0,39}$") val priceOptionCode: String? = null,
    @field:Min(1) @field:Max(50) val unitCount: Int? = null,
    @field:Valid val customer: BookingCustomerRequest,
    @field:NotBlank @field:Size(max = 200) val hotelName: String,
    @field:Size(max = 2000) val specialRequests: String? = null,
    @field:Pattern(regexp = "^(en|ar|ru|it)$") val locale: String,
    /** Honeypot. It is never persisted, logged or returned. */
    @field:Size(max = 0) val website: String? = null,
)

data class FollowUpOnlineRequest(
    @field:Min(0) val expectedRevision: Int,
    val status: OnlineRequestStatus,
)

data class ConvertOnlineRequest(
    @field:Min(0) val expectedRevision: Int,
    val slotId: UUID,
    @field:Min(0) val expectedTotalCents: Long,
)

data class OnlineRequestAcknowledgement(
    val reference: String,
)

data class OnlineRequestResponse(
    val id: UUID,
    val reference: String,
    val tourId: UUID,
    val tour: TourSummaryResponse?,
    val preferredDate: LocalDate,
    val preferredTime: TimeSlot?,
    val adultsCount: Int,
    val childrenCount: Int,
    val priceOptionCode: String?,
    val unitCount: Int?,
    val customer: BookingCustomerResponse,
    val hotelName: String,
    val specialRequests: String?,
    val locale: String,
    val estimatedTotal: MoneyResponse,
    val status: OnlineRequestStatus,
    val revision: Int,
    val bookingId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Validated
@RestController
class OnlineRequestController(
    private val service: OnlineRequestService,
    private val mapper: ObjectMapper,
) {
    /** Public POST only. No public detail/list/recovery endpoint exposes request PII. */
    @PostMapping("/api/v1/tours-operator/booking-requests")
    fun submit(
        @Valid @RequestBody request: SubmitOnlineRequest,
    ): ResponseEntity<Any> {
        val c = request.customer
        val invalidContact =
            listOf(c.fullName, c.phone, c.nationality, c.email.orEmpty(), request.hotelName)
                .any { value -> value.any(Char::isISOControl) }
        if (invalidContact ||
            request.specialRequests.orEmpty().any { it.isISOControl() && it !in "\n\r\t" }
        ) {
            return ResponseEntity.unprocessableEntity().body(ErrorResponse("invalid_contact"))
        }
        if (!c.phone.trim().matches(Regex("^\\+[1-9][0-9]{6,14}$"))) {
            return ResponseEntity.unprocessableEntity().body(ErrorResponse("invalid_phone"))
        }
        val details =
            OnlineRequestDetails(
                TourId(request.tourId),
                request.preferredDate,
                request.preferredTime,
                request.adultsCount,
                request.childrenCount,
                request.priceOptionCode,
                request.unitCount,
                CustomerContact(c.fullName.trim(), c.phone.trim(), c.nationality, c.email?.trim()?.takeIf(String::isNotEmpty)),
                request.hotelName.trim(),
                request.specialRequests?.trim()?.takeIf(String::isNotEmpty),
                request.locale,
            )
        // Canonical normalized payload binds the key without exposing the PII in response/logs.
        val hash = MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(details)).joinToString("") { "%02x".format(it) }
        return when (val result = service.submit(request.clientRequestId, hash, details)) {
            is OnlineRequestResult.Saved ->
                ResponseEntity
                    .status(if (result.replayed) 200 else 201)
                    .header("Cache-Control", "no-store")
                    .body(OnlineRequestAcknowledgement(result.request.reference))
            is OnlineRequestResult.Refused -> refused(result)
            else -> error("Unexpected request submission result")
        }
    }

    @GetMapping("/api/v1/tours-operator/staff/booking-requests")
    @PreAuthorize("hasAuthority('tours-operator.booking:view')")
    fun list(
        @RequestParam(required = false) status: OnlineRequestStatus?,
        @RequestParam(defaultValue = "0") @Min(0) @Max(10000) page: Int,
        @RequestParam(defaultValue = "50") @Min(1) @Max(100) size: Int,
    ) = service.list(status, page, size).map { response(it) }

    @GetMapping("/api/v1/tours-operator/staff/booking-requests/open-count")
    @PreAuthorize("hasAuthority('tours-operator.booking:view')")
    fun count() = mapOf("count" to service.countOpen())

    @GetMapping("/api/v1/tours-operator/staff/booking-requests/{id}")
    @PreAuthorize("hasAuthority('tours-operator.booking:view')")
    fun find(
        @PathVariable id: UUID,
    ): ResponseEntity<OnlineRequestResponse> =
        service.find(id)?.let { ResponseEntity.ok(response(it)) } ?: ResponseEntity.notFound().build()

    @GetMapping("/api/v1/tours-operator/staff/booking-requests/{id}/history")
    @PreAuthorize("hasAuthority('tours-operator.booking:view')")
    fun history(
        @PathVariable id: UUID,
    ) = service.history(id)

    @PostMapping("/api/v1/tours-operator/staff/booking-requests/{id}/follow-up")
    @PreAuthorize("hasAuthority('tours-operator.booking:create-office') and hasAuthority('tours-operator.booking:view')")
    fun followUp(
        @PathVariable id: UUID,
        @Valid @RequestBody request: FollowUpOnlineRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> = respond(service.followUp(id, request.expectedRevision, request.status, actor(authentication)))

    @PostMapping("/api/v1/tours-operator/staff/booking-requests/{id}/convert")
    @PreAuthorize("hasAuthority('tours-operator.booking:create-office') and hasAuthority('tours-operator.booking:view')")
    fun convert(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ConvertOnlineRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> =
        respond(
            service.convert(
                id,
                request.expectedRevision,
                TourSlotId(request.slotId),
                Money.fromCents(request.expectedTotalCents),
                actor(authentication),
                CorrelationContext.currentCorrelationId(),
            ),
        )

    private fun actor(authentication: Authentication) = (authentication.principal as AuthenticatedUser).userId

    private fun respond(result: OnlineRequestResult): ResponseEntity<Any> =
        when (result) {
            is OnlineRequestResult.Saved -> ResponseEntity.ok(response(result.request))
            is OnlineRequestResult.Converted -> ResponseEntity.ok(response(result.request))
            is OnlineRequestResult.Refused -> refused(result)
        }

    private fun refused(result: OnlineRequestResult.Refused): ResponseEntity<Any> =
        ResponseEntity.status(result.httpStatus).body(ErrorResponse(result.code))

    private fun response(request: OnlineRequest) = request.response(service.tour(request.details.tourId)?.toSummaryResponse())
}

private fun OnlineRequest.response(tour: TourSummaryResponse?): OnlineRequestResponse =
    OnlineRequestResponse(
        id,
        reference,
        details.tourId.value,
        tour,
        details.preferredDate,
        details.preferredTime,
        details.adultsCount,
        details.childrenCount,
        details.priceOptionCode,
        details.unitCount,
        BookingCustomerResponse(details.customer.fullName, details.customer.phone, details.customer.nationality, details.customer.email),
        details.hotelName,
        details.specialRequests,
        details.locale,
        MoneyResponse(estimatedTotal.amount.toPlainString()),
        status,
        revision,
        bookingId?.value,
        createdAt,
        updatedAt,
    )
