package com.wego.toursoperator.api

import com.wego.toursoperator.application.CashVarianceReport
import com.wego.toursoperator.application.DepartureDayMoney
import com.wego.toursoperator.application.DepartureMoneyReport
import com.wego.toursoperator.application.RequestConversionReport
import com.wego.toursoperator.application.ReversalKind
import com.wego.toursoperator.application.ReversalReport
import com.wego.toursoperator.domain.CashDayState
import com.wego.toursoperator.domain.PaidCurrency
import com.wego.toursoperator.domain.PartyType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/*
 * SAF-04 Slice B report responses. No customer name, phone, email or note appears in any of
 * them; booking references and staff account emails (as everywhere in the finance pages) do.
 */

private fun money(
    value: BigDecimal,
    currency: PaidCurrency,
) = AmountResponse(value.setScale(2).toPlainString(), currency.name)

data class ReversalLineResponse(
    val kind: ReversalKind,
    val id: UUID,
    val reversedAt: Instant,
    val reversedOn: LocalDate,
    val reversedBy: String?,
    val reason: String,
    val originalAt: Instant,
    val originalOn: LocalDate,
    val originalBy: String?,
    val lagDays: Long,
    /** In the currency the money moved in. */
    val amount: AmountResponse,
    /** EUR settled against the booking; null for supplier and driver ledgers (they are never converted here). */
    val amountEur: AmountResponse?,
    val cash: Boolean,
    val crossedClosedDay: Boolean,
    val bookingReference: String?,
    val partyType: PartyType?,
    val partyName: String?,
)

data class ReversalTotalResponse(
    val kind: ReversalKind,
    val currency: PaidCurrency,
    val count: Int,
    val amount: AmountResponse,
)

data class ReversalReportResponse(
    val from: LocalDate,
    val to: LocalDate,
    val basis: String,
    val lines: List<ReversalLineResponse>,
    val totals: List<ReversalTotalResponse>,
)

fun ReversalReport.toResponse() =
    ReversalReportResponse(
        from,
        to,
        "RECORDED_DAY",
        lines.map {
            val e = it.entry
            ReversalLineResponse(
                e.kind,
                e.id,
                e.reversedAt,
                it.reversedOn,
                e.reversedBy,
                e.reason,
                e.originalAt,
                it.originalOn,
                e.originalBy,
                it.lagDays,
                money(e.amount, e.currency),
                e.amountEur?.let { eur -> money(eur, PaidCurrency.EUR) },
                e.cash,
                it.crossedClosedDay,
                e.bookingReference,
                e.partyType,
                e.partyName,
            )
        },
        totals.map { ReversalTotalResponse(it.kind, it.currency, it.count, money(it.amount, it.currency)) },
    )

data class CashVarianceDayResponse(
    val date: LocalDate,
    val currency: PaidCurrency,
    val state: CashDayState,
    val expected: AmountResponse,
    val counted: AmountResponse,
    val difference: AmountResponse,
    val countedBy: String?,
    val countedAt: Instant,
    val confirmedBy: String?,
    val confirmedAt: Instant?,
    val countEvents: Int,
    val reopenCount: Int,
)

data class CashVarianceTotalResponse(
    val currency: PaidCurrency,
    val closedDays: Int,
    val daysWithVariance: Int,
    val difference: AmountResponse,
)

data class CashVarianceReportResponse(
    val from: LocalDate,
    val to: LocalDate,
    val basis: String,
    val days: List<CashVarianceDayResponse>,
    val totals: List<CashVarianceTotalResponse>,
)

fun CashVarianceReport.toResponse() =
    CashVarianceReportResponse(
        from,
        to,
        "BUSINESS_DAY",
        days.map {
            CashVarianceDayResponse(
                it.date,
                it.currency,
                it.state,
                money(it.expected, it.currency),
                money(it.counted, it.currency),
                money(it.difference, it.currency),
                it.countedBy,
                it.countedAt,
                it.confirmedBy,
                it.confirmedAt,
                it.countEvents,
                it.reopenCount,
            )
        },
        totals.map { CashVarianceTotalResponse(it.currency, it.closedDays, it.daysWithVariance, money(it.difference, it.currency)) },
    )

data class RequestConversionResponse(
    val from: LocalDate,
    val to: LocalDate,
    val basis: String,
    val total: Int,
    val new: Int,
    val inProgress: Int,
    val closed: Int,
    val converted: Int,
    /** converted ÷ total with one decimal, as text; null when no request was created in the range. */
    val conversionPercent: String?,
)

fun RequestConversionReport.toResponse() =
    RequestConversionResponse(from, to, "CREATED_DAY", total, new, inProgress, closed, converted, conversionPercent?.toPlainString())

data class ReceivedAmountResponse(
    val currency: PaidCurrency,
    val amount: AmountResponse,
    /** What this money settled against bookings, in EUR (the booking currency). */
    val settledEur: AmountResponse,
)

data class DepartureDayMoneyResponse(
    val date: LocalDate,
    val liveBookings: Int,
    val cancelledBookings: Int,
    val booked: AmountResponse,
    val collected: AmountResponse,
    val outstanding: AmountResponse,
    val retainedOnCancelled: AmountResponse,
    val received: List<ReceivedAmountResponse>,
    val paymentsUnderReview: Int,
    val refundsPending: Int,
)

data class DepartureMoneyResponse(
    val from: LocalDate,
    val to: LocalDate,
    val basis: String,
    val days: List<DepartureDayMoneyResponse>,
    val totals: DepartureDayMoneyResponse?,
)

private fun DepartureDayMoney.toResponse() =
    DepartureDayMoneyResponse(
        date,
        liveBookings,
        cancelledBookings,
        money(bookedEur, PaidCurrency.EUR),
        money(collectedEur, PaidCurrency.EUR),
        money(outstandingEur, PaidCurrency.EUR),
        money(retainedOnCancelledEur, PaidCurrency.EUR),
        received.map { ReceivedAmountResponse(it.currency, money(it.native, it.currency), money(it.eurValue, PaidCurrency.EUR)) },
        paymentsUnderReview,
        refundsPending,
    )

fun DepartureMoneyReport.toResponse() = DepartureMoneyResponse(from, to, "TOUR_DAY", days.map { it.toResponse() }, totals?.toResponse())
