package com.wego.toursoperator.api

import com.wego.toursoperator.application.CashDayView
import com.wego.toursoperator.application.OfficeFinanceSummary
import com.wego.toursoperator.application.PartyStatement
import com.wego.toursoperator.application.PartySummary
import com.wego.toursoperator.application.ProfitGroup
import com.wego.toursoperator.application.ProfitGrouping
import com.wego.toursoperator.application.ProfitReport
import com.wego.toursoperator.application.RefundPosition
import com.wego.toursoperator.domain.AdjustmentKind
import com.wego.toursoperator.domain.BookingChannel
import com.wego.toursoperator.domain.BookingStatus
import com.wego.toursoperator.domain.CashBoxEvent
import com.wego.toursoperator.domain.CashBoxEventKind
import com.wego.toursoperator.domain.CashDayState
import com.wego.toursoperator.domain.CostBasis
import com.wego.toursoperator.domain.CostCategory
import com.wego.toursoperator.domain.CostComponent
import com.wego.toursoperator.domain.CostGap
import com.wego.toursoperator.domain.CurrencyBalance
import com.wego.toursoperator.domain.MovementKind
import com.wego.toursoperator.domain.OfficeRefund
import com.wego.toursoperator.domain.OfficeRefundKind
import com.wego.toursoperator.domain.OwedIssueCode
import com.wego.toursoperator.domain.PaidCurrency
import com.wego.toursoperator.domain.PartyType
import com.wego.toursoperator.domain.PayableAdjustment
import com.wego.toursoperator.domain.ProfitTotals
import com.wego.toursoperator.domain.RateSource
import com.wego.toursoperator.domain.RefundMethod
import com.wego.toursoperator.domain.SettlementApproval
import com.wego.toursoperator.domain.SettlementMethod
import com.wego.toursoperator.domain.SettlementPayment
import com.wego.toursoperator.domain.SettlementPaymentKind
import com.wego.toursoperator.domain.SettlementPolicy
import com.wego.toursoperator.domain.TimeSlot
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** An amount in a stated currency; [amount] may carry a leading minus sign where a figure can be negative. */
data class AmountResponse(
    val amount: String,
    val currencyCode: String,
)

private fun amount(
    value: BigDecimal,
    currency: PaidCurrency,
) = AmountResponse(value.setScale(2).toPlainString(), currency.name)

private fun eur(value: BigDecimal) = AmountResponse(value.setScale(2).toPlainString(), "EUR")

data class FinanceErrorResponse(
    val error: String,
    val details: Map<String, String>,
)

// ── Costs ────────────────────────────────────────────────────────────────────

data class CostComponentRequest(
    /** Idempotency key: a retry with the same key and payload returns 200 with what was created. */
    val clientRequestId: UUID,
    val tourId: UUID? = null,
    val driverId: UUID? = null,
    val category: CostCategory,
    @field:Size(min = 1, max = 80)
    val label: String,
    val basis: CostBasis,
    val currency: PaidCurrency,
    @field:DecimalMin("0.00") @field:Digits(integer = 7, fraction = 2)
    val amount: BigDecimal,
    @field:DecimalMin("0.00") @field:Digits(integer = 7, fraction = 2)
    val childAmount: BigDecimal? = null,
    val supplierId: UUID? = null,
    /** First day the value applies (for a replacement: the day the new value takes effect). */
    val validFrom: LocalDate,
    /** Last day it applies (inclusive); null = open-ended. Ignored on a replacement (it keeps the old end). */
    val validUntil: LocalDate? = null,
    @field:Size(max = 500)
    val note: String? = null,
)

data class CostEndRequest(
    /** Last day the component applies (inclusive). validFrom − 1 withdraws one that never took effect. */
    val lastDay: LocalDate,
)

data class CostComponentResponse(
    val id: UUID,
    val tourId: UUID?,
    val driverId: UUID?,
    val category: CostCategory,
    val label: String,
    val basis: CostBasis,
    val amount: AmountResponse,
    val childAmount: AmountResponse?,
    val supplierId: UUID?,
    val validFrom: LocalDate,
    val validUntil: LocalDate?,
    val replacesComponentId: UUID?,
    val note: String?,
    val createdAt: Instant,
    val ended: Boolean,
    val endedAt: Instant?,
)

