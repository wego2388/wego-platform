package com.wego.toursoperator.api

import com.wego.events.CorrelationContext
import com.wego.toursoperator.application.InitiatePaymentResult
import com.wego.toursoperator.application.InitiatePaymentService
import com.wego.toursoperator.application.PaymentActivity
import com.wego.toursoperator.application.PaymentHistoryEntry
import com.wego.toursoperator.application.PaymentQueryService
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.Payment
import com.wego.toursoperator.domain.PaymentId
import com.wego.toursoperator.domain.PaymentStatus
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * Payment endpoints for the public booking flow.
 *
 * POST /api/v1/tours-operator/bookings/{bookingId}/pay
 *   — Initiate payment for a NEW booking. Returns a Paymob checkout URL.
 *   — Public (no auth) — booking owner has the bookingId from creation.
 *
 * GET  /api/v1/tours-operator/bookings/{bookingId}/payment-status
 *   — Returns the current payment status. Used by the return URL page
 *     to poll after the customer returns from the Paymob iframe.
 *   — Public (no auth).
 */
@Validated
@RestController("toursOperatorPaymentController")
@RequestMapping("/api/v1/tours-operator")
class PaymentController(
    private val initiatePaymentService: InitiatePaymentService,
    private val paymentQueryService: PaymentQueryService,
) {
    /** Initiate Paymob checkout for a NEW booking. */
    @PostMapping("/bookings/{bookingId}/pay")
    fun initiatePayment(
        @PathVariable bookingId: UUID,
    ): ResponseEntity<Any> {
        val result =
            initiatePaymentService.initiate(
                com.wego.toursoperator.application.InitiatePaymentCommand(
                    bookingId = BookingId(bookingId),
                    correlationId = CorrelationContext.currentCorrelationId(),
                ),
            )
        return when (result) {
            is InitiatePaymentResult.Initiated ->
                ResponseEntity.status(HttpStatus.CREATED).body(result.toResponse())

            is InitiatePaymentResult.AlreadyInitiated ->
                ResponseEntity.ok(result.toResponse())

            InitiatePaymentResult.BookingNotFound ->
                ResponseEntity.notFound().build()

            InitiatePaymentResult.PaymentsPaused ->
                ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ErrorResponse("payments_paused"))

            is InitiatePaymentResult.BookingNotPayable ->
                ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ErrorResponse("booking_not_payable_status_${result.bookingStatus.name.lowercase()}"))

            is InitiatePaymentResult.PaymentNotPayable ->
                ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ErrorResponse("payment_not_payable_status_${result.paymentStatus.name.lowercase()}"))

            InitiatePaymentResult.ReconciliationRequired ->
                ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(ErrorResponse("payment_reconciliation_required"))

            is InitiatePaymentResult.ProviderError ->
                ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(ErrorResponse("payment_provider_error"))
        }
    }

    /** Poll payment status after returning from Paymob checkout. */
    @GetMapping("/bookings/{bookingId}/payment-status")
    fun getPaymentStatus(
        @PathVariable bookingId: UUID,
    ): ResponseEntity<Any> {
        val payment =
            paymentQueryService.findByBookingId(BookingId(bookingId))
                ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(payment.toStatusResponse())
    }

    /** Staff-only payment status history for one booking, oldest first. */
    @GetMapping("/staff/bookings/{bookingId}/payment-history")
    @PreAuthorize("hasAuthority('tours-operator.payment:view')")
    fun paymentHistory(
        @PathVariable bookingId: UUID,
    ): List<PaymentHistoryEntryResponse> = paymentQueryService.historyForBooking(BookingId(bookingId)).map { it.toHistoryResponse() }

    /** Staff-only immutable payment ledger used by finance and reconciliation. */
    @GetMapping("/staff/payments")
    @PreAuthorize("hasAuthority('tours-operator.payment:view')")
    fun listLedger(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) status: PaymentStatus?,
        @RequestParam(required = false) after: UUID?,
        @RequestParam(required = false, defaultValue = "50") @Min(1) @Max(200) size: Int,
    ): ResponseEntity<Any> {
        if (to.isBefore(from)) {
            return ResponseEntity.badRequest().body(ErrorResponse("invalid_date_range"))
        }
        val cairo = ZoneId.of("Africa/Cairo")
        val payments =
            paymentQueryService.listActivity(
                from.atStartOfDay(cairo).toInstant(),
                to.plusDays(1).atStartOfDay(cairo).toInstant(),
                status,
                after?.let(::PaymentId),
                size,
            )
        return ResponseEntity.ok(payments.map { it.toLedgerResponse() })
    }
}

