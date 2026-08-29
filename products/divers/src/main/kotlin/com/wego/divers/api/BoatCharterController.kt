package com.wego.divers.api

import com.wego.divers.application.BoatCharterQueryService
import com.wego.divers.application.CreateBoatCharterCommand
import com.wego.divers.application.CreateBoatCharterService
import com.wego.divers.application.EndCharterResult
import com.wego.divers.application.EndCharterService
import com.wego.divers.application.UpdateBoatCharterCommand
import com.wego.divers.application.UpdateBoatCharterResult
import com.wego.divers.application.UpdateBoatCharterService
import com.wego.divers.domain.BoatCharter
import com.wego.divers.domain.BoatCharterId
import com.wego.divers.domain.CharterStatus
import com.wego.divers.domain.CharterType
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
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Validated
@RestController
@RequestMapping("/api/v1/divers/boat-charters")
class BoatCharterController(
    private val createBoatCharterService: CreateBoatCharterService,
    private val updateBoatCharterService: UpdateBoatCharterService,
    private val endCharterService: EndCharterService,
    private val boatCharterQueryService: BoatCharterQueryService,
) {
    @PostMapping
    @PreAuthorize("hasAuthority('boat-charter:manage')")
    fun create(
        @Valid @RequestBody request: CreateBoatCharterRequest,
        authentication: Authentication,
    ): ResponseEntity<BoatCharterResponse> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        val charter =
            createBoatCharterService.create(
                CreateBoatCharterCommand(
                    boatName = request.boatName,
                    charterType = request.charterType,
                    licensedCapacity = request.licensedCapacity,
                    startsOn = request.startsOn,
                    endsOn = request.endsOn,
                    notes = request.notes,
                    createdByUserId = actorUserId,
                    correlationId = CorrelationContext.currentCorrelationId(),
                ),
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(charter.toResponse())
    }

    @GetMapping
    @PreAuthorize("hasAuthority('boat-charter:view')")
    fun list(
        @RequestParam(required = false) type: CharterType?,
        @RequestParam(required = false) status: CharterStatus?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false, defaultValue = "0") @Min(0) page: Int,
        @RequestParam(required = false, defaultValue = "50") @Min(1) @Max(200) size: Int,
    ): List<BoatCharterResponse> = boatCharterQueryService.list(type, status, search, page, size).map { it.toResponse() }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('boat-charter:view')")
    fun findById(
        @PathVariable id: UUID,
    ): ResponseEntity<BoatCharterResponse> {
        val charter = boatCharterQueryService.findById(BoatCharterId(id)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(charter.toResponse())
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('boat-charter:manage')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateBoatCharterRequest,
    ): ResponseEntity<Any> =
        when (
            val result =
                updateBoatCharterService.update(
                    UpdateBoatCharterCommand(
                        BoatCharterId(id),
                        request.boatName,
                        request.licensedCapacity,
                        request.startsOn,
                        request.endsOn,
                        request.notes,
                    ),
                )
        ) {
            is UpdateBoatCharterResult.Updated -> ResponseEntity.ok(result.charter.toResponse())
            UpdateBoatCharterResult.NotFound -> ResponseEntity.notFound().build()
            UpdateBoatCharterResult.CapacityBelowLinkedOfferings ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(BoatCharterErrorResponse("capacity_below_linked_offerings"))
        }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasAuthority('boat-charter:manage')")
    fun end(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (val result = endCharterService.end(BoatCharterId(id), actorUserId, CorrelationContext.currentCorrelationId())) {
            is EndCharterResult.Ended -> ResponseEntity.ok(result.charter.toResponse())
            EndCharterResult.NotFound -> ResponseEntity.notFound().build()
            EndCharterResult.AlreadyEnded -> ResponseEntity.status(HttpStatus.CONFLICT).body(BoatCharterErrorResponse("already_ended"))
        }
    }
}

private fun BoatCharter.toResponse() =
    BoatCharterResponse(
        id = id.value,
        boatName = boatName,
        charterType = charterType,
        licensedCapacity = licensedCapacity,
        startsOn = startsOn,
        endsOn = endsOn,
        notes = notes,
        status = status,
        createdAt = createdAt,
        endedAt = endedAt,
    )
