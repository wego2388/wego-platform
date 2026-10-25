package com.wego.toursoperator.application

import com.wego.toursoperator.domain.BookingProfit
import com.wego.toursoperator.domain.ProfitCalculator
import com.wego.toursoperator.domain.ProfitDeparture
import com.wego.toursoperator.domain.ProfitTotals
import com.wego.toursoperator.domain.RateChoice
import com.wego.toursoperator.domain.RateSource
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ProfitGrouping { BOOKING, DEPARTURE, TOUR, MONTH }

data class ProfitGroup(
    /** Booking id, slot id, tour id or yyyy-MM. */
    val key: String,
    val bookings: List<BookingProfit>,
    val totals: ProfitTotals,
)

data class ProfitReport(
    val from: LocalDate,
    val to: LocalDate,
    val grouping: ProfitGrouping,
    val groups: List<ProfitGroup>,
    val totals: ProfitTotals,
)

data class OfficeFinanceSummary(
    val from: LocalDate,
    val to: LocalDate,
    val money: OfficeMoneySummary,
)

/**
 * Read-only profitability (OPS2-F) on the existing finance permission
 * `tours-operator.payment:view`. Bookings are taken by tour day. Revenue in
 * EUR: recognised Paymob payments (online) and office collections minus office
 * refunds (office), shown apart and never mixed. Cost: components × guests at
 * the rate of the departure day; a past day without one uses its nearest earlier
 * rate (never a later one), a future day the latest rate (the report says which); a booking with any unknown cost shows no profit.
 */
class ProfitabilityService(
    private val reads: FinanceReadRepository,
    private val costs: CostComponentRepository,
    private val settlements: SettlementRepository,
    private val clock: Clock,
) {
    fun report(
        from: LocalDate,
        to: LocalDate,
        grouping: ProfitGrouping,
    ): ProfitReport {
        val bookings = reads.profitBookings(from, to)
        val slotIds = bookings.map { it.slotId }.toSet()
        val drivers = reads.drivers(slotIds)
        val suppliers = reads.suppliers(slotIds)
        val adjustments = settlements.adjustmentsForSlots(slotIds).groupBy { it.slotId }
        // Reversals carry no slot (dated the day they are recorded): attach them to the departure of what they reverse.
        val reversals =
            settlements.adjustments(null).filter { it.reversesAdjustmentId != null }
        val byId = adjustments.values.flatten().associateBy { it.id }
        val departures =
            slotIds.associateWith { slotId ->
                val own = adjustments[slotId].orEmpty()
                val reversing = reversals.filter { byId[it.reversesAdjustmentId]?.slotId == slotId }
                ProfitDeparture(slotId, drivers[slotId], own + reversing, suppliers[slotId].orEmpty())
            }
        val today = FxRateService.todayInSharm(clock)
        val rates = java.util.TreeMap(reads.ratesUpTo(minOf(to, today)))
        val latest = reads.latestRate()
        val rateFor: (LocalDate) -> RateChoice = { day ->
            val own = rates[day]
            val previous = rates.floorEntry(day)?.value
            when {
                own != null -> RateChoice(own, RateSource.DEPARTURE_DATE)
                // A day that has begun is valued at its nearest earlier rate (never a later one), so it cannot drift.
                !day.isAfter(today) -> RateChoice(previous, if (previous != null) RateSource.PREVIOUS else RateSource.NONE)
                latest != null -> RateChoice(latest, RateSource.LATEST)
                else -> RateChoice(null, RateSource.NONE)
            }
        }
        val results = ProfitCalculator.compute(bookings, departures, costs.findAll(), rateFor)
        val keyOf: (BookingProfit) -> String =
            when (grouping) {
                ProfitGrouping.BOOKING -> { r ->
                    r.booking.bookingId.value
                        .toString()
                }
                ProfitGrouping.DEPARTURE -> { r ->
                    r.booking.slotId.value
                        .toString()
                }
                ProfitGrouping.TOUR -> { r ->
                    r.booking.tourId.value
                        .toString()
                }
                ProfitGrouping.MONTH -> { r ->
                    r.booking.tourDate
                        .toString()
                        .take(7)
                }
            }
        val groups =
            results
                .groupBy(keyOf)
                .map { (key, rows) ->
                    val sorted = rows.sortedWith(compareBy({ it.booking.tourDate }, { it.booking.timeSlot }, { it.booking.reference }))
                    ProfitGroup(key, sorted, ProfitTotals.of(sorted))
                }.sortedWith(
                    compareBy({
                        it.bookings
                            .first()
                            .booking.tourDate
                    }, {
                        it.bookings
                            .first()
                            .booking.timeSlot
                    }, { it.key }),
                )
        return ProfitReport(from, to, grouping, groups, ProfitTotals.of(results))
    }

    fun officeSummary(
        from: LocalDate,
        to: LocalDate,
    ): OfficeFinanceSummary {
        val start: Instant = from.atStartOfDay(CAIRO).toInstant()
        val end: Instant = to.plusDays(1).atStartOfDay(CAIRO).toInstant()
        return OfficeFinanceSummary(from, to, reads.officeMoney(start, end))
    }

    companion object {
        private val CAIRO: ZoneId = ZoneId.of("Africa/Cairo")
        const val MAX_RANGE_DAYS = 366L
    }
}
