package com.wego.toursoperator.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.UUID

/** Which EUR→EGP rate a report used for a departure. */
enum class RateSource {
    /** The manager-set rate of the departure day. */
    DEPARTURE_DATE,

    /** No rate that day: the most recent rate set before or after it. */
    LATEST,

    /** No rate at all: EGP costs cannot be converted, so the profit is not shown. */
    NONE,
}

data class RateChoice(
    val egpPerEur: BigDecimal?,
    val source: RateSource,
)

/** Why a cost is not known well enough to show a profit (a missing cost is never shown as profit). */
enum class CostGap {
    /** The tour has no cost component in force on the departure day. */
    NO_COST_COMPONENTS,

    /** A per-unit cost on a booking that bought no units. */
    UNIT_COST_ON_PER_PERSON_BOOKING,

    /** A driver is assigned but has no trip rate and no manual charge for this departure. */
    DRIVER_COST_MISSING,

    /** EGP costs and no EUR→EGP rate at all. */
    FX_RATE_MISSING,
}

/** One booking as the profitability report sees it. Revenue is EUR; office revenue is collected minus refunded. */
data class ProfitBooking(
    val bookingId: BookingId,
    val reference: String,
    val tourId: TourId,
    val slotId: TourSlotId,
    val tourDate: LocalDate,
    val timeSlot: TimeSlot,
    val status: BookingStatus,
    val channel: BookingChannel,
    val pricing: BookingPricing,
    /** Recognised Paymob revenue (PAID with a recognition time); zero otherwise. */
    val onlineRevenue: BigDecimal,
    /** Office collections net of reversals, minus office refunds net of reversals. */
    val officeRevenue: BigDecimal,
) {
    /** A booking that actually ran (or will): it carries cost. A cancelled one keeps only the money it retained. */
    val live: Boolean get() = status == BookingStatus.CONFIRMED || status == BookingStatus.COMPLETED
}

data class ProfitDeparture(
    val slotId: TourSlotId,
    val driverId: UUID?,
    /** Payable adjustments recorded against this departure (reversals included). */
    val adjustments: List<PayableAdjustment>,
)

data class BookingProfit(
    val booking: ProfitBooking,
    val revenue: BigDecimal,
    val cost: BigDecimal?,
    val profit: BigDecimal?,
    val marginPercent: BigDecimal?,
    val gaps: Set<CostGap>,
    val rate: RateChoice,
)

object ProfitCalculator {
    private val ZERO: BigDecimal = BigDecimal.ZERO.setScale(2)

    /**
     * Costs per booking: per-person and per-unit components of the tour in force
     * on the tour day; per-departure components, the driver's trip rate and the
     * departure's payable adjustments are shared by the departure's live bookings
     * in proportion to guests (the last booking takes the rounding remainder so a
     * departure always adds up exactly). EGP converts at [rateFor] of the day.
     */
    fun compute(
        bookings: List<ProfitBooking>,
        departures: Map<TourSlotId, ProfitDeparture>,
        components: List<CostComponent>,
        rateFor: (LocalDate) -> RateChoice,
    ): List<BookingProfit> =
        bookings.groupBy { it.slotId }.flatMap { (slotId, group) ->
            departure(group, departures[slotId], components, rateFor(group.first().tourDate))
        }

