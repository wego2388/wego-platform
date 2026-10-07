package com.wego.toursoperator.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/*
 * WEGO-016-OPS2-F: costs, refunds, supplier/driver settlements and the daily
 * cash box. Customer money (Money, EUR) and supplier money (EGP or EUR) are
 * kept apart: every supplier-side amount carries its own currency and is only
 * converted to EUR inside a report, at a stated manager-set rate.
 */

/** Rules for an amount staff type on the supplier side: positive, two decimals at most, within the column range. */
object FinanceAmount {
    val MAX: BigDecimal = BigDecimal("9999999.99")

    fun positive(raw: BigDecimal): BigDecimal {
        require(raw.signum() > 0) { "Amount must be greater than zero" }
        require(raw.stripTrailingZeros().scale() <= 2) { "Amount has at most 2 decimals" }
        val amount = raw.setScale(2, RoundingMode.UNNECESSARY)
        require(amount <= MAX) { "Amount must not exceed $MAX" }
        return amount
    }

    fun nonNegative(raw: BigDecimal): BigDecimal {
        require(raw.signum() >= 0) { "Amount must not be negative" }
        require(raw.stripTrailingZeros().scale() <= 2) { "Amount has at most 2 decimals" }
        val amount = raw.setScale(2, RoundingMode.UNNECESSARY)
        require(amount <= MAX) { "Amount must not exceed $MAX" }
        return amount
    }

    /** Optional free text: trimmed, never blank, no control characters except line breaks. */
    fun text(
        raw: String?,
        field: String,
        max: Int,
        required: Boolean = false,
    ): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() }
        require(!required || value != null) { "$field is required" }
        if (value != null) {
            require(value.length <= max) { "$field must be at most $max characters" }
            require(value.none { Character.isISOControl(it) && it != '\n' }) { "$field must not contain control characters" }
        }
        return value
    }

    fun toEur(
        amount: BigDecimal,
        currency: PaidCurrency,
        egpPerEur: BigDecimal?,
    ): BigDecimal? =
        when (currency) {
            PaidCurrency.EUR -> amount.setScale(2, RoundingMode.HALF_UP)
            PaidCurrency.EGP -> egpPerEur?.let { amount.divide(it, 2, RoundingMode.HALF_UP) }
        }
}

// ── Cost model ───────────────────────────────────────────────────────────────

enum class CostCategory {
    /** What the supplier charges us. */
    SUPPLIER,

    /** Our own extra costs (entry fees, food, water…). */
    OWN_EXTRA,

    /** A fixed amount per departure (e.g. a guide). */
    FIXED,

    /** A driver's pay per trip. */
    DRIVER,
}

enum class CostBasis { PER_PERSON, PER_UNIT, PER_DEPARTURE }

/**
 * One effective-dated cost of a tour departure (or of a driver trip). Never
 * edited: a change ends this row and adds one that names it in
 * [replacesComponentId]. [validUntil] is inclusive.
 */