fun CostComponent.toResponse() =
    CostComponentResponse(
        id = id,
        tourId = tourId?.value,
        driverId = driverId,
        category = category,
        label = label,
        basis = basis,
        amount = amount(amount, currency),
        childAmount = childAmount?.let { amount(it, currency) },
        supplierId = supplierId,
        validFrom = validFrom,
        validUntil = validUntil,
        replacesComponentId = replacesComponentId,
        note = note,
        createdAt = createdAt,
        ended = ended,
        endedAt = endedAt,
    )

// ── Profitability ────────────────────────────────────────────────────────────

data class ProfitTotalsResponse(
    val bookings: Int,
    val guests: Int,
    /** Recognised Paymob (online) revenue, EUR. */
    val onlineRevenue: AmountResponse,
    /** Office payments net of reversals and refunds, EUR. Shown apart from online revenue. */
    val officeRevenue: AmountResponse,
    val revenue: AmountResponse,
    /** Null when any booking's cost is incomplete (see gaps): a missing cost is never shown as profit. */
    val cost: AmountResponse?,
    val profit: AmountResponse?,
    val marginPercent: String?,
    val incompleteBookings: Int,
    val gaps: List<CostGap>,
    val rateSources: List<RateSource>,
)

private fun ProfitTotals.toResponse() =
    ProfitTotalsResponse(
        bookings = bookings,
        guests = guests,
        onlineRevenue = eur(onlineRevenue),
        officeRevenue = eur(officeRevenue),
        revenue = eur(revenue),
        cost = cost?.let(::eur),
        profit = profit?.let(::eur),
        marginPercent = marginPercent?.toPlainString(),
        incompleteBookings = incompleteBookings,
        gaps = gaps.sorted(),
        rateSources = rateSources.sorted(),
    )

data class ProfitGroupResponse(
    val key: String,
    val tourId: UUID?,
    val slotId: UUID?,
    val date: LocalDate?,
    val timeSlot: TimeSlot?,
    val month: String?,
    val bookingId: UUID?,
    val reference: String?,
    val status: BookingStatus?,
    val channel: BookingChannel?,
    /** EGP per EUR used for this departure's EGP costs, and where it came from (booking and departure rows). */
    val egpPerEur: String?,
    val rateSource: RateSource?,
    val totals: ProfitTotalsResponse,
)

data class ProfitReportResponse(
    val from: LocalDate,
    val to: LocalDate,
    val groupBy: ProfitGrouping,
    val totals: ProfitTotalsResponse,
    val groups: List<ProfitGroupResponse>,
)

private fun ProfitGroup.toResponse(grouping: ProfitGrouping): ProfitGroupResponse {
    val first = bookings.first()
    val perDeparture = grouping == ProfitGrouping.BOOKING || grouping == ProfitGrouping.DEPARTURE
    val single = grouping == ProfitGrouping.BOOKING
    return ProfitGroupResponse(
        key = key,
        tourId = if (grouping == ProfitGrouping.MONTH) null else first.booking.tourId.value,
        slotId = if (perDeparture) first.booking.slotId.value else null,
        date = if (perDeparture) first.booking.tourDate else null,
        timeSlot = if (perDeparture) first.booking.timeSlot else null,
        month = if (grouping == ProfitGrouping.MONTH) key else null,
        bookingId = if (single) first.booking.bookingId.value else null,
        reference = if (single) first.booking.reference else null,
        status = if (single) first.booking.status else null,
        channel = if (single) first.booking.channel else null,
        egpPerEur = if (perDeparture) first.rate.egpPerEur?.toPlainString() else null,
        rateSource = if (perDeparture) first.rate.source else null,
        totals = totals.toResponse(),
    )
}

fun ProfitReport.toResponse() = ProfitReportResponse(from, to, grouping, totals.toResponse(), groups.map { it.toResponse(grouping) })

