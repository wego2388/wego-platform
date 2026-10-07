package com.wego.toursoperator.application

import com.wego.toursoperator.domain.Booking
import com.wego.toursoperator.domain.BookingChannel
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.BookingStatus
import com.wego.toursoperator.domain.CollectionMethod
import com.wego.toursoperator.domain.CollectionReference
import com.wego.toursoperator.domain.Money
import com.wego.toursoperator.domain.OfficeCollection
import com.wego.toursoperator.domain.OfficeCollectionKind
import com.wego.toursoperator.domain.OfficePaymentSummary
import com.wego.toursoperator.domain.PaidCurrency
import com.wego.toursoperator.domain.Settlement
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class RecordCollectionCommand(
    val bookingId: BookingId,
    val method: CollectionMethod,
    /** What the customer handed over, in [currency]. */
    val amountPaid: Money,
    val currency: PaidCurrency,
    /** Receipt number from the terminal/wallet/InstaPay/Fawry; required for non-cash, absent for cash. */
    val reference: String?,
    /** The rate the staff member was shown; required for EGP. */
    val fxRateId: UUID?,
    /** Re-records this reversed collection (same method and reference) under its receipt. */
    val correctsCollectionId: UUID?,
    val actorUserId: UUID,
    val clientRequestId: UUID,
)

data class ReverseCollectionCommand(
    val bookingId: BookingId,
    val collectionId: UUID,
    val reason: String,
    val actorUserId: UUID,
    val clientRequestId: UUID,
)

sealed class QuoteResult {
    data class Quoted(
        val settlement: Settlement,
        val outstanding: Money,
    ) : QuoteResult()

    data object BookingNotFound : QuoteResult()

    data object NotAnOfficeBooking : QuoteResult()
}

sealed class CollectionResult {
    data class Recorded(
        val entry: OfficeCollection,
        val summary: OfficePaymentSummary,
    ) : CollectionResult()

    /** The same staff actor already recorded this entry with this request id; nothing new was written. */
    data class Replayed(
        val entry: OfficeCollection,
        val summary: OfficePaymentSummary,
    ) : CollectionResult()

    data object BookingNotFound : CollectionResult()

    /** Online bookings are paid through the provider and never take office cash. */
    data object NotAnOfficeBooking : CollectionResult()

    /** Payments can only be recorded on a live (confirmed or completed) office booking. */
    data class BookingNotOpen(
        val status: BookingStatus,
    ) : CollectionResult()

    data class ExceedsOutstanding(
        val outstanding: Money,
    ) : CollectionResult()

    data object ReferenceRequired : CollectionResult()

    data object ReferenceNotAllowed : CollectionResult()

    /** The same receipt was already recorded for this method. */
    data object ReferenceAlreadyUsed : CollectionResult()

    /** EGP needs today's manager-set rate; none exists yet. */
    data object FxRateNotSet : CollectionResult()

    /** An EGP payment worth less than 1.00 EUR at today's rate. */
    data object AmountBelowMinimum : CollectionResult()

    /** The manager changed today's rate after the staff member's quote; re-quote and retry. */
    data object FxRateChanged : CollectionResult()

    data object FxRateIdRequired : CollectionResult()

    /** Only a different staff member (a manager) may reverse an entry; nobody reverses their own. */
    data object CannotReverseOwn : CollectionResult()

    /** The entry to correct is missing, not reversed, a different method/reference, or already corrected. */
    data object InvalidCorrection : CollectionResult()

    data object CollectionNotFound : CollectionResult()

    data object AlreadyReversed : CollectionResult()

    /** Only a COLLECTION can be reversed; a reversal is final. */
    data object NotReversible : CollectionResult()

    data object IdempotencyKeyReused : CollectionResult()

    /** Today's office cash box for this currency is closed: no cash entry can be added to it (OPS2-F). */
    data object CashDayClosed : CollectionResult()

    /** Reversing this collection would leave less collected than was already refunded (OPS2-F review M1). */
    data object RefundsExceedCollected : CollectionResult()
}

/**
 * Office cash ledger. The booking row lock serializes every entry for one
 * booking, so concurrent collections can never push the collected total past
 * the booking total. This ledger is independent of the online (Paymob)
 * payment tables: nothing here creates a payment row, confirms the booking or
 * touches online revenue recognition.
 */
