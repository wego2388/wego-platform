package com.wego.toursoperator.api

import com.wego.identity.AuthenticatedUser
import com.wego.toursoperator.application.AdjustCommand
import com.wego.toursoperator.application.ApproveCommand
import com.wego.toursoperator.application.CashBoxService
import com.wego.toursoperator.application.CostInput
import com.wego.toursoperator.application.CostService
import com.wego.toursoperator.application.DocumentLanguage
import com.wego.toursoperator.application.DocumentResult
import com.wego.toursoperator.application.FailureKind
import com.wego.toursoperator.application.FinanceResult
import com.wego.toursoperator.application.OfficeDocumentService
import com.wego.toursoperator.application.OfficeRefundService
import com.wego.toursoperator.application.PartySummary
import com.wego.toursoperator.application.PayCommand
import com.wego.toursoperator.application.PayablesService
import com.wego.toursoperator.application.ProfitGrouping
import com.wego.toursoperator.application.ProfitabilityService
import com.wego.toursoperator.application.RecordRefundCommand
import com.wego.toursoperator.application.ReverseEntryCommand
import com.wego.toursoperator.application.ReverseRefundCommand
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.PaidCurrency
import com.wego.toursoperator.domain.PartyRef
import com.wego.toursoperator.domain.PartyType
import com.wego.toursoperator.domain.TourSlotId
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.CacheControl
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
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

private fun actorOf(authentication: Authentication): UUID = (authentication.principal as AuthenticatedUser).userId

private fun <T : Any> finance(body: T): ResponseEntity<Any> = ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body)

/** Maps an OPS2-F write outcome: 201 when written, 200 for an idempotent replay, otherwise the failure's status and code. */
private fun <T> respond(
    result: FinanceResult<T>,
    map: (T) -> Any,
): ResponseEntity<Any> =
    when (result) {
        is FinanceResult.Ok ->
            ResponseEntity
                .status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
                .cacheControl(CacheControl.noStore())
                .body(map(result.value))
        is FinanceResult.Failed -> {
            val status =
                when (result.kind) {
                    FailureKind.NOT_FOUND -> HttpStatus.NOT_FOUND
                    FailureKind.INVALID -> HttpStatus.UNPROCESSABLE_ENTITY
                    FailureKind.CONFLICT -> HttpStatus.CONFLICT
                    FailureKind.FORBIDDEN -> HttpStatus.FORBIDDEN
                }
            ResponseEntity.status(status).body(FinanceErrorResponse(result.code, result.details))
        }
    }

private fun badRange(): ResponseEntity<Any> = ResponseEntity.badRequest().body(ErrorResponse("invalid_date_range"))

private fun invalidRange(
    from: LocalDate,
    to: LocalDate,
): Boolean = to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > ProfitabilityService.MAX_RANGE_DAYS

private fun partyRef(
    type: String,
    id: UUID,
): PartyRef? =
    when (type) {
        "suppliers" -> PartyRef(PartyType.SUPPLIER, id)
        "drivers" -> PartyRef(PartyType.DRIVER, id)
        else -> null
    }

/**
 * WEGO-016-OPS2-F tour costs and driver trip rates. Changing a cost never edits
 * it: "replace" ends it the day before the new value and adds the new one,
 * "end" stops it. Read with the cost or the finance permission.
 */
