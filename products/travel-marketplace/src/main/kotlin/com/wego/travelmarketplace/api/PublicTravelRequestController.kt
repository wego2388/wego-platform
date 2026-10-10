package com.wego.travelmarketplace.api

import com.wego.events.CorrelationContext
import com.wego.travelmarketplace.application.CreateTravelRequestCommand
import com.wego.travelmarketplace.application.CreateTravelRequestResult
import com.wego.travelmarketplace.application.CreateTravelRequestService
import com.wego.travelmarketplace.application.TravelRequestQueryService
import com.wego.travelmarketplace.domain.Money
import com.wego.travelmarketplace.domain.ServiceId
import com.wego.travelmarketplace.domain.TravelRequest
import com.wego.travelmarketplace.domain.TravelRequestCustomer
import com.wego.travelmarketplace.domain.TravelRequestReference
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Unauthenticated by construction — registered under the same public
 * wildcard prefix (`/api/v1/travel-marketplace/public`, see
 * `PublicApiPrefix`) `PublicCatalogController` already contributes; see
 * `TravelMarketplaceBeanConfiguration`. A request is never confirmation —
 * [create] can return a `NEW` or an already-`CONFIRMED` result depending on
 * the service's `confirmationType`, but creating it is always the same call.
 */
@Validated
@RestController
@RequestMapping("/api/v1/travel-marketplace/public/requests")
class PublicTravelRequestController(
    private val createTravelRequestService: CreateTravelRequestService,
    private val travelRequestQueryService: TravelRequestQueryService,
) {
    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateTravelRequestApiRequest,
        @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) idempotencyKey: String,
    ): ResponseEntity<Any> {
        val command =
            CreateTravelRequestCommand(
                serviceId = ServiceId(request.serviceId),
                serviceOptionId = request.serviceOptionId,
                requestedDate = request.requestedDate,
                requestedTime = request.requestedTime,
                adults = request.adults,
                children = request.children,
                hotelOrPickup = request.hotelOrPickup,
                locale = request.locale,
                notes = request.notes,
                sourceChannel = request.sourceChannel,
                customer = TravelRequestCustomer(request.customer.name, request.customer.phone, request.customer.email),
                idempotencyKey = idempotencyKey,
                correlationId = CorrelationContext.currentCorrelationId(),
                expectedPrice = Money(request.expectedPriceAmount.setScale(2), request.expectedPriceCurrency),
            )
        return when (val result = createTravelRequestService.create(command)) {
            CreateTravelRequestResult.RequestsPaused ->
                ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .cacheControl(
                        org.springframework.http.CacheControl
                            .noStore(),
                    ).body(TravelRequestErrorResponse("requests_paused"))
            is CreateTravelRequestResult.Created -> ResponseEntity.status(HttpStatus.CREATED).body(result.request.toPublicResponse())
            is CreateTravelRequestResult.AlreadyExists -> ResponseEntity.ok(result.request.toPublicResponse())
            CreateTravelRequestResult.ServiceNotFound ->
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(TravelRequestErrorResponse("service_not_found"))
            CreateTravelRequestResult.OptionNotFound ->
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(TravelRequestErrorResponse("option_not_found"))
            is CreateTravelRequestResult.PartySizeExceedsCapacity ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(TravelRequestErrorResponse("party_size_exceeds_capacity"))
            is CreateTravelRequestResult.PriceChanged ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(
                    TravelRequestErrorResponse(
                        error = "price_changed",
                        currentPriceAmount = result.currentPrice.amount,
                        currentPriceCurrency = result.currentPrice.currencyCode,
                    ),
                )
        }
    }

    /** The only way a customer sees their own request again — by the reference this endpoint's own create() call returned. */
    @GetMapping("/{reference}")
    fun findByReference(
        @PathVariable @NotBlank reference: String,
    ): ResponseEntity<TravelRequestPublicResponse> {
        val parsedReference = runCatching { TravelRequestReference(reference) }.getOrNull() ?: return ResponseEntity.notFound().build()
        val found = travelRequestQueryService.findByReference(parsedReference) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(found.toPublicResponse())
    }
}

fun TravelRequest.toPublicResponse() =
    TravelRequestPublicResponse(
        reference = reference.value,
        status = status,
        serviceName = serviceName.toDto(),
        optionLabel = optionLabel.toDto(),
        priceAmount = price.amount,
        priceCurrency = price.currencyCode,
        priceBasis = priceBasis,
        cancellationPolicy = cancellationPolicy.toDto(),
        requestedDate = requestedDate,
        requestedTime = requestedTime,
        adults = adults,
        children = children,
        hotelOrPickup = hotelOrPickup,
        createdAt = createdAt,
        confirmedAt = confirmedAt,
        completedAt = completedAt,
        cancelledAt = cancelledAt,
        expiredAt = expiredAt,
    )
