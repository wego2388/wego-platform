package com.wego.toursoperator.domain

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** A departure that ran (or runs today) as the payables see it: who served it and the live guests. */
data class PayableDeparture(
    val slotId: TourSlotId,
    val tourId: TourId,
    val date: LocalDate,
    val timeSlot: TimeSlot,
    val driverId: UUID?,
    val supplierIds: Set<UUID>,
    /** Pricing of the confirmed/completed bookings only. */
    val live: List<BookingPricing>,
)

/** What a departure makes us owe one supplier or driver, in one currency. */
data class OwedLine(
    val slotId: TourSlotId,
    val tourId: TourId,
    val date: LocalDate,
    val timeSlot: TimeSlot,
    val guests: Int,
    val currency: PaidCurrency,
    val amount: BigDecimal,
    val labels: List<String>,
)

enum class OwedIssueCode {
    /** A driver with no trip rate and no manual charge for the departure: enter the amount by hand. */
    MANUAL_AMOUNT_NEEDED,

    /** A supplier price not linked to a supplier, on a departure served by several suppliers: split it by hand. */
    SHARED_SUPPLIER_COST,

    /** The supplier serves the departure but no supplier price is in force for the tour. */
    NO_SUPPLIER_PRICE,

    /** A per-unit supplier price on a booking that bought no units. */
    UNIT_COST_ON_PER_PERSON_BOOKING,
}

data class OwedIssue(
    val slotId: TourSlotId,
    val tourId: TourId,
    val date: LocalDate,
    val timeSlot: TimeSlot,
    val code: OwedIssueCode,
)

enum class MovementKind { DEPARTURE, CHARGE, DEDUCTION, CHARGE_REVERSED, DEDUCTION_REVERSED, PAYMENT, PAYMENT_REVERSED }

/** One line of a statement. [signed] raises (+) or lowers (−) what we owe the party. */
data class PayableMovement(
    val date: LocalDate,
    val kind: MovementKind,
    val currency: PaidCurrency,
    val signed: BigDecimal,
    val owedLine: OwedLine? = null,
    val adjustment: PayableAdjustment? = null,
    val payment: SettlementPayment? = null,
    val recordedAt: Instant? = null,
)

data class CurrencyBalance(
    val currency: PaidCurrency,
    val opening: BigDecimal,
    /** Owed during the period: departures + charges − deductions (and their reversals). */
    val owed: BigDecimal,
    /** Paid during the period, net of payment reversals. */
    val paid: BigDecimal,
    val closing: BigDecimal,
)

object PayablesCalculator {
    private val ZERO: BigDecimal = BigDecimal.ZERO.setScale(2)

    /**
     * Amounts derived from departures for [party]. Supplier: its SUPPLIER cost
     * components in force on the day (linked to it, or unlinked when it is the
     * only supplier of the departure). Driver: its trip rate in force on the day.
     */
    fun derive(
        party: PartyRef,
        departures: List<PayableDeparture>,
        components: List<CostComponent>,
        adjustments: List<PayableAdjustment>,
    ): Pair<List<OwedLine>, List<OwedIssue>> {
        val lines = mutableListOf<OwedLine>()
        val issues = mutableListOf<OwedIssue>()
        val reversed = adjustments.mapNotNull { it.reversesAdjustmentId }.toSet()
        for (dep in departures.sortedWith(compareBy({ it.date }, { it.timeSlot }))) {
            if (dep.live.isEmpty()) continue

            fun issue(code: OwedIssueCode) {
                issues += OwedIssue(dep.slotId, dep.tourId, dep.date, dep.timeSlot, code)
            }
            val guests = dep.live.sumOf { it.guests }
            when (party.type) {
                PartyType.SUPPLIER -> {
                    if (party.id !in dep.supplierIds) continue
                    val inForce =
                        components.filter {
                            it.category == CostCategory.SUPPLIER && it.tourId == dep.tourId && it.effectiveOn(dep.date)
                        }
                    val linked = inForce.filter { it.supplierId == party.id }
                    val unlinked = inForce.filter { it.supplierId == null }
                    val applicable =
                        linked +
                            if (unlinked.isNotEmpty() && dep.supplierIds.size > 1) {
                                issue(OwedIssueCode.SHARED_SUPPLIER_COST)
                                emptyList()
                            } else {
                                unlinked
                            }
                    if (inForce.none { it.supplierId == party.id || it.supplierId == null }) issue(OwedIssueCode.NO_SUPPLIER_PRICE)
                    var unitGap = false
                    applicable.groupBy { it.currency }.forEach { (currency, comps) ->
                        var amount = ZERO
                        comps.forEach { component ->
                            if (component.basis == CostBasis.PER_DEPARTURE) {
                                amount = amount.add(component.amount)
                            } else {
                                dep.live.forEach { pricing ->
                                    val cost = component.bookingCost(pricing)
                                    if (cost == null) unitGap = true else amount = amount.add(cost)
                                }
                            }
                        }
                        if (amount.signum() > 0) {
                            lines +=
                                OwedLine(dep.slotId, dep.tourId, dep.date, dep.timeSlot, guests, currency, amount, comps.map { it.label })
                        }
                    }
                    if (unitGap) issue(OwedIssueCode.UNIT_COST_ON_PER_PERSON_BOOKING)
                }
                PartyType.DRIVER -> {
                    if (dep.driverId != party.id) continue
                    val rates = components.filter { it.driverId == party.id && it.effectiveOn(dep.date) }
                    rates.groupBy { it.currency }.forEach { (currency, comps) ->
                        val amount = comps.fold(ZERO) { acc, c -> acc.add(c.amount) }
                        if (amount.signum() > 0) {
                            lines +=
                                OwedLine(dep.slotId, dep.tourId, dep.date, dep.timeSlot, guests, currency, amount, comps.map { it.label })
                        }
                    }
                    val manual =
                        adjustments.any {
                            it.kind == AdjustmentKind.CHARGE && it.slotId == dep.slotId && it.id !in reversed && it.party == party
                        }
                    if (rates.isEmpty() && !manual) issue(OwedIssueCode.MANUAL_AMOUNT_NEEDED)
                }
            }
        }
        return lines to issues
    }

