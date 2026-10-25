package com.wego.toursoperator.application

import com.wego.toursoperator.domain.CashBoxEvent
import com.wego.toursoperator.domain.CashBoxEventKind
import com.wego.toursoperator.domain.CashDayState
import com.wego.toursoperator.domain.OnlineRequestStatus
import com.wego.toursoperator.domain.PaidCurrency
import com.wego.toursoperator.domain.PartyType
import com.wego.toursoperator.domain.ProfitBooking
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

/*
 * WEGO-016 SAF-04 Slice B: read-only report indicators. Nothing here writes, locks or
 * migrates. Every report states its basis (the day it is counted on) and keeps EUR and
 * EGP apart; a customer name, phone, email or note never enters a report.
 */

enum class ReversalKind { COLLECTION, OFFICE_REFUND, SETTLEMENT_PAYMENT, ADJUSTMENT }

/** One reversal row as read from a ledger, joined to the entry it reverses. [amount] is in [currency] (native); [amountEur] only where the ledger stores it. */
data class ReversalEntry(
    val kind: ReversalKind,
    val id: UUID,
    val reversedAt: Instant,
    val reversedBy: String?,
    val reason: String,
    val originalAt: Instant,
    val originalBy: String?,
    val currency: PaidCurrency,
    val amount: BigDecimal,
    val amountEur: BigDecimal?,
    val cash: Boolean,
    val bookingReference: String?,
    val partyType: PartyType?,
    val partyName: String?,
)

data class ReversalLine(
    val entry: ReversalEntry,
    val reversedOn: LocalDate,
    val originalOn: LocalDate,
    val lagDays: Long,
    /** A cash entry whose original Cairo day was already closed when it was reversed: the reversal moved a later day's cash box. */
    val crossedClosedDay: Boolean,
)

data class ReversalTotal(
    val kind: ReversalKind,
    val currency: PaidCurrency,
    val count: Int,
    val amount: BigDecimal,
)

data class ReversalReport(
    val from: LocalDate,
    val to: LocalDate,
    val lines: List<ReversalLine>,
    val totals: List<ReversalTotal>,
)

data class CashVarianceDay(
    val date: LocalDate,
    val currency: PaidCurrency,
    val state: CashDayState,
    val expected: BigDecimal,
    val counted: BigDecimal,
    val difference: BigDecimal,
    val countedBy: String?,
    val countedAt: Instant,
    /** Who confirmed the latest count; null while the day is not closed. */
    val confirmedBy: String?,
    val confirmedAt: Instant?,
    val countEvents: Int,
    val reopenCount: Int,
)

data class CashVarianceTotal(
    val currency: PaidCurrency,
    val closedDays: Int,
    val daysWithVariance: Int,
    val difference: BigDecimal,
)

data class CashVarianceReport(
    val from: LocalDate,
    val to: LocalDate,
    val days: List<CashVarianceDay>,
    val totals: List<CashVarianceTotal>,
)

/** Status counts of the website requests created in the range. No customer data. */
data class RequestConversionReport(
    val from: LocalDate,
    val to: LocalDate,
    val total: Int,
    val new: Int,
    val inProgress: Int,
    val closed: Int,
    val converted: Int,
    /** converted ÷ total, one decimal; null when there are no requests. */
    val conversionPercent: BigDecimal?,
)

/** Office entries of a booking in one currency, net of reversals and refunds: [native] as handed over, [eur] as settled against the booking. */
data class NativeNet(
    val native: BigDecimal,
    val eur: BigDecimal,
)

data class ReceivedAmount(
    val currency: PaidCurrency,
    val native: BigDecimal,
    /** What this money settled against bookings, in EUR (the booking currency). Equal to [native] for EUR. */
    val eurValue: BigDecimal,
)

data class DepartureDayMoney(
    val date: LocalDate,
    val liveBookings: Int,
    val cancelledBookings: Int,
    val bookedEur: BigDecimal,
    val collectedEur: BigDecimal,
    val outstandingEur: BigDecimal,
    /** Money kept on cancelled bookings of the day (net of refunds), apart from the live figures. */
    val retainedOnCancelledEur: BigDecimal,
    val received: List<ReceivedAmount>,
    val paymentsUnderReview: Int,
    val refundsPending: Int,
)

data class DepartureMoneyReport(
    val from: LocalDate,
    val to: LocalDate,
    val days: List<DepartureDayMoney>,
    val totals: DepartureDayMoney?,
)