data class OfficeSummaryResponse(
    val from: LocalDate,
    val to: LocalDate,
    val collected: AmountResponse,
    val collectionsReversed: AmountResponse,
    val refunded: AmountResponse,
    val refundsReversed: AmountResponse,
    /** Collected − reversals − refunds (+ refund reversals), EUR: the "office payments" line. */
    val net: AmountResponse,
    val collectionCount: Int,
    val refundCount: Int,
)

fun OfficeFinanceSummary.toResponse() =
    OfficeSummaryResponse(
        from = from,
        to = to,
        collected = eur(money.collectedEur),
        collectionsReversed = eur(money.collectionsReversedEur),
        refunded = eur(money.refundedEur),
        refundsReversed = eur(money.refundsReversedEur),
        net = eur(money.netEur),
        collectionCount = money.collectionCount,
        refundCount = money.refundCount,
    )

// ── Refunds ──────────────────────────────────────────────────────────────────

data class RecordRefundRequest(
    val clientRequestId: UUID,
    val method: RefundMethod,
    @field:DecimalMin("0.01") @field:Digits(integer = 7, fraction = 2)
    val amount: BigDecimal,
    /** EUR when omitted. */
    val currency: PaidCurrency? = null,
    @field:Size(max = 64)
    val reference: String? = null,
    val fxRateId: UUID? = null,
    @field:Size(min = 1, max = 500)
    val reason: String,
)

data class ReverseEntryRequest(
    val clientRequestId: UUID,
    @field:Size(min = 1, max = 500)
    val reason: String,
)

data class OfficeRefundResponse(
    val id: UUID,
    val kind: OfficeRefundKind,
    val method: RefundMethod,
    /** EUR returned against what was collected. */
    val amount: AmountResponse,
    /** What was handed back (EUR or EGP). */
    val amountPaid: AmountResponse,
    val fxRate: String?,
    val reference: String?,
    val reason: String,
    val reversesRefundId: UUID?,
    val recordedByEmail: String?,
    val recordedAt: Instant,
)

fun OfficeRefund.toResponse() =
    OfficeRefundResponse(
        id = id,
        kind = kind,
        method = method,
        amount = eur(amount.amount),
        amountPaid = amount(amountPaid.amount, currencyPaid),
        fxRate = fxRate?.egpPerEur?.toPlainString(),
        reference = reference,
        reason = reason,
        reversesRefundId = reversesRefundId,
        recordedByEmail = recordedByEmail,
        recordedAt = recordedAt,
    )

data class RefundPositionResponse(
    val collected: AmountResponse,
    val refunded: AmountResponse,
    val refundable: AmountResponse,
)

fun RefundPosition.toResponse() = RefundPositionResponse(eur(collected), eur(refunded), eur(refundable))

data class OfficeRefundsResponse(
    val entries: List<OfficeRefundResponse>,
    val position: RefundPositionResponse,
)

data class OfficeRefundOutcomeResponse(
    val entry: OfficeRefundResponse,
    val position: RefundPositionResponse,
    /** TODAY_RATE_USED: part of an EGP refund had no EGP collection to follow and was valued at today's rate. */
    val warning: String? = null,
)

// ── Payables and settlements ────────────────────────────────────────────────

data class CurrencyBalanceResponse(
    val currency: PaidCurrency,
    val opening: AmountResponse,
    val owed: AmountResponse,
    val paid: AmountResponse,
    val closing: AmountResponse,
)

private fun CurrencyBalance.toResponse() =
    CurrencyBalanceResponse(currency, amount(opening, currency), amount(owed, currency), amount(paid, currency), amount(closing, currency))

data class PartySummaryResponse(
    val partyType: PartyType,
    val partyId: UUID,
    val name: String,
    val code: String?,
    val active: Boolean,
    val balances: List<CurrencyBalanceResponse>,
    val openIssues: Int,
    val pendingApprovals: Int,
)

fun PartySummary.toResponse() =
    PartySummaryResponse(
        info.party.type,
        info.party.id,
        info.name,
        info.code,
        info.active,
        balances.map { it.toResponse() },
        openIssues,
        pendingApprovals,
    )

