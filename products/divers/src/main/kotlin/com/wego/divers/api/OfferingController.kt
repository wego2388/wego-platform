package com.wego.divers.api

import com.wego.divers.application.CloseOfferingResult
import com.wego.divers.application.CloseOfferingService
import com.wego.divers.application.CreateOfferingCommand
import com.wego.divers.application.CreateOfferingService
import com.wego.divers.application.OfferingQueryService
import com.wego.divers.domain.Money
import com.wego.divers.domain.Offering
import com.wego.divers.domain.OfferingId
import com.wego.divers.domain.OfferingStatus
import com.wego.divers.domain.OfferingType
import com.wego.events.CorrelationContext
import com.wego.identity.AuthenticatedUser
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
@RequestMapping("/api/v1/divers/offerings")
class OfferingController(
    private val createOfferingService: CreateOfferingService,
    private val closeOfferingService: CloseOfferingService,
    private val offeringQueryService: OfferingQueryService,
) {
    @PostMapping
    @PreAuthorize("hasAuthority('offering:manage')")
    fun create(
        @Valid @RequestBody request: CreateOfferingRequest,
        authentication: Authentication,
    ): ResponseEntity<OfferingResponse> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        val offering =
            createOfferingService.create(
                CreateOfferingCommand(
                    offeringType = request.offeringType,
                    title = request.title,
                    description = request.description,
                    startsOn = request.startsOn,
                    endsOn = request.endsOn,
                    capacity = request.capacity,
                    pricingBasis = request.pricingBasis,
                    unitPrice = parseMoney(request.unitPrice),
                    createdByUserId = actorUserId,
                    correlationId = CorrelationContext.currentCorrelationId(),
                ),
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(offering.toResponse())
    }

    @GetMapping
    @PreAuthorize("hasAuthority('offering:view')")
    fun list(
        @RequestParam(required = false) type: OfferingType?,
        @RequestParam(required = false) status: OfferingStatus?,
        @RequestParam(required = false, defaultValue = "0") @Min(0) page: Int,
        @RequestParam(required = false, defaultValue = "50") @Min(1) @Max(200) size: Int,
    ): List<OfferingResponse> = offeringQueryService.list(type, status, page, size).map { it.toResponse() }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('offering:view')")
    fun findById(
        @PathVariable id: UUID,
    ): ResponseEntity<OfferingResponse> {
        val offering = offeringQueryService.findById(OfferingId(id)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(offering.toResponse())
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('offering:manage')")
    fun close(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CloseOfferingRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                closeOfferingService.close(OfferingId(id), actorUserId, request.reason, CorrelationContext.currentCorrelationId())
        ) {
            is CloseOfferingResult.Closed -> ResponseEntity.ok(result.offering.toResponse())
            CloseOfferingResult.NotFound -> ResponseEntity.notFound().build()
            CloseOfferingResult.AlreadyClosed ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(BookingErrorResponse("already_closed"))
        }
    }
}

private fun parseMoney(dto: MoneyDto): Money {
    val amount = dto.amount.toBigDecimalOrNull() ?: throw IllegalArgumentException("Price amount must be a valid decimal number")
    return Money(amount, dto.currencyCode)
}

private fun Offering.toResponse() =
    OfferingResponse(
        id = id.value,
        offeringType = offeringType,
        title = title,
        description = description,
        startsOn = startsOn,
        endsOn = endsOn,
        capacity = capacity,
        pricingBasis = pricingBasis,
        unitPrice = MoneyDto(unitPrice.amount.toPlainString(), unitPrice.currencyCode),
        status = status,
        createdAt = createdAt,
        closedAt = closedAt,
    )