data class CostComponent(
    val id: UUID,
    val tourId: TourId?,
    val driverId: UUID?,
    val category: CostCategory,
    val label: String,
    val basis: CostBasis,
    val currency: PaidCurrency,
    val amount: BigDecimal,
    /** PER_PERSON only; null means a child costs the adult amount (never less than what we know). */
    val childAmount: BigDecimal?,
    /** SUPPLIER only; null means "whichever single supplier is assigned to the departure". */
    val supplierId: UUID?,
    val validFrom: LocalDate,
    val validUntil: LocalDate?,
    val replacesComponentId: UUID?,
    val note: String?,
    val createdByUserId: UUID?,
    val createdAt: Instant,
    val endedByUserId: UUID? = null,
    val endedAt: Instant? = null,
    /** Idempotency key of the request that created it (unique per creator). */
    val clientRequestId: UUID = UUID.randomUUID(),
) {
    init {
        require((tourId == null) != (driverId == null)) { "A cost belongs to a tour or to a driver" }
        require((category == CostCategory.DRIVER) == (driverId != null)) { "Only a driver cost belongs to a driver" }
        require(category !in setOf(CostCategory.FIXED, CostCategory.DRIVER) || basis == CostBasis.PER_DEPARTURE) {
            "Fixed and driver costs are per departure"
        }
        require(childAmount == null || basis == CostBasis.PER_PERSON) { "A child amount applies to per-person costs only" }
        require(supplierId == null || category == CostCategory.SUPPLIER) { "Only a supplier cost names a supplier" }
        require(amount.scale() == 2 && amount.signum() >= 0 && amount <= FinanceAmount.MAX) { "Invalid cost amount" }
        require(childAmount == null || (childAmount.scale() == 2 && childAmount.signum() >= 0)) { "Invalid child amount" }
        require(label.isNotBlank() && label == label.trim() && label.length <= 80) { "Label must be 1 to 80 characters" }
        require(validUntil == null || !validUntil.isBefore(validFrom.minusDays(1))) { "validUntil is before validFrom" }
    }

    fun effectiveOn(day: LocalDate): Boolean = !day.isBefore(validFrom) && (validUntil == null || !day.isAfter(validUntil))

    /** Review M3: the same cost twice — same owner, category, label, supplier and currency over overlapping days. */
    fun duplicates(other: CostComponent): Boolean =
        tourId == other.tourId &&
            driverId == other.driverId &&
            category == other.category &&
            label == other.label &&
            supplierId == other.supplierId &&
            currency == other.currency &&
            !(validUntil != null && validUntil.isBefore(other.validFrom)) &&
            !(other.validUntil != null && other.validUntil.isBefore(validFrom))

    val ended: Boolean get() = endedAt != null

    /**
     * What one booking costs under this component, in [currency]. Null when it
     * cannot be applied: a per-unit cost on a booking that did not buy units.
     * Per-departure components are charged once per departure, not here.
     */
    fun bookingCost(pricing: BookingPricing): BigDecimal? =
        when (basis) {
            CostBasis.PER_PERSON ->
                amount
                    .multiply(BigDecimal(pricing.adultsCount))
                    .add((childAmount ?: amount).multiply(BigDecimal(pricing.childrenCount)))
                    .setScale(2, RoundingMode.HALF_UP)
            CostBasis.PER_UNIT -> pricing.unit?.let { amount.multiply(BigDecimal(it.unitCount)).setScale(2, RoundingMode.HALF_UP) }
            CostBasis.PER_DEPARTURE -> BigDecimal.ZERO.setScale(2)
        }
}

// ── Office refunds ───────────────────────────────────────────────────────────

/** How money was handed back: cash from the office, or the same non-cash method the customer used. */
enum class RefundMethod(
    val isCash: Boolean,
) {
    CASH(true),
    MOBILE_WALLET(false),
    CARD_TERMINAL(false),
    INSTAPAY(false),
    FAWRY_OFFICE(false),
    BANK_TRANSFER(false),
}

enum class OfficeRefundKind { REFUND, REVERSAL }

/**
 * Money returned to the customer of a cancelled office booking. Append-only:
 * a mistake is cancelled by a REVERSAL. [amount] is the EUR it returns against
 * what was collected; [amountPaid] is what was handed back in [currencyPaid].
 */
data class OfficeRefund(
    val id: UUID,
    val bookingId: BookingId,
    val kind: OfficeRefundKind,
    val method: RefundMethod,
    val amount: Money,
    val currencyPaid: PaidCurrency,
    val amountPaid: Money,
    val fxRate: FxRate?,
    val reference: String?,
    val reason: String,
    val reversesRefundId: UUID?,
    val recordedByUserId: UUID?,
    val clientRequestId: UUID,
    val recordedAt: Instant,
    val recordedByEmail: String? = null,
) {
    init {
        require(amount.amount.signum() > 0 && amountPaid.amount.signum() > 0) { "A refund amount must be greater than zero" }
        require((currencyPaid == PaidCurrency.EGP) == (fxRate != null)) { "Only an EGP refund carries a rate" }
        require(currencyPaid == PaidCurrency.EGP || amountPaid == amount) { "A EUR refund returns itself exactly" }
        require(reason.isNotBlank()) { "A refund needs a reason" }
        when (kind) {
            OfficeRefundKind.REFUND -> {
                require(reversesRefundId == null) { "A refund reverses nothing" }
                require(method.isCash == (reference == null)) { "Non-cash refunds need a reference; cash carries none" }
            }
            OfficeRefundKind.REVERSAL ->
                require(
                    reversesRefundId != null && reference == null,
                ) { "A reversal names the refund it reverses" }
        }
    }

    companion object {
        fun netRefunded(entries: List<OfficeRefund>): BigDecimal =
            entries.fold(BigDecimal.ZERO.setScale(2)) { acc, e ->
                if (e.kind == OfficeRefundKind.REFUND) acc.add(e.amount.amount) else acc.subtract(e.amount.amount)
            }
    }
}