    private fun departure(
        group: List<ProfitBooking>,
        departure: ProfitDeparture?,
        components: List<CostComponent>,
        rate: RateChoice,
    ): List<BookingProfit> {
        val live = group.filter { it.live }.sortedBy { it.reference }
        val retained = group.filterNot { it.live }.map { profit(it, ZERO, emptySet(), rate) }
        if (live.isEmpty()) return retained

        val first = live.first()
        val tourComponents = components.filter { it.tourId == first.tourId && it.effectiveOn(first.tourDate) }
        val gaps = mutableSetOf<CostGap>()
        if (tourComponents.isEmpty()) gaps += CostGap.NO_COST_COMPONENTS

        val departureNative = mutableMapOf<PaidCurrency, BigDecimal>()

        fun add(
            map: MutableMap<PaidCurrency, BigDecimal>,
            currency: PaidCurrency,
            amount: BigDecimal,
        ) {
            map[currency] = (map[currency] ?: ZERO).add(amount)
        }
        tourComponents.filter { it.basis == CostBasis.PER_DEPARTURE }.forEach { add(departureNative, it.currency, it.amount) }

        val adjustments = departure?.adjustments.orEmpty()
        val reversed = adjustments.mapNotNull { it.reversesAdjustmentId }.toSet()
        val effectiveAdjustments = adjustments.filter { it.kind != AdjustmentKind.REVERSAL && it.id !in reversed }
        effectiveAdjustments.forEach {
            add(departureNative, it.currency, if (it.kind == AdjustmentKind.CHARGE) it.amount else it.amount.negate())
        }
        departure?.driverId?.let { driverId ->
            val rates = components.filter { it.driverId == driverId && it.effectiveOn(first.tourDate) }
            rates.forEach { add(departureNative, it.currency, it.amount) }
            val manual =
                effectiveAdjustments.any {
                    it.kind == AdjustmentKind.CHARGE && it.party.type == PartyType.DRIVER && it.party.id == driverId
                }
            if (rates.isEmpty() && !manual) gaps += CostGap.DRIVER_COST_MISSING
        }

        val bookingNative =
            live.associate { booking ->
                val costs = mutableMapOf<PaidCurrency, BigDecimal>()
                var unitGap = false
                tourComponents.filter { it.basis != CostBasis.PER_DEPARTURE }.forEach { component ->
                    val cost = component.bookingCost(booking.pricing)
                    if (cost == null) unitGap = true else add(costs, component.currency, cost)
                }
                booking.bookingId to (costs to unitGap)
            }

        val anyEgp =
            (departureNative[PaidCurrency.EGP]?.signum() ?: 0) != 0 ||
                bookingNative.values.any { (it.first[PaidCurrency.EGP]?.signum() ?: 0) != 0 }
        if (anyEgp && rate.egpPerEur == null) gaps += CostGap.FX_RATE_MISSING

        fun eur(native: Map<PaidCurrency, BigDecimal>): BigDecimal =
            native.entries.fold(ZERO) { acc, (currency, amount) ->
                acc.add(FinanceAmount.toEur(amount, currency, rate.egpPerEur) ?: ZERO)
            }

        val departureEur = eur(departureNative)
        val totalGuests = live.sumOf { it.pricing.guests }
        var allocated = ZERO
        val results =
            live.mapIndexed { index, booking ->
                val share =
                    if (index == live.lastIndex) {
                        departureEur.subtract(allocated)
                    } else {
                        departureEur
                            .multiply(BigDecimal(booking.pricing.guests))
                            .divide(BigDecimal(totalGuests), 2, RoundingMode.HALF_UP)
                    }
                allocated = allocated.add(share)
                val (native, unitGap) = checkNotNull(bookingNative[booking.bookingId])
                val bookingGaps = if (unitGap) gaps + CostGap.UNIT_COST_ON_PER_PERSON_BOOKING else gaps.toSet()
                profit(booking, eur(native).add(share), bookingGaps, rate)
            }
        return results + retained
    }

    private fun profit(
        booking: ProfitBooking,
        cost: BigDecimal,
        gaps: Set<CostGap>,
        rate: RateChoice,
    ): BookingProfit {
        val revenue = booking.onlineRevenue.add(booking.officeRevenue).setScale(2)
        if (gaps.isNotEmpty()) return BookingProfit(booking, revenue, null, null, null, gaps, rate)
        val profit = revenue.subtract(cost)
        return BookingProfit(booking, revenue, cost, profit, margin(profit, revenue), gaps, rate)
    }

    fun margin(
        profit: BigDecimal,
        revenue: BigDecimal,
    ): BigDecimal? = if (revenue.signum() == 0) null else profit.multiply(BigDecimal(100)).divide(revenue, 1, RoundingMode.HALF_UP)
}

/** An aggregated line (departure, tour or month). Cost and profit are null as soon as one booking's cost is incomplete. */
data class ProfitTotals(
    val bookings: Int,
    val guests: Int,
    val onlineRevenue: BigDecimal,
    val officeRevenue: BigDecimal,
    val revenue: BigDecimal,
    val cost: BigDecimal?,
    val profit: BigDecimal?,
    val marginPercent: BigDecimal?,
    val incompleteBookings: Int,
    val gaps: Set<CostGap>,
    val rateSources: Set<RateSource>,
) {
    companion object {
        fun of(rows: List<BookingProfit>): ProfitTotals {
            val zero = BigDecimal.ZERO.setScale(2)
            val revenue = rows.fold(zero) { acc, r -> acc.add(r.revenue) }
            val incomplete = rows.count { it.cost == null }
            val cost = if (incomplete == 0) rows.fold(zero) { acc, r -> acc.add(checkNotNull(r.cost)) } else null
            val profit = cost?.let { revenue.subtract(it) }
            return ProfitTotals(
                bookings = rows.size,
                guests = rows.filter { it.booking.live }.sumOf { it.booking.pricing.guests },
                onlineRevenue = rows.fold(zero) { acc, r -> acc.add(r.booking.onlineRevenue) },
                officeRevenue = rows.fold(zero) { acc, r -> acc.add(r.booking.officeRevenue) },
                revenue = revenue,
                cost = cost,
                profit = profit,
                marginPercent = profit?.let { ProfitCalculator.margin(it, revenue) },
                incompleteBookings = incomplete,
                gaps = rows.flatMap { it.gaps }.toSet(),
                rateSources = rows.filter { it.booking.live }.map { it.rate.source }.toSet(),
            )
        }
    }
}