data class PayableAdjustmentResponse(
    val id: UUID,
    val slotId: UUID?,
    val serviceDate: LocalDate,
    val kind: AdjustmentKind,
    val amount: AmountResponse,
    val reason: String,
    val reversesAdjustmentId: UUID?,
    val recordedByEmail: String?,
    val recordedAt: Instant,
)

fun PayableAdjustment.toResponse() =
    PayableAdjustmentResponse(
        id,
        slotId?.value,
        serviceDate,
        kind,
        amount(amount, currency),
        reason,
        reversesAdjustmentId,
        recordedByEmail,
        recordedAt,
    )

data class SettlementPaymentResponse(
    val id: UUID,
    val kind: SettlementPaymentKind,
    val method: SettlementMethod,
    val amount: AmountResponse,
    val egpEquivalent: String?,
    val reference: String?,
    val note: String?,
    val approvalId: UUID?,
    val reversesPaymentId: UUID?,
    val reason: String?,
    val recordedByEmail: String?,
    val recordedAt: Instant,
)

fun SettlementPayment.toResponse() =
    SettlementPaymentResponse(
        id,
        kind,
        method,
        amount(amount, currency),
        egpEquivalent?.toPlainString(),
        reference,
        note,
        approvalId,
        reversesPaymentId,
        reason,
        recordedByEmail,
        recordedAt,
    )

data class SettlementApprovalResponse(
    val id: UUID,
    val amount: AmountResponse,
    val note: String?,
    val approvedByEmail: String?,
    val approvedAt: Instant,
    val usedByPaymentId: UUID?,
)

fun SettlementApproval.toResponse() =
    SettlementApprovalResponse(id, amount(amount, currency), note, approvedByEmail, approvedAt, usedByPaymentId)

data class StatementMovementResponse(
    val date: LocalDate,
    val kind: MovementKind,
    /** Raises (+) or lowers (−) what is owed. */
    val amount: AmountResponse,
    val slotId: UUID?,
    val tourId: UUID?,
    val timeSlot: TimeSlot?,
    val guests: Int?,
    val labels: List<String>,
    val adjustment: PayableAdjustmentResponse?,
    val payment: SettlementPaymentResponse?,
)

data class StatementIssueResponse(
    val date: LocalDate,
    val slotId: UUID,
    val tourId: UUID,
    val timeSlot: TimeSlot,
    val code: OwedIssueCode,
)

data class PartyStatementResponse(
    val party: PartySummaryResponse,
    val from: LocalDate,
    val to: LocalDate,
    val balances: List<CurrencyBalanceResponse>,
    val movements: List<StatementMovementResponse>,
    val issues: List<StatementIssueResponse>,
    val approvals: List<SettlementApprovalResponse>,
    /** The manager limit per party per Cairo day, EGP (owner-delegated default). */
    val managerLimitEgp: String,
    /** Today's non-reversed payments to this party in EGP (counted against the daily limit); null = unknown (needs approval). */
    val paidTodayEgp: String?,
)

fun PartyStatement.toResponse(summary: PartySummaryResponse) =
    PartyStatementResponse(
        party = summary,
        from = checkNotNull(from),
        to = to,
        balances = balances.map { it.toResponse() },
        movements =
            movements.map { m ->
                StatementMovementResponse(
                    date = m.date,
                    kind = m.kind,
                    amount = amount(m.signed, m.currency),
                    slotId = m.owedLine?.slotId?.value ?: m.adjustment?.slotId?.value,
                    tourId = m.owedLine?.tourId?.value,
                    timeSlot = m.owedLine?.timeSlot,
                    guests = m.owedLine?.guests,
                    labels = m.owedLine?.labels.orEmpty(),
                    adjustment = m.adjustment?.toResponse(),
                    payment = m.payment?.toResponse(),
                )
            },
        issues = issues.map { StatementIssueResponse(it.date, it.slotId.value, it.tourId.value, it.timeSlot, it.code) },
        approvals = approvals.map { it.toResponse() },
        managerLimitEgp = SettlementPolicy.MANAGER_LIMIT_EGP.toPlainString(),
        paidTodayEgp = paidTodayEgp?.toPlainString(),
    )

