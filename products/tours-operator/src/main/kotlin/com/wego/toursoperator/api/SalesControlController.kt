package com.wego.toursoperator.api

import com.wego.identity.AuthenticatedUser
import com.wego.toursoperator.application.SalesControlService
import com.wego.toursoperator.application.UpdateSalesControlCommand
import com.wego.toursoperator.domain.SalesControl
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/**
 * Emergency sales control: a public read so the site can tell visitors that
 * online booking is paused, and a staff switch for managers.
 */
@Validated
@RestController("toursOperatorSalesControlController")
@RequestMapping("/api/v1/tours-operator")
class SalesControlController(
    private val salesControlService: SalesControlService,
) {
    /** Public — only the two flags; never the staff note or who changed it. */
    @GetMapping("/sales-status")
    fun publicStatus(): PublicSalesStatusResponse =
        salesControlService.current().let {
            PublicSalesStatusResponse(bookingsOpen = !it.bookingsPaused, paymentsOpen = !it.paymentsPaused)
        }

    @GetMapping("/staff/sales-control")
    @PreAuthorize("hasAuthority('tours-operator.tour:manage')")
    fun current(): SalesControlResponse = salesControlService.current().toResponse()

    @PutMapping("/staff/sales-control")
    @PreAuthorize("hasAuthority('tours-operator.tour:manage')")
    fun update(
        @Valid @RequestBody request: UpdateSalesControlRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actor =
            (authentication.principal as? AuthenticatedUser)?.userId
                ?: return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        val control =
            salesControlService.update(
                UpdateSalesControlCommand(
                    bookingsPaused = requireNotNull(request.bookingsPaused),
                    paymentsPaused = requireNotNull(request.paymentsPaused),
                    reason = request.reason,
                    actorUserId = actor,
                ),
            )
        return ResponseEntity.ok(control.toResponse())
    }
}

data class PublicSalesStatusResponse(
    val bookingsOpen: Boolean,
    val paymentsOpen: Boolean,
)

data class UpdateSalesControlRequest(
    // Nullable + @NotNull so a missing field is a 400, never a silent "false".
    @field:NotNull
    val bookingsPaused: Boolean?,
    @field:NotNull
    val paymentsPaused: Boolean?,
    @field:Size(max = SalesControl.MAX_REASON_LENGTH)
    val reason: String? = null,
)

data class SalesControlResponse(
    val bookingsPaused: Boolean,
    val paymentsPaused: Boolean,
    val reason: String?,
    val updatedByUserId: UUID?,
    val updatedAt: Instant?,
)

private fun SalesControl.toResponse() =
    SalesControlResponse(
        bookingsPaused = bookingsPaused,
        paymentsPaused = paymentsPaused,
        reason = reason,
        updatedByUserId = updatedByUserId,
        updatedAt = updatedAt,
    )