// ── Settlements ──────────────────────────────────────────────────────────────

enum class PartyType { SUPPLIER, DRIVER }

data class PartyRef(
    val type: PartyType,
    val id: UUID,
) {
    val key: String get() = "${type.name}:$id"
}

enum class SettlementMethod(
    val isCash: Boolean,
) {
    CASH(true),
    INSTAPAY(false),
    MOBILE_WALLET(false),
    BANK_TRANSFER(false),
    OTHER(false),
}

enum class AdjustmentKind { CHARGE, DEDUCTION, REVERSAL }

/** A documented addition to (CHARGE) or reduction of (DEDUCTION) what a supplier or driver is owed. */
data class PayableAdjustment(
    val id: UUID,
    val party: PartyRef,
    val slotId: TourSlotId?,
    val serviceDate: LocalDate,
    val kind: AdjustmentKind,
    val currency: PaidCurrency,
    val amount: BigDecimal,
    val reason: String,
    val reversesAdjustmentId: UUID?,
    val recordedByUserId: UUID?,
    val clientRequestId: UUID,
    val recordedAt: Instant,
    val recordedByEmail: String? = null,
) {
    init {
        require(amount.scale() == 2 && amount.signum() > 0) { "Adjustment amount must be greater than zero" }
        require((kind == AdjustmentKind.REVERSAL) == (reversesAdjustmentId != null)) { "Only a reversal names an adjustment" }
        require(reason.isNotBlank()) { "An adjustment needs a reason" }
    }
}

/** The owner's approval for one payment above the manager limit; consumed by exactly one payment. */
data class SettlementApproval(
    val id: UUID,
    val party: PartyRef,
    val currency: PaidCurrency,
    val amount: BigDecimal,
    val note: String?,
    val approvedByUserId: UUID?,
    val clientRequestId: UUID,
    val approvedAt: Instant,
    val approvedByEmail: String? = null,
    /** Read model: the payment that consumed it, if any. */
    val usedByPaymentId: UUID? = null,
)

enum class SettlementPaymentKind { PAYMENT, REVERSAL }

data class SettlementPayment(
    val id: UUID,
    val party: PartyRef,
    val kind: SettlementPaymentKind,
    val method: SettlementMethod,
    val currency: PaidCurrency,
    val amount: BigDecimal,
    /** Value in EGP checked against the manager limit; null when a EUR payment had no rate that day. */
    val egpEquivalent: BigDecimal?,
    val fxRateId: UUID?,
    val reference: String?,
    val note: String?,
    val approvalId: UUID?,
    val reversesPaymentId: UUID?,
    val reason: String?,
    val recordedByUserId: UUID?,
    val clientRequestId: UUID,
    val recordedAt: Instant,
    val recordedByEmail: String? = null,
) {
    init {
        require(amount.scale() == 2 && amount.signum() > 0) { "Payment amount must be greater than zero" }
        when (kind) {
            SettlementPaymentKind.PAYMENT -> {
                require(reversesPaymentId == null && reason == null) { "A payment reverses nothing" }
                require(method.isCash || reference != null) { "A non-cash payment needs a reference" }
                require(
                    approvalId != null || !SettlementPolicy.needsApproval(egpEquivalent),
                ) { "Above the manager limit a payment needs an approval" }
            }
            SettlementPaymentKind.REVERSAL ->
                require(
                    reversesPaymentId != null && !reason.isNullOrBlank() && approvalId == null,
                ) { "A reversal names the payment and a reason" }
        }
    }
}