class OfficeCollectionService(
    private val bookingRepository: BookingRepository,
    private val collectionRepository: OfficeCollectionRepository,
    private val fxRateRepository: FxRateRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
    private val cashDayGate: CashDayGate = CashDayGate.NONE,
    private val refundRepository: OfficeRefundRepository? = null,
) {
    fun record(command: RecordCollectionCommand): CollectionResult =
        transactionRunner.runInTransaction {
            val booking = lockOffice(command.bookingId)
            if (booking is Locked.Failure) return@runInTransaction booking.result
            booking as Locked.Ok

            val entries = collectionRepository.findByBooking(booking.booking.id)
            collectionRepository.findByRequest(command.actorUserId, command.clientRequestId)?.let { existing ->
                val same =
                    existing.bookingId == booking.booking.id &&
                        existing.kind == OfficeCollectionKind.COLLECTION &&
                        existing.method == command.method &&
                        existing.currencyPaid == command.currency &&
                        existing.amountPaid == command.amountPaid &&
                        existing.reference == command.reference?.let(CollectionReference::of) &&
                        existing.correctsCollectionId == command.correctsCollectionId &&
                        (command.currency != PaidCurrency.EGP || existing.fxRate?.id == command.fxRateId)
                return@runInTransaction if (same) {
                    CollectionResult.Replayed(existing, summary(booking.booking, entries))
                } else {
                    CollectionResult.IdempotencyKeyReused
                }
            }

            if (booking.booking.status != BookingStatus.CONFIRMED && booking.booking.status != BookingStatus.COMPLETED) {
                return@runInTransaction CollectionResult.BookingNotOpen(booking.booking.status)
            }

            val reference = command.reference?.let(CollectionReference::of)
            if (!command.method.isCash && reference == null) return@runInTransaction CollectionResult.ReferenceRequired
            if (command.method.isCash && reference != null) return@runInTransaction CollectionResult.ReferenceNotAllowed
            if (command.correctsCollectionId != null) {
                // The only way a receipt reference is reused: explicitly correcting a reversed entry
                // of this booking with the same method and reference, once.
                val corrected = entries.firstOrNull { it.id == command.correctsCollectionId }
                val valid =
                    reference != null &&
                        corrected != null &&
                        corrected.kind == OfficeCollectionKind.COLLECTION &&
                        corrected.method == command.method &&
                        corrected.reference == reference &&
                        entries.any { it.reversesCollectionId == corrected.id } &&
                        entries.none { it.correctsCollectionId == corrected.id }
                if (!valid) return@runInTransaction CollectionResult.InvalidCorrection
            } else if (reference != null && collectionRepository.referenceExists(command.method, reference)) {
                return@runInTransaction CollectionResult.ReferenceAlreadyUsed
            }

            val current = summary(booking.booking, entries)
            val rate = if (command.currency == PaidCurrency.EGP) fxRateRepository.latestFor(FxRateService.todayInSharm(clock)) else null
            if (command.currency == PaidCurrency.EGP) {
                if (rate == null) return@runInTransaction CollectionResult.FxRateNotSet
                if (command.fxRateId == null) return@runInTransaction CollectionResult.FxRateIdRequired
                if (command.fxRateId != rate.id) return@runInTransaction CollectionResult.FxRateChanged
            }
            val settled =
                when (val settlement = Settlement.of(command.amountPaid, command.currency, rate, current.outstanding)) {
                    is Settlement.Settled -> settlement
                    Settlement.RateMissing -> return@runInTransaction CollectionResult.FxRateNotSet
                    Settlement.BelowMinimum -> return@runInTransaction CollectionResult.AmountBelowMinimum
                    is Settlement.ExceedsOutstanding -> return@runInTransaction CollectionResult.ExceedsOutstanding(settlement.outstanding)
                }
            if (command.method.isCash && !cashDayGate.lockOpenToday(command.currency)) {
                return@runInTransaction CollectionResult.CashDayClosed
            }

            val entry =
                OfficeCollection(
                    id = UUID.randomUUID(),
                    bookingId = booking.booking.id,
                    kind = OfficeCollectionKind.COLLECTION,
                    method = command.method,
                    amount = settled.eur,
                    currencyPaid = command.currency,
                    amountPaid = command.amountPaid,
                    fxRate = settled.rate,
                    reference = reference,
                    reversesCollectionId = null,
                    reason = null,
                    recordedByUserId = command.actorUserId,
                    clientRequestId = command.clientRequestId,
                    recordedAt = Instant.now(clock),
                    correctsCollectionId = command.correctsCollectionId,
                )
            collectionRepository.append(entry)
            CollectionResult.Recorded(entry, summary(booking.booking, entries + entry))
        }

    /** Preview for the ERP form: what a payment would settle, using today's manager-set rate. */
    fun quote(
        bookingId: BookingId,
        amountPaid: Money,
        currency: PaidCurrency,
    ): QuoteResult {
        val booking = bookingRepository.findById(bookingId) ?: return QuoteResult.BookingNotFound
        if (booking.channel != BookingChannel.OFFICE) return QuoteResult.NotAnOfficeBooking
        val outstanding = summary(booking, collectionRepository.findByBooking(bookingId)).outstanding
        val rate = if (currency == PaidCurrency.EGP) fxRateRepository.latestFor(FxRateService.todayInSharm(clock)) else null
        return QuoteResult.Quoted(Settlement.of(amountPaid, currency, rate, outstanding), outstanding)
    }

    /** Correct a mistake: a separate, audited entry that cancels one collection in full. */
    fun reverse(command: ReverseCollectionCommand): CollectionResult =
        transactionRunner.runInTransaction {
            val booking = lockOffice(command.bookingId)
            if (booking is Locked.Failure) return@runInTransaction booking.result
            booking as Locked.Ok

            val entries = collectionRepository.findByBooking(booking.booking.id)
            collectionRepository.findByRequest(command.actorUserId, command.clientRequestId)?.let { existing ->
                val same =
                    existing.bookingId == booking.booking.id &&
                        existing.kind == OfficeCollectionKind.REVERSAL &&
                        existing.reversesCollectionId == command.collectionId
                return@runInTransaction if (same) {
                    CollectionResult.Replayed(existing, summary(booking.booking, entries))
                } else {
                    CollectionResult.IdempotencyKeyReused
                }
            }

            val target =
                entries.firstOrNull { it.id == command.collectionId }
                    ?: return@runInTransaction CollectionResult.CollectionNotFound
            if (target.kind != OfficeCollectionKind.COLLECTION) return@runInTransaction CollectionResult.NotReversible
            if (target.recordedByUserId == command.actorUserId) return@runInTransaction CollectionResult.CannotReverseOwn
            if (entries.any { it.reversesCollectionId == target.id }) return@runInTransaction CollectionResult.AlreadyReversed
            // Money already handed back can never exceed what stays collected (both under the booking lock).
            val refunded =
                refundRepository?.findByBooking(booking.booking.id)?.let {
                    com.wego.toursoperator.domain.OfficeRefund
                        .netRefunded(it)
                }
                    ?: BigDecimal.ZERO
            if (OfficePaymentSummary.netCollected(entries).subtract(target.amount.amount) < refunded) {
                return@runInTransaction CollectionResult.RefundsExceedCollected
            }
            // Re-check F3: EGP lots have their own rates, so for an EGP collection the EGP itself must also stay covered:
            // the EGP still collected after this reversal must be at least the EGP already refunded.
            if (target.currencyPaid == PaidCurrency.EGP) {
                val egpRefunded = egpNet(refundRepository?.findByBooking(booking.booking.id).orEmpty())
                val reversedIds = entries.mapNotNull { it.reversesCollectionId }.toSet()
                val egpCollected =
                    entries
                        .filter {
                            it.kind == OfficeCollectionKind.COLLECTION &&
                                it.currencyPaid == PaidCurrency.EGP &&
                                it.id !in reversedIds
                        }.fold(BigDecimal.ZERO) { acc, e -> acc.add(e.amountPaid.amount) }
                if (egpCollected.subtract(target.amountPaid.amount) < egpRefunded) {
                    return@runInTransaction CollectionResult.RefundsExceedCollected
                }
            }
            // A reversal takes cash out of today's box (it is dated today, whatever day the original was).
            if (target.method.isCash && !cashDayGate.lockOpenToday(target.currencyPaid)) {
                return@runInTransaction CollectionResult.CashDayClosed
            }

            val entry =
                OfficeCollection(
                    id = UUID.randomUUID(),
                    bookingId = booking.booking.id,
                    kind = OfficeCollectionKind.REVERSAL,
                    method = target.method,
                    amount = target.amount,
                    currencyPaid = target.currencyPaid,
                    amountPaid = target.amountPaid,
                    fxRate = target.fxRate,
                    reference = null,
                    reversesCollectionId = target.id,
                    reason = command.reason.trim(),
                    recordedByUserId = command.actorUserId,
                    clientRequestId = command.clientRequestId,
                    recordedAt = Instant.now(clock),
                )
            collectionRepository.append(entry)
            CollectionResult.Recorded(entry, summary(booking.booking, entries + entry))
        }

    fun entries(bookingId: BookingId): List<OfficeCollection> = collectionRepository.findByBooking(bookingId)

    /** Net cash collected per booking, for response projections of several bookings at once. */
    fun netCollected(bookingIds: Collection<BookingId>): Map<BookingId, BigDecimal> = collectionRepository.netCollected(bookingIds)

    /** Net EUR returned on cancelled office bookings (OPS2-F refunds), for the same projections. */
    fun netRefunded(bookingIds: Collection<BookingId>): Map<BookingId, BigDecimal> = refundRepository?.netRefunded(bookingIds).orEmpty()

    /** EGP handed back in EGP refunds, net of their reversals. */
    private fun egpNet(refunds: List<com.wego.toursoperator.domain.OfficeRefund>): BigDecimal {
        val reversed = refunds.mapNotNull { it.reversesRefundId }.toSet()
        return refunds
            .filter {
                it.kind == com.wego.toursoperator.domain.OfficeRefundKind.REFUND &&
                    it.currencyPaid == PaidCurrency.EGP &&
                    it.id !in reversed
            }.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.amountPaid.amount) }
    }

    private sealed interface Locked {
        data class Ok(
            val booking: Booking,
        ) : Locked

        data class Failure(
            val result: CollectionResult,
        ) : Locked
    }

    private fun lockOffice(id: BookingId): Locked {
        val booking = bookingRepository.findByIdForUpdate(id) ?: return Locked.Failure(CollectionResult.BookingNotFound)
        if (booking.channel != BookingChannel.OFFICE) return Locked.Failure(CollectionResult.NotAnOfficeBooking)
        return Locked.Ok(booking)
    }

    private fun summary(
        booking: Booking,
        entries: List<OfficeCollection>,
    ): OfficePaymentSummary = OfficePaymentSummary.of(booking.pricing.totalEur, entries)
}
