package com.wego.travelmarketplace.api

import com.wego.identity.AuthenticatedUser
import com.wego.travelmarketplace.application.CancelTravelRequestCommand
import com.wego.travelmarketplace.application.CancelTravelRequestResult
import com.wego.travelmarketplace.application.CancelTravelRequestService
import com.wego.travelmarketplace.application.CompleteTravelRequestResult
import com.wego.travelmarketplace.application.CompleteTravelRequestService
import com.wego.travelmarketplace.application.ConfirmTravelRequestResult
import com.wego.travelmarketplace.application.ConfirmTravelRequestService
import com.wego.travelmarketplace.application.StartTravelRequestReviewResult
import com.wego.travelmarketplace.application.StartTravelRequestReviewService
import com.wego.travelmarketplace.application.TravelRequestAuditQueryService
import com.wego.travelmarketplace.application.TravelRequestQueryService
import com.wego.travelmarketplace.domain.TravelRequest
import com.wego.travelmarketplace.domain.TravelRequestActorType
import com.wego.travelmarketplace.domain.TravelRequestId
import com.wego.travelmarketplace.domain.TravelRequestStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
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
import java.util.UUID

@Validated
@RestController
@RequestMapping("/api/v1/travel-marketplace/requests")
class TravelRequestController(
    private val travelRequestQueryService: TravelRequestQueryService,
    private val travelRequestAuditQueryService: TravelRequestAuditQueryService,
    private val startTravelRequestReviewService: StartTravelRequestReviewService,
    private val confirmTravelRequestService: ConfirmTravelRequestService,
    private val cancelTravelRequestService: CancelTravelRequestService,
    private val completeTravelRequestService: CompleteTravelRequestService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('travel-request:view')")
    fun list(
        @RequestParam(required = false) status: TravelRequestStatus?,
        @RequestParam(required = false, defaultValue = "0") @Min(0) page: Int,
        @RequestParam(required = false, defaultValue = "50") @Min(1) @Max(200) size: Int,
    ): List<TravelRequestStaffResponse> = travelRequestQueryService.list(status, page, size).map { it.toStaffResponse() }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('travel-request:view')")
    fun findById(
        @PathVariable id: UUID,
    ): ResponseEntity<TravelRequestStaffResponse> {
        val request = travelRequestQueryService.findById(TravelRequestId(id)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(request.toStaffResponse())
    }

    @GetMapping("/{id}/audit")
    @PreAuthorize("hasAuthority('travel-request:view')")
    fun auditHistory(
        @PathVariable id: UUID,
    ): List<TravelRequestAuditEventResponse> =
        travelRequestAuditQueryService.findByRequestId(id).map {
            TravelRequestAuditEventResponse(
                id = it.id,
                occurredAt = it.occurredAt,
                fromStatus = it.fromStatus,
                toStatus = it.toStatus,
                actorType = it.actorType,
                actorUserId = it.actorUserId,
                reason = it.reason,
                detail = it.detail,
            )
        }

    @PostMapping("/{id}/start-review")
    @PreAuthorize("hasAuthority('travel-request:review')")
    fun startReview(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (val result = startTravelRequestReviewService.start(TravelRequestId(id), actorUserId, null)) {
            is StartTravelRequestReviewResult.Started -> ResponseEntity.ok(result.request.toStaffResponse())
            StartTravelRequestReviewResult.NotFound -> ResponseEntity.notFound().build()
            StartTravelRequestReviewResult.InvalidTransition ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(TravelRequestErrorResponse("invalid_transition"))
        }
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('travel-request:confirm')")
    fun confirm(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (val result = confirmTravelRequestService.confirm(TravelRequestId(id), actorUserId, null)) {
            is ConfirmTravelRequestResult.Confirmed -> ResponseEntity.ok(result.request.toStaffResponse())
            ConfirmTravelRequestResult.NotFound -> ResponseEntity.notFound().build()
            ConfirmTravelRequestResult.InvalidTransition ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(TravelRequestErrorResponse("invalid_transition"))
        }
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('travel-request:cancel')")
    fun cancel(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CancelTravelRequestApiRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        val command =
            CancelTravelRequestCommand(
                requestId = TravelRequestId(id),
                reason = request.reason,
                detail = request.detail,
                actorType = TravelRequestActorType.STAFF,
                actorUserId = actorUserId,
                correlationId = null,
            )
        return when (val result = cancelTravelRequestService.cancel(command)) {
            is CancelTravelRequestResult.Cancelled -> ResponseEntity.ok(result.request.toStaffResponse())
            CancelTravelRequestResult.NotFound -> ResponseEntity.notFound().build()
            CancelTravelRequestResult.AlreadyTerminal ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(TravelRequestErrorResponse("already_terminal"))
        }
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('travel-request:complete')")
    fun complete(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (val result = completeTravelRequestService.complete(TravelRequestId(id), actorUserId, null)) {
            is CompleteTravelRequestResult.Completed -> ResponseEntity.ok(result.request.toStaffResponse())
            CompleteTravelRequestResult.NotFound -> ResponseEntity.notFound().build()
            CompleteTravelRequestResult.InvalidTransition ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(TravelRequestErrorResponse("invalid_transition"))
        }
    }
}

private fun TravelRequest.toStaffResponse() =
    TravelRequestStaffResponse(
        id = id.value,
        reference = reference.value,
        status = status,
        serviceId = serviceId.value,
        serviceOptionId = serviceOptionId,
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
        locale = locale,
        notes = notes,
        sourceChannel = sourceChannel,
        customer = TravelRequestCustomerDto(customer.name, customer.phone, customer.email),
        createdAt = createdAt,
        confirmedAt = confirmedAt,
        completedAt = completedAt,
        cancelledAt = cancelledAt,
        cancelReason = cancelReason,
        cancelDetail = cancelDetail,
        expiredAt = expiredAt,
    )
