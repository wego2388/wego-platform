package com.wego.toursoperator.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

/**
 * Methods the owner approved for office payments (2026-10-05). Every one is
 * recorded by hand by staff: there is no online integration with any of them.
 */
enum class CollectionMethod(
    val isCash: Boolean,
) {
    CASH_AT_OFFICE(true),
    CASH_ON_PICKUP(true),
    MOBILE_WALLET(false),

    /** The card machine in the office. */
    CARD_TERMINAL(false),
    INSTAPAY(false),

    /** The Fawry machine in the office. */
    FAWRY_OFFICE(false),
}

enum class OfficeCollectionKind {
    COLLECTION,
    REVERSAL,
}

/** The currency the customer actually handed over. Balances are always EUR. */
enum class PaidCurrency {
    EUR,
    EGP,
}

/**
 * A manager-set EUR→EGP rate (EGP per 1 EUR) for one day. Never edited; a new
 * rate for the same day supersedes it and the old row stays as history.
 */
data class FxRate(
    val id: UUID,
    val rateDate: java.time.LocalDate,
    val egpPerEur: BigDecimal,
    val setByUserId: UUID?,
    val setAt: Instant,
) {
    init {
        require(egpPerEur.scale() <= RATE_SCALE) { "Rate has at most $RATE_SCALE decimals" }
        require(egpPerEur >= MIN_RATE && egpPerEur <= MAX_RATE) { "Rate must be between $MIN_RATE and $MAX_RATE EGP per EUR" }
    }

    companion object {
        const val RATE_SCALE = 4
        val MIN_RATE: BigDecimal = BigDecimal("1")
        val MAX_RATE: BigDecimal = BigDecimal("1000")
    }
}

/** Outcome of settling a payment against an outstanding EUR balance. */
sealed class Settlement {
    data class Settled(
        val eur: Money,
        val rate: FxRate?,
    ) : Settlement()

    /** Paying EGP needs today's manager-set rate; staff cannot supply their own. */
    data object RateMissing : Settlement()

    /** The payment converts to less than one cent. */
    data object TooSmall : Settlement()

    data class ExceedsOutstanding(
        val outstanding: Money,
    ) : Settlement()

    companion object {
        /**
         * EUR settles 1:1. EGP converts at the manager-set [rate], half-up to
         * cents. The settled EUR may never exceed [outstanding]; to let the last
         * payment close the balance exactly despite rounding, paying exactly the
         * EGP the balance is worth (outstanding × rate, half-up) settles the
         * exact outstanding.
         */
        fun of(
            paid: Money,
            currency: PaidCurrency,
            rate: FxRate?,
            outstanding: Money,
        ): Settlement {
            if (currency == PaidCurrency.EUR) {
                return if (paid.amount > outstanding.amount) ExceedsOutstanding(outstanding) else Settled(paid, null)
            }
            rate ?: return RateMissing
            val exactEgpDue = outstanding.amount.multiply(rate.egpPerEur).setScale(Money.REQUIRED_SCALE, RoundingMode.HALF_UP)
            if (outstanding.amount.signum() > 0 && paid.amount.compareTo(exactEgpDue) == 0) return Settled(outstanding, rate)
            val eur = paid.amount.divide(rate.egpPerEur, Money.REQUIRED_SCALE, RoundingMode.HALF_UP)
            if (eur.signum() == 0) return TooSmall
            if (eur > outstanding.amount) return ExceedsOutstanding(outstanding)
            return Settled(Money(eur), rate)
        }
    }
}

/** A trimmed receipt number from a terminal, wallet, InstaPay or Fawry. Never logged or echoed in errors. */
object CollectionReference {
    const val MAX_LENGTH = 64

    fun of(raw: String): String {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= MAX_LENGTH) { "Payment reference must be 1 to $MAX_LENGTH characters" }
        require(trimmed.none { Character.isISOControl(it) }) { "Payment reference must not contain control characters" }
        return trimmed
    }
}

/**
 * One append-only entry of the office payment ledger. A mistake is never
 * edited or deleted: it is cancelled by a REVERSAL entry that names the
 * collection it reverses and gives a reason. [amount] is the EUR settled
 * against the booking; [amountPaid] is what the customer handed over in
 * [currencyPaid] (the same figure for EUR), with the [fxRate] used for EGP.
 */
data class OfficeCollection(
    val id: UUID,
    val bookingId: BookingId,
    val kind: OfficeCollectionKind,
    val method: CollectionMethod,
    val amount: Money,
    val currencyPaid: PaidCurrency,
    val amountPaid: Money,
    val fxRate: FxRate?,
    val reference: String?,
    val reversesCollectionId: UUID?,
    val reason: String?,
    val recordedByUserId: UUID?,
    val clientRequestId: UUID,
    val recordedAt: Instant,
) {
    init {
        require(amount.amount.signum() > 0 && amountPaid.amount.signum() > 0) { "A collection amount must be greater than zero" }
        require((currencyPaid == PaidCurrency.EGP) == (fxRate != null)) { "Only an EGP payment carries a rate" }
        require(currencyPaid == PaidCurrency.EGP || amountPaid == amount) { "A EUR payment settles itself exactly" }
        when (kind) {
            OfficeCollectionKind.COLLECTION -> {
                require(reversesCollectionId == null && reason == null) { "A collection neither reverses nor carries a reason" }
                require(method.isCash == (reference == null)) { "Non-cash payments need a reference; cash carries none" }
            }
            OfficeCollectionKind.REVERSAL -> {
                require(reversesCollectionId != null && !reason.isNullOrBlank()) { "A reversal names the collection it reverses and a reason" }
                require(reference == null) { "A reversal carries no receipt reference" }
            }
        }
    }
}

enum class OfficePaymentState {
    UNPAID,
    PARTIALLY_PAID,
    PAID,
}

/**
 * Payment truth of an office booking, derived from its ledger — never stored,
 * so it cannot disagree with the entries.
 */
data class OfficePaymentSummary(
    val total: Money,
    val collected: Money,
) {
    init {
        require(collected.amount <= total.amount) { "Collected cash must not exceed the booking total" }
    }

    val outstanding: Money get() = Money(total.amount.subtract(collected.amount))

    val state: OfficePaymentState
        get() =
            when {
                collected.amount.signum() == 0 -> OfficePaymentState.UNPAID
                collected.amount.compareTo(total.amount) == 0 -> OfficePaymentState.PAID
                else -> OfficePaymentState.PARTIALLY_PAID
            }

    companion object {
        fun of(
            total: Money,
            entries: List<OfficeCollection>,
        ): OfficePaymentSummary = fromNet(total, netCollected(entries))

        fun fromNet(
            total: Money,
            net: BigDecimal,
        ): OfficePaymentSummary = OfficePaymentSummary(total, Money(net.setScale(Money.REQUIRED_SCALE)))

        fun netCollected(entries: List<OfficeCollection>): BigDecimal =
            entries.fold(BigDecimal.ZERO.setScale(Money.REQUIRED_SCALE)) { acc, e ->
                if (e.kind == OfficeCollectionKind.COLLECTION) acc.add(e.amount.amount) else acc.subtract(e.amount.amount)
            }
    }
}
