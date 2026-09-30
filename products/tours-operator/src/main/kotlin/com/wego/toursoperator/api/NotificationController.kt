package com.wego.toursoperator.api

import com.wego.identity.AuthenticatedUser
import com.wego.toursoperator.application.NotificationListItem
import com.wego.toursoperator.application.NotificationRepository
import com.wego.toursoperator.application.ResendNotificationResult
import com.wego.toursoperator.application.ResendNotificationService
import com.wego.toursoperator.domain.NotificationId
import com.wego.toursoperator.domain.NotificationKind
import com.wego.toursoperator.domain.NotificationStatus
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/**
 * Staff view of customer notifications. Responses carry the booking
 * reference but never the recipient address or message body.
 */
@Validated
@RestController("toursOperatorNotificationController")
@RequestMapping("/api/v1/tours-operator/staff/notifications")
class NotificationController(
    @Qualifier("stoNotificationRepositoryImpl") private val notificationRepository: NotificationRepository,
    private val resendNotificationService: ResendNotificationService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('tours-operator.booking:view')")
    fun list(
        @RequestParam(required = false) status: NotificationStatus?,
        @RequestParam(required = false, defaultValue = "0") @Min(0) @Max(10_000) page: Int,
        @RequestParam(required = false, defaultValue = "50") @Min(1) @Max(200) size: Int,
    ): List<NotificationResponse> = notificationRepository.list(status, size, page * size).map { it.toResponse() }

    @PostMapping("/{id}/resend")
    @PreAuthorize("hasAuthority('tours-operator.notification:manage')")
    fun resend(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Any> =
        when (resendNotificationService.resend(NotificationId(id), (authentication.principal as? AuthenticatedUser)?.userId)) {
            ResendNotificationResult.Requeued -> ResponseEntity.accepted().build()
            ResendNotificationResult.NotFound -> ResponseEntity.notFound().build()
            ResendNotificationResult.BookingStateChanged ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse("booking_state_changed"))
        }
}

data class NotificationResponse(
    val id: UUID,
    val bookingId: UUID,
    val bookingReference: String,
    val kind: NotificationKind,
    val status: NotificationStatus,
    val attemptCount: Int,
    val lastError: String?,
    val availableAt: Instant,
    val createdAt: Instant,
    val sentAt: Instant?,
    val resendCount: Int,
    val lastResentAt: Instant?,
)

private fun NotificationListItem.toResponse() =
    NotificationResponse(
        id = notification.id.value,
        bookingId = notification.bookingId.value,
        bookingReference = bookingReference,
        kind = notification.kind,
        status = notification.status,
        attemptCount = notification.attemptCount,
        lastError = notification.lastError,
        availableAt = notification.availableAt,
        createdAt = notification.createdAt,
        sentAt = notification.sentAt,
        resendCount = notification.resendCount,
        lastResentAt = notification.lastResentAt,
    )
