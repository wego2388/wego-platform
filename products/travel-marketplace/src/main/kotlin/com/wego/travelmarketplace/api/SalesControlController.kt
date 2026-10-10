package com.wego.travelmarketplace.api

import com.wego.identity.AuthenticatedUser
import com.wego.travelmarketplace.application.SalesControlService
import com.wego.travelmarketplace.application.UpdateSalesControlResult
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/travel-marketplace")
class SalesControlController(
    private val service: SalesControlService,
) {
    /** No staff reason, actor or timestamp is exposed publicly. */
    @GetMapping("/public/sales-status")
    fun publicStatus(): ResponseEntity<PublicSalesStatus> =
        ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(PublicSalesStatus(!service.current().requestsPaused))

    @GetMapping("/sales-control")
    @PreAuthorize("hasAuthority('travel-sales:manage')")
    fun current(): ResponseEntity<Any> = ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.current())

    @PutMapping("/sales-control")
    @PreAuthorize("hasAuthority('travel-sales:manage')")
    fun update(
        @Valid @RequestBody request: UpdateSalesControlRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actor = (authentication.principal as? AuthenticatedUser)?.userId ?: return ResponseEntity.status(403).build()
        return when (
            val result =
                service.update(
                    requireNotNull(request.requestsPaused),
                    requireNotNull(request.expectedVersion),
                    request.reason,
                    actor,
                )
        ) {
            is UpdateSalesControlResult.Updated -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result.control)
            is UpdateSalesControlResult.VersionConflict ->
                ResponseEntity
                    .status(
                        409,
                    ).cacheControl(CacheControl.noStore())
                    .body(SalesControlConflict("version_conflict", result.currentVersion))
        }
    }
}

data class PublicSalesStatus(
    val requestsOpen: Boolean,
)

data class SalesControlConflict(
    val error: String,
    val currentVersion: Long,
)

data class UpdateSalesControlRequest(
    @field:NotNull val requestsPaused: Boolean?,
    @field:NotNull @field:Min(0) val expectedVersion: Long?,
    @field:Size(max = 300) val reason: String?,
)