class FinanceReportService(
    private val reads: FinanceReadRepository,
    private val requests: OnlineRequestRepository,
) {
    fun reversals(
        from: LocalDate,
        to: LocalDate,
    ): ReversalReport {
        val entries = reads.reversals(startOf(from), startOf(to.plusDays(1))).sortedByDescending { it.reversedAt }
        val events =
            if (entries.isEmpty()) {
                emptyList()
            } else {
                reads.cashEvents(entries.minOf { it.originalAt.atZone(CAIRO).toLocalDate() }, to)
            }
        val lines =
            entries.map { e ->
                val reversedOn = e.reversedAt.atZone(CAIRO).toLocalDate()
                val originalOn = e.originalAt.atZone(CAIRO).toLocalDate()
                val closed = e.cash && originalOn != reversedOn && closedWhen(events, originalOn, e.currency, e.reversedAt)
                ReversalLine(e, reversedOn, originalOn, ChronoUnit.DAYS.between(originalOn, reversedOn), closed)
            }
        val totals =
            lines
                .groupBy { it.entry.kind to it.entry.currency }
                .map { (key, rows) -> ReversalTotal(key.first, key.second, rows.size, rows.fold(ZERO) { a, r -> a.add(r.entry.amount) }) }
                .sortedWith(compareBy({ it.kind }, { it.currency }))
        return ReversalReport(from, to, lines, totals)
    }

    private fun closedWhen(
        events: List<CashBoxEvent>,
        day: LocalDate,
        currency: PaidCurrency,
        at: Instant,
    ): Boolean =
        events
            .filter { it.businessDate == day && it.currency == currency && !it.occurredAt.isAfter(at) }
            .maxByOrNull { it.sequence }
            ?.kind == CashBoxEventKind.CONFIRM

    fun cashVariances(
        from: LocalDate,
        to: LocalDate,
    ): CashVarianceReport {
        val days =
            reads
                .cashEvents(from, to)
                .groupBy { it.businessDate to it.currency }
                .mapNotNull { (key, events) ->
                    val ordered = events.sortedBy { it.sequence }
                    val count = ordered.lastOrNull { it.kind == CashBoxEventKind.COUNT } ?: return@mapNotNull null
                    val confirm = ordered.lastOrNull { it.kind == CashBoxEventKind.CONFIRM && it.countEventId == count.id }
                    val state = CashBoxEvent.state(ordered)
                    CashVarianceDay(
                        date = key.first,
                        currency = key.second,
                        state = state,
                        expected = checkNotNull(count.expected),
                        counted = checkNotNull(count.counted),
                        difference = checkNotNull(count.difference),
                        countedBy = count.actorEmail,
                        countedAt = count.occurredAt,
                        confirmedBy = if (state == CashDayState.CLOSED) confirm?.actorEmail else null,
                        confirmedAt = if (state == CashDayState.CLOSED) confirm?.occurredAt else null,
                        countEvents = ordered.count { it.kind == CashBoxEventKind.COUNT },
                        reopenCount = ordered.count { it.kind == CashBoxEventKind.REOPEN },
                    )
                }.sortedWith(compareByDescending<CashVarianceDay> { it.date }.thenBy { it.currency })
        val totals =
            days
                .filter { it.state == CashDayState.CLOSED }
                .groupBy { it.currency }
                .map { (currency, rows) ->
                    CashVarianceTotal(
                        currency,
                        rows.size,
                        rows.count { it.difference.signum() != 0 },
                        rows.fold(ZERO) { a, r -> a.add(r.difference) },
                    )
                }.sortedBy { it.currency }
        return CashVarianceReport(from, to, days, totals)
    }

    fun requestConversion(
        from: LocalDate,
        to: LocalDate,
    ): RequestConversionReport {
        val counts = requests.statusCounts(startOf(from), startOf(to.plusDays(1)))

        fun of(status: OnlineRequestStatus) = counts[status] ?: 0
        val total = counts.values.sum()
        val converted = of(OnlineRequestStatus.CONVERTED)
        val percent =
            if (total == 0) {
                null
            } else {
                BigDecimal(converted).multiply(BigDecimal(100)).divide(BigDecimal(total), 1, RoundingMode.HALF_UP)
            }
        return RequestConversionReport(
            from,
            to,
            total,
            of(OnlineRequestStatus.NEW),
            of(OnlineRequestStatus.IN_PROGRESS),
            of(OnlineRequestStatus.CLOSED),
            converted,
            percent,
        )
    }

    fun departureMoney(
        from: LocalDate,
        to: LocalDate,
    ): DepartureMoneyReport {
        val bookings = reads.profitBookings(from, to)
        val native = reads.officeNetByCurrency(bookings.map { it.bookingId })
        val days =
            bookings
                .groupBy { it.tourDate }
                .map { (date, rows) -> day(date, rows, native) }
                .sortedBy { it.date }
        return DepartureMoneyReport(from, to, days, if (days.isEmpty()) null else total(days))
    }

    private fun day(
        date: LocalDate,
        rows: List<ProfitBooking>,
        native: Map<com.wego.toursoperator.domain.BookingId, Map<PaidCurrency, NativeNet>>,
    ): DepartureDayMoney {
        val live = rows.filter { it.live }
        val cancelled = rows.filterNot { it.live }

        fun revenue(b: ProfitBooking) = b.onlineRevenue.add(b.officeRevenue)
        val booked = live.fold(ZERO) { a, b -> a.add(b.pricing.totalEur.amount) }
        val collected = live.fold(ZERO) { a, b -> a.add(revenue(b)) }
        val outstanding =
            live.fold(ZERO) { a, b ->
                a.add(
                    b.pricing.totalEur.amount
                        .subtract(revenue(b))
                        .max(ZERO),
                )
            }
        val retained = cancelled.fold(ZERO) { a, b -> a.add(revenue(b)) }
        val eurNative = live.fold(ZERO) { a, b -> a.add(b.onlineRevenue).add(native[b.bookingId]?.get(PaidCurrency.EUR)?.native ?: ZERO) }
        val egp = live.mapNotNull { native[it.bookingId]?.get(PaidCurrency.EGP) }
        return DepartureDayMoney(
            date = date,
            liveBookings = live.size,
            cancelledBookings = cancelled.size,
            bookedEur = booked.setScale(2),
            collectedEur = collected.setScale(2),
            outstandingEur = outstanding.setScale(2),
            retainedOnCancelledEur = retained.setScale(2),
            received =
                listOf(
                    ReceivedAmount(PaidCurrency.EUR, eurNative.setScale(2), eurNative.setScale(2)),
                    ReceivedAmount(PaidCurrency.EGP, egp.fold(ZERO) { a, n -> a.add(n.native) }, egp.fold(ZERO) { a, n -> a.add(n.eur) }),
                ),
            paymentsUnderReview = rows.count { it.paymentUnderReview },
            refundsPending = rows.count { it.refundPending },
        )
    }

    private fun total(days: List<DepartureDayMoney>): DepartureDayMoney {
        fun received(currency: PaidCurrency): ReceivedAmount {
            val all = days.flatMap { it.received }.filter { it.currency == currency }
            return ReceivedAmount(currency, all.fold(ZERO) { a, r -> a.add(r.native) }, all.fold(ZERO) { a, r -> a.add(r.eurValue) })
        }
        return DepartureDayMoney(
            date = days.first().date,
            liveBookings = days.sumOf { it.liveBookings },
            cancelledBookings = days.sumOf { it.cancelledBookings },
            bookedEur = days.fold(ZERO) { a, d -> a.add(d.bookedEur) },
            collectedEur = days.fold(ZERO) { a, d -> a.add(d.collectedEur) },
            outstandingEur = days.fold(ZERO) { a, d -> a.add(d.outstandingEur) },
            retainedOnCancelledEur = days.fold(ZERO) { a, d -> a.add(d.retainedOnCancelledEur) },
            received = listOf(received(PaidCurrency.EUR), received(PaidCurrency.EGP)),
            paymentsUnderReview = days.sumOf { it.paymentsUnderReview },
            refundsPending = days.sumOf { it.refundsPending },
        )
    }

    private fun startOf(day: LocalDate): Instant = day.atStartOfDay(CAIRO).toInstant()

    companion object {
        private val CAIRO: ZoneId = ZoneId.of("Africa/Cairo")
        private val ZERO: BigDecimal = BigDecimal.ZERO.setScale(2)
        const val MAX_RANGE_DAYS = ProfitabilityService.MAX_RANGE_DAYS
    }
}