    /** Every movement of the party in date order: departures, adjustments and payments, each reversal on its own date. */
    fun movements(
        owed: List<OwedLine>,
        adjustments: List<PayableAdjustment>,
        payments: List<SettlementPayment>,
        dayOf: (Instant) -> LocalDate,
    ): List<PayableMovement> {
        val adjustmentById = adjustments.associateBy { it.id }
        val out = mutableListOf<PayableMovement>()
        owed.forEach { out += PayableMovement(it.date, MovementKind.DEPARTURE, it.currency, it.amount, owedLine = it) }
        adjustments.forEach { a ->
            val (kind, signed) =
                when (a.kind) {
                    AdjustmentKind.CHARGE -> MovementKind.CHARGE to a.amount
                    AdjustmentKind.DEDUCTION -> MovementKind.DEDUCTION to a.amount.negate()
                    AdjustmentKind.REVERSAL ->
                        if (adjustmentById[a.reversesAdjustmentId]?.kind == AdjustmentKind.DEDUCTION) {
                            MovementKind.DEDUCTION_REVERSED to a.amount
                        } else {
                            MovementKind.CHARGE_REVERSED to a.amount.negate()
                        }
                }
            out += PayableMovement(a.serviceDate, kind, a.currency, signed, adjustment = a, recordedAt = a.recordedAt)
        }
        payments.forEach { p ->
            val kind = if (p.kind == SettlementPaymentKind.PAYMENT) MovementKind.PAYMENT else MovementKind.PAYMENT_REVERSED
            val signed = if (p.kind == SettlementPaymentKind.PAYMENT) p.amount.negate() else p.amount
            out += PayableMovement(dayOf(p.recordedAt), kind, p.currency, signed, payment = p, recordedAt = p.recordedAt)
        }
        return out.sortedWith(compareBy<PayableMovement>({ it.date }, { it.recordedAt ?: Instant.MIN }))
    }

    fun balances(
        movements: List<PayableMovement>,
        from: LocalDate?,
        to: LocalDate,
    ): List<CurrencyBalance> =
        movements
            .filter { !it.date.isAfter(to) }
            .groupBy { it.currency }
            .map { (currency, list) ->
                val (before, within) = list.partition { from != null && it.date.isBefore(from) }
                val opening = before.fold(ZERO) { acc, m -> acc.add(m.signed) }
                val paidKinds = setOf(MovementKind.PAYMENT, MovementKind.PAYMENT_REVERSED)
                val owed = within.filter { it.kind !in paidKinds }.fold(ZERO) { acc, m -> acc.add(m.signed) }
                val paid = within.filter { it.kind in paidKinds }.fold(ZERO) { acc, m -> acc.subtract(m.signed) }
                CurrencyBalance(currency, opening, owed, paid, opening.add(owed).subtract(paid))
            }.sortedBy { it.currency }
}