/**
 * Owner-delegated default (hub «التكاليف والتسويات», marked for review): a
 * manager pays a supplier or driver up to 5000 EGP per payment without the
 * owner; above that the owner approves first. Exactly 5000.00 needs no approval.
 * A EUR payment is valued at today's manager rate; without a rate it is
 * treated as above the limit (never guessed).
 */
object SettlementPolicy {
    val MANAGER_LIMIT_EGP: BigDecimal = BigDecimal("5000.00")

    fun egpEquivalent(
        currency: PaidCurrency,
        amount: BigDecimal,
        rate: FxRate?,
    ): BigDecimal? =
        when (currency) {
            PaidCurrency.EGP -> amount
            PaidCurrency.EUR -> rate?.let { amount.multiply(it.egpPerEur).setScale(2, RoundingMode.HALF_UP) }
        }

    fun needsApproval(egpEquivalent: BigDecimal?): Boolean = egpEquivalent == null || egpEquivalent > MANAGER_LIMIT_EGP

    /** Today's rate may differ from the previous day's by at most 20 %; beyond that EUR values are not trusted without approval. */
    val RATE_BAND: BigDecimal = BigDecimal("0.20")

    fun rateTrusted(
        today: FxRate?,
        previous: FxRate?,
    ): Boolean =
        today != null &&
            (
                previous == null ||
                    today.egpPerEur
                        .subtract(previous.egpPerEur)
                        .abs()
                        .divide(previous.egpPerEur, 6, RoundingMode.HALF_UP) <= RATE_BAND
            )

    /**
     * OPS2-F review M2 (owner-delegated reading): the manager limit is per party per Cairo day.
     * Today's non-reversed payments to the party plus this one must stay ≤ 5000.00 EGP; an
     * unknown EGP value (EUR without a rate set today, or outside the 20 % band) needs approval.
     */
    fun dailyNeedsApproval(
        paidTodayEgp: BigDecimal?,
        egpEquivalent: BigDecimal?,
        currency: PaidCurrency,
        rateTrusted: Boolean,
    ): Boolean =
        egpEquivalent == null ||
            paidTodayEgp == null ||
            (currency == PaidCurrency.EUR && !rateTrusted) ||
            paidTodayEgp.add(egpEquivalent) > MANAGER_LIMIT_EGP

    /** Today's non-reversed payments to one party, in EGP; null when one of them has no EGP value. */
    fun paidTodayEgp(
        payments: List<SettlementPayment>,
        isToday: (Instant) -> Boolean,
    ): BigDecimal? {
        val reversed = payments.mapNotNull { it.reversesPaymentId }.toSet()
        return payments
            .filter { it.kind == SettlementPaymentKind.PAYMENT && it.id !in reversed && isToday(it.recordedAt) }
            .fold(BigDecimal.ZERO.setScale(2) as BigDecimal?) { acc, p -> p.egpEquivalent?.let { acc?.add(it) } }
    }
}

/** One EGP office collection still available to be refunded at its own rate. */
data class EgpLot(
    val collectionId: UUID,
    val egp: BigDecimal,
    val eur: BigDecimal,
    val rate: FxRate,
)

/**
 * OPS2-F review L1: an EGP refund returns money at the rate(s) the customer paid at, FIFO over
 * the booking's EGP collections (net of reversals and of earlier EGP refunds). Only what goes
 * beyond those collections (or a booking paid only in EUR) uses today's rate, with a warning.
 */
object EgpRefundRule {
    data class Conversion(
        val eurFromLots: BigDecimal,
        val excessEgp: BigDecimal,
        val firstRate: FxRate?,
    )