data class PayRequest(
    val clientRequestId: UUID,
    val method: SettlementMethod,
    val currency: PaidCurrency,
    @field:DecimalMin("0.01") @field:Digits(integer = 7, fraction = 2)
    val amount: BigDecimal,
    @field:Size(max = 64)
    val reference: String? = null,
    @field:Size(max = 500)
    val note: String? = null,
    /** Required above the manager limit: an unused owner approval of the same party, currency and amount. */
    val approvalId: UUID? = null,
)

data class ApproveRequest(
    val clientRequestId: UUID,
    val currency: PaidCurrency,
    @field:DecimalMin("0.01") @field:Digits(integer = 7, fraction = 2)
    val amount: BigDecimal,
    @field:Size(max = 500)
    val note: String? = null,
)

data class AdjustRequest(
    val clientRequestId: UUID,
    /** CHARGE or DEDUCTION. */
    val kind: AdjustmentKind,
    val currency: PaidCurrency,
    @field:DecimalMin("0.01") @field:Digits(integer = 7, fraction = 2)
    val amount: BigDecimal,
    /** The departure it belongs to (its day is used); or give serviceDate. */
    val slotId: UUID? = null,
    val serviceDate: LocalDate? = null,
    @field:Size(min = 1, max = 500)
    val reason: String,
)

// ── Cash box ────────────────────────────────────────────────────────────────

data class CashCountRequest(
    @field:DecimalMin("0.00") @field:Digits(integer = 9, fraction = 2)
    val counted: BigDecimal,
    @field:Size(max = 500)
    val note: String? = null,
)

data class CashReopenRequest(
    @field:Size(min = 1, max = 500)
    val reason: String,
)

data class CashBoxEventResponse(
    val id: UUID,
    val date: LocalDate,
    val currency: PaidCurrency,
    val sequence: Int,
    val kind: CashBoxEventKind,
    val expected: AmountResponse?,
    val counted: AmountResponse?,
    val difference: AmountResponse?,
    val note: String?,
    val actorEmail: String?,
    val occurredAt: Instant,
)

fun CashBoxEvent.toResponse() =
    CashBoxEventResponse(
        id,
        businessDate,
        currency,
        sequence,
        kind,
        expected?.let { amount(it, currency) },
        counted?.let { amount(it, currency) },
        difference?.let { amount(it, currency) },
        note,
        actorEmail,
        occurredAt,
    )

data class CashDayResponse(
    val date: LocalDate,
    val currency: PaidCurrency,
    val state: CashDayState,
    val cashCollected: AmountResponse,
    val collectionsReversed: AmountResponse,
    val cashRefunded: AmountResponse,
    val refundsReversed: AmountResponse,
    val cashSettlementsPaid: AmountResponse,
    val settlementsReversed: AmountResponse,
    /** Collections − refunds − settlement payments, each net of reversals (cash only, this currency). */
    val expected: AmountResponse,
    val events: List<CashBoxEventResponse>,
)

fun CashDayView.toResponse() =
    CashDayResponse(
        date = date,
        currency = currency,
        state = state,
        cashCollected = amount(flow.collected, currency),
        collectionsReversed = amount(flow.collectionsReversed, currency),
        cashRefunded = amount(flow.refunded, currency),
        refundsReversed = amount(flow.refundsReversed, currency),
        cashSettlementsPaid = amount(flow.settlementsPaid, currency),
        settlementsReversed = amount(flow.settlementsReversed, currency),
        expected = amount(flow.expected, currency),
        events = events.sortedBy { it.sequence }.map { it.toResponse() },
    )

// ── Settlement statement document ───────────────────────────────────────────

data class PrintStatementRequest(
    val from: LocalDate,
    val to: LocalDate,
    @field:jakarta.validation.constraints.Pattern(regexp = "^(en|ar)$")
    val language: String,
)
