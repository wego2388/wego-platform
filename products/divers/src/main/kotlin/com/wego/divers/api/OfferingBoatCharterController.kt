package com.wego.divers.api

import com.wego.divers.application.BoatCharterQueryService
import com.wego.divers.application.LinkOfferingToCharterCommand
import com.wego.divers.application.LinkOfferingToCharterResult
import com.wego.divers.application.LinkOfferingToCharterService
import com.wego.divers.application.UnlinkOfferingFromCharterResult
import com.wego.divers.application.UnlinkOfferingFromCharterService
import com.wego.divers.domain.BoatCharterId
import com.wego.divers.domain.OfferingBoatCharterLink
import com.wego.divers.domain.OfferingId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Validated
@RestController
@RequestMapping("/api/v1/divers/offerings/{offeringId}/boat-charter")
class OfferingBoatCharterController(
    private val linkOfferingToCharterService: LinkOfferingToCharterService,
    private val unlinkOfferingFromCharterService: UnlinkOfferingFromCharterService,
    private val boatCharterQueryService: BoatCharterQueryService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('boat-charter:view')")
    fun get(
        @PathVariable offeringId: UUID,
    ): ResponseEntity<OfferingBoatCharterLinkResponse> {
        val link = boatCharterQueryService.findLinkForOffering(OfferingId(offeringId)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(link.toResponse())
    }

    @PutMapping
    @PreAuthorize("hasAuthority('boat-charter:manage')")
    fun link(
        @PathVariable offeringId: UUID,
        @Valid @RequestBody request: LinkBoatCharterRequest,
    ): ResponseEntity<Any> =
        when (
            val result =
                linkOfferingToCharterService.link(
                    LinkOfferingToCharterCommand(OfferingId(offeringId), BoatCharterId(request.boatCharterId)),
                )
        ) {
            is LinkOfferingToCharterResult.Linked -> ResponseEntity.ok(result.link.toResponse())
            LinkOfferingToCharterResult.OfferingNotFound -> ResponseEntity.notFound().build()
            LinkOfferingToCharterResult.CharterNotFound ->
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(BoatCharterErrorResponse("charter_not_found"))
            LinkOfferingToCharterResult.CharterNotActive ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(BoatCharterErrorResponse("charter_not_active"))
            LinkOfferingToCharterResult.OfferingHasNoCapacity ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(BoatCharterErrorResponse("offering_has_no_capacity"))
            LinkOfferingToCharterResult.OfferingCapacityExceedsCharter ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(BoatCharterErrorResponse("offering_capacity_exceeds_charter"))
        }

    @DeleteMapping
    @PreAuthorize("hasAuthority('boat-charter:manage')")
    fun unlink(
        @PathVariable offeringId: UUID,
    ): ResponseEntity<Any> =
        when (unlinkOfferingFromCharterService.unlink(OfferingId(offeringId))) {
            UnlinkOfferingFromCharterResult.Unlinked -> ResponseEntity.noContent().build()
            UnlinkOfferingFromCharterResult.NoLink -> ResponseEntity.notFound().build()
        }
}

private fun OfferingBoatCharterLink.toResponse() =
    OfferingBoatCharterLinkResponse(
        offeringId = offeringId.value,
        boatCharterId = boatCharterId.value,
        linkedAt = linkedAt,
    )