    fun remainingLots(
        collections: List<OfficeCollection>,
        refunds: List<OfficeRefund>,
    ): List<EgpLot> {
        val reversedCollections = collections.mapNotNull { it.reversesCollectionId }.toSet()
        val reversedRefunds = refunds.mapNotNull { it.reversesRefundId }.toSet()
        var alreadyRefunded =
            refunds
                .filter { it.kind == OfficeRefundKind.REFUND && it.currencyPaid == PaidCurrency.EGP && it.id !in reversedRefunds }
                .fold(BigDecimal.ZERO.setScale(2)) { acc, r -> acc.add(r.amountPaid.amount) }
        return collections
            .filter { it.kind == OfficeCollectionKind.COLLECTION && it.currencyPaid == PaidCurrency.EGP && it.id !in reversedCollections }
            .sortedWith(compareBy({ it.recordedAt }, { it.id }))
            .mapNotNull { c ->
                val egp = c.amountPaid.amount
                val used = alreadyRefunded.min(egp)
                alreadyRefunded = alreadyRefunded.subtract(used)
                val left = egp.subtract(used)
                if (left.signum() <= 0) {
                    null
                } else {
                    val eur =
                        if (used.signum() ==
                            0
                        ) {
                            c.amount.amount
                        } else {
                            c.amount.amount
                                .multiply(left)
                                .divide(egp, 2, RoundingMode.HALF_UP)
                        }
                    EgpLot(c.id, left, eur, checkNotNull(c.fxRate))
                }
            }
    }

    fun convert(
        lots: List<EgpLot>,
        amountEgp: BigDecimal,
    ): Conversion {
        var remaining = amountEgp
        var eur = BigDecimal.ZERO.setScale(2)
        var first: FxRate? = null
        for (lot in lots) {
            if (remaining.signum() <= 0) break
            val take = remaining.min(lot.egp)
            eur = eur.add(if (take.compareTo(lot.egp) == 0) lot.eur else lot.eur.multiply(take).divide(lot.egp, 2, RoundingMode.HALF_UP))
            first = first ?: lot.rate
            remaining = remaining.subtract(take)
        }
        return Conversion(eur, remaining, first)
    }
}

// ── Cash box ────────────────────────────────────────────────────────────────

enum class CashBoxEventKind { COUNT, CONFIRM, REOPEN }

enum class CashDayState { OPEN, COUNTED, CLOSED }

data class CashBoxEvent(
    val id: UUID,
    val businessDate: LocalDate,
    val currency: PaidCurrency,
    val sequence: Int,
    val kind: CashBoxEventKind,
    val expected: BigDecimal?,
    val counted: BigDecimal?,
    val difference: BigDecimal?,
    val note: String?,
    val countEventId: UUID?,
    val actorUserId: UUID?,
    val occurredAt: Instant,
    val actorEmail: String? = null,
) {
    init {
        when (kind) {
            CashBoxEventKind.COUNT ->
                require(
                    expected != null &&
                        counted != null &&
                        counted.signum() >= 0 &&
                        difference == counted.subtract(expected) &&
                        countEventId == null,
                ) { "A count records expected, counted and the difference" }
            CashBoxEventKind.CONFIRM ->
                require(
                    expected != null && counted == null && countEventId != null,
                ) { "A confirmation names its count" }
            CashBoxEventKind.REOPEN -> require(!note.isNullOrBlank() && expected == null && counted == null) { "Reopening needs a reason" }
        }
    }

    companion object {
        /** The day's state is its latest event: no event or a reopen = OPEN. */
        fun state(events: List<CashBoxEvent>): CashDayState =
            when (events.maxByOrNull { it.sequence }?.kind) {
                null, CashBoxEventKind.REOPEN -> CashDayState.OPEN
                CashBoxEventKind.COUNT -> CashDayState.COUNTED
                CashBoxEventKind.CONFIRM -> CashDayState.CLOSED
            }
    }
}

/** Cash in and out of the office box on one Cairo day, in one currency. */
data class CashFlow(
    val collected: BigDecimal,
    val collectionsReversed: BigDecimal,
    val refunded: BigDecimal,
    val refundsReversed: BigDecimal,
    val settlementsPaid: BigDecimal,
    val settlementsReversed: BigDecimal,
) {
    /** Cash collections − cash refunds − cash settlement payments, each net of its reversals. */
    val expected: BigDecimal
        get() =
            collected
                .subtract(collectionsReversed)
                .subtract(refunded.subtract(refundsReversed))
                .subtract(settlementsPaid.subtract(settlementsReversed))
                .setScale(2)

    companion object {
        val EMPTY = BigDecimal.ZERO.setScale(2).let { z -> CashFlow(z, z, z, z, z, z) }
    }
}