@Validated
@RestController("toursOperatorCostController")
@RequestMapping("/api/v1/tours-operator/staff/costs")
class CostController(
    private val costs: CostService,
) {
    @GetMapping
    @PreAuthorize("hasAnyAuthority('tours-operator.cost:manage', 'tours-operator.payment:view')")
    fun list(
        @RequestParam(required = false) tourId: UUID?,
        @RequestParam(required = false) driverId: UUID?,
        @RequestParam(required = false, defaultValue = "false") includeEnded: Boolean,
    ): ResponseEntity<Any> = finance(costs.list(tourId, driverId, includeEnded).map { it.toResponse() })

    @PostMapping
    @PreAuthorize("hasAuthority('tours-operator.cost:manage')")
    fun create(
        @Valid @RequestBody request: CostComponentRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> = respond(costs.create(request.toInput(), actorOf(authentication))) { it.toResponse() }

    @PostMapping("/{id}/replace")
    @PreAuthorize("hasAuthority('tours-operator.cost:manage')")
    fun replace(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CostComponentRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> = respond(costs.replace(id, request.toInput(), actorOf(authentication))) { it.toResponse() }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasAuthority('tours-operator.cost:manage')")
    fun end(
        @PathVariable id: UUID,
        @Valid @RequestBody request: CostEndRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> =
        respond(costs.end(id, request.lastDay, actorOf(authentication))) { it.toResponse() }.let {
            // Ending changes an existing row: 200, not 201.
            if (it.statusCode == HttpStatus.CREATED) ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(it.body) else it
        }

    private fun CostComponentRequest.toInput() =
        CostInput(
            tourId,
            driverId,
            category,
            label,
            basis,
            currency,
            amount,
            childAmount,
            supplierId,
            validFrom,
            validUntil,
            note,
            clientRequestId,
        )
}

/**
 * Read-only finance reports (OPS2-F) on the existing finance permission
 * `tours-operator.payment:view`: profitability and the office-payments line.
 */
@Validated
@RestController("toursOperatorFinanceReportController")
@RequestMapping("/api/v1/tours-operator/staff/finance")
class FinanceReportController(
    private val profitability: ProfitabilityService,
) {
    @GetMapping("/profitability")
    @PreAuthorize("hasAuthority('tours-operator.payment:view')")
    fun profitability(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false, defaultValue = "DEPARTURE") groupBy: ProfitGrouping,
    ): ResponseEntity<Any> {
        if (invalidRange(from, to)) return badRange()
        return finance(profitability.report(from, to, groupBy).toResponse())
    }

    @GetMapping("/office-summary")
    @PreAuthorize("hasAuthority('tours-operator.payment:view')")
    fun officeSummary(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): ResponseEntity<Any> {
        if (invalidRange(from, to)) return badRange()
        return finance(profitability.officeSummary(from, to).toResponse())
    }
}

/** Money returned on a cancelled office booking (manager permission). Never a Paymob refund. */
@Validated
@RestController("toursOperatorOfficeRefundController")
@RequestMapping("/api/v1/tours-operator/staff/bookings/{bookingId}/refunds")
class OfficeRefundController(
    private val refunds: OfficeRefundService,
) {
    @GetMapping
    @PreAuthorize("hasAuthority('tours-operator.booking:view')")
    fun list(
        @PathVariable bookingId: UUID,
    ): ResponseEntity<Any> {
        val id = BookingId(bookingId)
        return finance(OfficeRefundsResponse(refunds.entries(id).map { it.toResponse() }, refunds.position(id).toResponse()))
    }

    @PostMapping
    @PreAuthorize("hasAuthority('tours-operator.booking:refund-office')")
    fun record(
        @PathVariable bookingId: UUID,
        @Valid @RequestBody request: RecordRefundRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> =
        respond(
            refunds.record(
                RecordRefundCommand(
                    BookingId(bookingId),
                    request.method,
                    request.amount,
                    request.currency ?: PaidCurrency.EUR,
                    request.reference?.trim()?.takeIf { it.isNotEmpty() },
                    request.fxRateId,
                    request.reason,
                    actorOf(authentication),
                    request.clientRequestId,
                ),
            ),
        ) { OfficeRefundOutcomeResponse(it.entry.toResponse(), it.position.toResponse(), it.warning) }

    @PostMapping("/{refundId}/reverse")
    @PreAuthorize("hasAuthority('tours-operator.booking:refund-office')")
    fun reverse(
        @PathVariable bookingId: UUID,
        @PathVariable refundId: UUID,
        @Valid @RequestBody request: ReverseEntryRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> =
        respond(
            refunds.reverse(
                ReverseRefundCommand(BookingId(bookingId), refundId, request.reason, actorOf(authentication), request.clientRequestId),
            ),
        ) { OfficeRefundOutcomeResponse(it.entry.toResponse(), it.position.toResponse()) }
}

/**
 * Supplier and driver payables (OPS2-F). Viewing: settlement:pay,
 * settlement:approve or payment:view. Paying (≤ 5000 EGP per payment) and
 * adjustments: settlement:pay. Approving above the limit and reversing:
 * settlement:approve. `{partyType}` is `suppliers` or `drivers`.
 */
@Validated
@RestController("toursOperatorPayablesController")
@RequestMapping("/api/v1/tours-operator/staff/payables")
class PayablesController(
    private val payables: PayablesService,
) {
    @GetMapping
    @PreAuthorize("hasAnyAuthority('tours-operator.settlement:pay', 'tours-operator.settlement:approve', 'tours-operator.payment:view')")
    fun overview(): ResponseEntity<Any> = finance(payables.overview().map { it.toResponse() })

    @GetMapping("/{partyType}/{partyId}/statement")
    @PreAuthorize("hasAnyAuthority('tours-operator.settlement:pay', 'tours-operator.settlement:approve', 'tours-operator.payment:view')")
    fun statement(
        @PathVariable partyType: String,
        @PathVariable partyId: UUID,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): ResponseEntity<Any> {
        val party = partyRef(partyType, partyId) ?: return ResponseEntity.notFound().build()
        if (invalidRange(from, to)) return badRange()
        val statement = payables.statement(party, from, to) ?: return ResponseEntity.notFound().build()
        val summary =
            PartySummary(
                statement.info,
                statement.current,
                statement.issues.size,
                statement.approvals.count { it.usedByPaymentId == null },
            ).toResponse()
        return finance(statement.toResponse(summary))
    }

    @PostMapping("/{partyType}/{partyId}/payments")
    @PreAuthorize("hasAuthority('tours-operator.settlement:pay')")
    fun pay(
        @PathVariable partyType: String,
        @PathVariable partyId: UUID,
        @Valid @RequestBody request: PayRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val party = partyRef(partyType, partyId) ?: return ResponseEntity.notFound().build()
        return respond(
            payables.pay(
                PayCommand(
                    party,
                    request.method,
                    request.currency,
                    request.amount,
                    request.reference,
                    request.note,
                    request.approvalId,
                    actorOf(authentication),
                    request.clientRequestId,
                ),
            ),
        ) { it.toResponse() }
    }

    @PostMapping("/{partyType}/{partyId}/payments/{paymentId}/reverse")
    @PreAuthorize("hasAuthority('tours-operator.settlement:approve')")
    fun reversePayment(
        @PathVariable partyType: String,
        @PathVariable partyId: UUID,
        @PathVariable paymentId: UUID,
        @Valid @RequestBody request: ReverseEntryRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val party = partyRef(partyType, partyId) ?: return ResponseEntity.notFound().build()
        return respond(
            payables.reversePayment(
                ReverseEntryCommand(party, paymentId, request.reason, actorOf(authentication), request.clientRequestId),
            ),
        ) { it.toResponse() }
    }

    @PostMapping("/{partyType}/{partyId}/approvals")
    @PreAuthorize("hasAuthority('tours-operator.settlement:approve')")
    fun approve(
        @PathVariable partyType: String,
        @PathVariable partyId: UUID,
        @Valid @RequestBody request: ApproveRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val party = partyRef(partyType, partyId) ?: return ResponseEntity.notFound().build()
        return respond(
            payables.approve(
                ApproveCommand(party, request.currency, request.amount, request.note, actorOf(authentication), request.clientRequestId),
            ),
        ) { it.toResponse() }
    }

    @PostMapping("/{partyType}/{partyId}/adjustments")
    @PreAuthorize("hasAuthority('tours-operator.settlement:pay')")
    fun adjust(
        @PathVariable partyType: String,
        @PathVariable partyId: UUID,
        @Valid @RequestBody request: AdjustRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val party = partyRef(partyType, partyId) ?: return ResponseEntity.notFound().build()
        return respond(
            payables.adjust(
                AdjustCommand(
                    party,
                    request.slotId?.let(::TourSlotId),
                    request.serviceDate,
                    request.kind,
                    request.currency,
                    request.amount,
                    request.reason,
                    actorOf(authentication),
                    request.clientRequestId,
                    authentication.authorities.any { it.authority == "tours-operator.settlement:approve" },
                ),
            ),
        ) { it.toResponse() }
    }

    @PostMapping("/{partyType}/{partyId}/adjustments/{adjustmentId}/reverse")
    @PreAuthorize("hasAuthority('tours-operator.settlement:approve')")
    fun reverseAdjustment(
        @PathVariable partyType: String,
        @PathVariable partyId: UUID,
        @PathVariable adjustmentId: UUID,
        @Valid @RequestBody request: ReverseEntryRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val party = partyRef(partyType, partyId) ?: return ResponseEntity.notFound().build()
        return respond(
            payables.reverseAdjustment(
                ReverseEntryCommand(party, adjustmentId, request.reason, actorOf(authentication), request.clientRequestId),
            ),
        ) { it.toResponse() }
    }
}

/**
 * Daily office cash box per currency (OPS2-F). Count: cash-box:close
 * (reception). Confirm (closes the day) and reopen with a reason:
 * cash-box:confirm (manager), never the person who counted. Read with either,
 * or with the finance permission.
 */
@Validated
@RestController("toursOperatorCashBoxController")
@RequestMapping("/api/v1/tours-operator/staff/cash-box")
class CashBoxController(
    private val cashBox: CashBoxService,
) {
    @GetMapping
    @PreAuthorize("hasAnyAuthority('tours-operator.cash-box:close', 'tours-operator.cash-box:confirm', 'tours-operator.payment:view')")
    fun day(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @RequestParam currency: PaidCurrency,
    ): ResponseEntity<Any> = finance(cashBox.day(date, currency).toResponse())

    @GetMapping("/recent")
    @PreAuthorize("hasAnyAuthority('tours-operator.cash-box:close', 'tours-operator.cash-box:confirm', 'tours-operator.payment:view')")
    fun recent(
        @RequestParam(required = false, defaultValue = "14") @Min(1) @Max(92) days: Int,
    ): ResponseEntity<Any> = finance(cashBox.recent(days).map { it.toResponse() })

    @PostMapping("/{date}/{currency}/count")
    @PreAuthorize("hasAuthority('tours-operator.cash-box:close')")
    fun count(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @PathVariable currency: PaidCurrency,
        @Valid @RequestBody request: CashCountRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> =
        respond(cashBox.count(date, currency, request.counted, request.note, actorOf(authentication))) { it.toResponse() }

    @PostMapping("/{date}/{currency}/confirm")
    @PreAuthorize("hasAuthority('tours-operator.cash-box:confirm')")
    fun confirm(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @PathVariable currency: PaidCurrency,
        authentication: Authentication,
    ): ResponseEntity<Any> = respond(cashBox.confirm(date, currency, actorOf(authentication))) { it.toResponse() }

    @PostMapping("/{date}/{currency}/reopen")
    @PreAuthorize("hasAuthority('tours-operator.cash-box:confirm')")
    fun reopen(
        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @PathVariable currency: PaidCurrency,
        @Valid @RequestBody request: CashReopenRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> = respond(cashBox.reopen(date, currency, request.reason, actorOf(authentication))) { it.toResponse() }
}

/** Settlement statement (OPS2-F) in the OPS2-D print register: EN/AR, A4, numbered STL-YYYY-NNNNNN, reprints COPY/REVISED. */
@Validated
@RestController("toursOperatorSettlementStatementController")
@RequestMapping("/api/v1/tours-operator/documents/payables")
class SettlementStatementController(
    private val documents: OfficeDocumentService,
) {
    @PostMapping("/{partyType}/{partyId}/statement")
    @PreAuthorize("hasAnyAuthority('tours-operator.settlement:pay', 'tours-operator.settlement:approve')")
    fun statement(
        @PathVariable partyType: String,
        @PathVariable partyId: UUID,
        @Valid @RequestBody request: PrintStatementRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val party = partyRef(partyType, partyId) ?: return ResponseEntity.notFound().build()
        if (invalidRange(request.from, request.to)) return badRange()
        val language = checkNotNull(DocumentLanguage.fromCode(request.language))
        return when (val result = documents.settlementStatement(party, request.from, request.to, language, actorOf(authentication))) {
            is DocumentResult.Ready ->
                ResponseEntity
                    .ok()
                    .cacheControl(CacheControl.noStore())
                    .body(DocumentResponse(result.stamp.toStampResponse(), result.data))
            DocumentResult.NotFound -> ResponseEntity.notFound().build()
            is DocumentResult.Refused -> ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(result.code))
        }
    }
}