// ── Response DTOs ─────────────────────────────────────────────────────────────

data class InitiatePaymentResponse(
    val paymentId: UUID,
    val bookingId: UUID,
    val checkoutUrl: String,
    val amountEur: String,
    val currencyCode: String,
    val status: PaymentStatus,
)

data class PaymentStatusResponse(
    val paymentId: UUID,
    val bookingId: UUID,
    val amountEur: String,
    val currencyCode: String,
    val status: PaymentStatus,
    val paidAt: Instant?,
    val failedAt: Instant?,
    val refundedAt: Instant?,
)

data class PaymentLedgerEntryResponse(
    val paymentId: UUID,
    val bookingId: UUID,
    val tourId: UUID,
    val adultsCount: Int,
    val childrenCount: Int,
    val amount: MoneyResponse,
    val status: PaymentStatus,
    val createdAt: Instant,
    val paidAt: Instant?,
    val failedAt: Instant?,
    val refundedAt: Instant?,
    val revenueRecognisedAt: Instant?,
)

private fun InitiatePaymentResult.Initiated.toResponse() =
    InitiatePaymentResponse(
        paymentId = payment.id.value,
        bookingId = payment.bookingId.value,
        checkoutUrl = checkoutUrl,
        amountEur = payment.amountEur.toPlainString(),
        currencyCode = payment.currencyCode,
        status = payment.status,
    )

private fun InitiatePaymentResult.AlreadyInitiated.toResponse() =
    InitiatePaymentResponse(
        paymentId = payment.id.value,
        bookingId = payment.bookingId.value,
        checkoutUrl = checkoutUrl,
        amountEur = payment.amountEur.toPlainString(),
        currencyCode = payment.currencyCode,
        status = payment.status,
    )

private fun Payment.toStatusResponse() =
    PaymentStatusResponse(
        paymentId = id.value,
        bookingId = bookingId.value,
        amountEur = amountEur.toPlainString(),
        currencyCode = currencyCode,
        status = status,
        paidAt = paidAt,
        failedAt = failedAt,
        refundedAt = refundedAt,
    )

private fun PaymentActivity.toLedgerResponse() =
    PaymentLedgerEntryResponse(
        paymentId = payment.id.value,
        bookingId = payment.bookingId.value,
        tourId = tourId.value,
        adultsCount = adultsCount,
        childrenCount = childrenCount,
        amount = MoneyResponse(payment.amountEur.toPlainString(), payment.currencyCode),
        status = payment.status,
        createdAt = payment.createdAt,
        paidAt = payment.paidAt,
        failedAt = payment.failedAt,
        refundedAt = payment.refundedAt,
        revenueRecognisedAt = payment.revenueRecognisedAt,
    )

data class PaymentHistoryEntryResponse(
    val paymentId: UUID,
    val fromStatus: PaymentStatus?,
    val toStatus: PaymentStatus,
    val providerStatus: String?,
    val occurredAt: Instant,
    val recorded: Boolean,
)

private fun PaymentHistoryEntry.toHistoryResponse() =
    PaymentHistoryEntryResponse(
        paymentId = paymentId.value,
        fromStatus = fromStatus,
        toStatus = toStatus,
        providerStatus = providerStatus,
        occurredAt = occurredAt,
        recorded = recorded,
    )
