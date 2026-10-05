package com.wego.toursoperator.api

import com.wego.identity.AuthenticatedUser
import com.wego.toursoperator.application.BookingMode
import com.wego.toursoperator.application.SalesControlService
import com.wego.toursoperator.application.UpdateSalesControlCommand
import com.wego.toursoperator.domain.SalesControl
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.beans.factory.annotation.Qualifier
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
    @Qualifier("stoBookingMode") private val bookingMode: BookingMode = BookingMode.ONLINE_PAYMENT,
) {
    /** Public — capability and effective flags; never staff notes or actors. */
    @GetMapping("/sales-status")
    fun publicStatus(): PublicSalesStatusResponse =
        salesControlService.current().let {
            PublicSalesStatusResponse(
                bookingsOpen = bookingMode.onlineEnabled && !it.bookingsPaused && !it.paymentsPaused,
                paymentsOpen = bookingMode.onlineEnabled && !it.paymentsPaused,
                bookingMode = bookingMode,
            )
        }

    @GetMapping("/staff/sales-control")
    @PreAuthorize("hasAuthority('tours-operator.tour:manage')")
    fun current(): SalesControlResponse = salesControlService.current().toResponse(bookingMode)

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
        return ResponseEntity.ok(control.toResponse(bookingMode))
    }
}

data class PublicSalesStatusResponse(
    val bookingsOpen: Boolean,
    val paymentsOpen: Boolean,
    val bookingMode: BookingMode,
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
    val bookingMode: BookingMode,
)

private fun SalesControl.toResponse(mode: BookingMode) =
    SalesControlResponse(
        bookingsPaused = bookingsPaused,
        paymentsPaused = paymentsPaused,
        reason = reason,
        updatedByUserId = updatedByUserId,
        updatedAt = updatedAt,
        bookingMode = mode,
    )
