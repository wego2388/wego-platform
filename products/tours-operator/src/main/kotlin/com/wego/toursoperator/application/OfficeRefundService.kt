package com.wego.toursoperator.application

import com.wego.toursoperator.domain.Booking
import com.wego.toursoperator.domain.BookingChannel
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.BookingStatus
import com.wego.toursoperator.domain.CollectionReference
import com.wego.toursoperator.domain.EgpRefundRule
import com.wego.toursoperator.domain.FinanceAmount
import com.wego.toursoperator.domain.FxRate
import com.wego.toursoperator.domain.Money
import com.wego.toursoperator.domain.OfficePaymentSummary
import com.wego.toursoperator.domain.OfficeRefund
import com.wego.toursoperator.domain.OfficeRefundKind
import com.wego.toursoperator.domain.PaidCurrency
import com.wego.toursoperator.domain.RefundMethod
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class RecordRefundCommand(
    val bookingId: BookingId,
    val method: RefundMethod,
    val amountPaid: BigDecimal,
    val currency: PaidCurrency,
    val reference: String?,
    val fxRateId: UUID?,
    val reason: String,
    val actorUserId: UUID,
    val clientRequestId: UUID,
)

data class ReverseRefundCommand(
    val bookingId: BookingId,
    val refundId: UUID,
    val reason: String,
    val actorUserId: UUID,
    val clientRequestId: UUID,
)

/** Money a cancelled office booking still holds: collected (net) minus returned (net). */
data class RefundPosition(
    val collected: BigDecimal,
    val refunded: BigDecimal,
) {
    val refundable: BigDecimal get() = collected.subtract(refunded).max(BigDecimal.ZERO.setScale(2))
}

data class RefundOutcome(
    val entry: OfficeRefund,
    val position: RefundPosition,
    /** TODAY_RATE_USED when part of an EGP refund had no EGP collection to follow and used today's rate. */
    val warning: String? = null,
)

/**
 * Money returned on a cancelled office booking (OPS2-F; owner-delegated default:
 * cash from the office or the same method, after manager approval — recording
 * needs `tours-operator.booking:refund-office`). Never more than what was
 * collected, under the booking row lock. It never touches Paymob, the
 * collections ledger or the booking itself. A cash refund takes cash out of
 * today's box, so it is refused once today's box is closed.
 */
class OfficeRefundService(
    private val bookings: BookingRepository,
    private val collections: OfficeCollectionRepository,
    private val refunds: OfficeRefundRepository,
    private val fxRates: FxRateRepository,
    private val cashDayGate: CashDayGate,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun entries(bookingId: BookingId): List<OfficeRefund> = refunds.findByBooking(bookingId)

    fun position(bookingId: BookingId): RefundPosition =
        RefundPosition(
            OfficePaymentSummary.netCollected(collections.findByBooking(bookingId)).setScale(2),
            OfficeRefund.netRefunded(refunds.findByBooking(bookingId)),
        )

    fun record(command: RecordRefundCommand): FinanceResult<RefundOutcome> =
        transactionRunner.runInTransaction {
            val booking = lock(command.bookingId) ?: return@runInTransaction notFound()
            if (booking.channel != BookingChannel.OFFICE) return@runInTransaction conflict("not_an_office_booking")
            val reference = command.reference?.let(CollectionReference::of)
            refunds.findByRequest(command.actorUserId, command.clientRequestId)?.let { existing ->
                val same =
                    existing.bookingId == booking.id &&
                        existing.kind == OfficeRefundKind.REFUND &&
                        existing.method == command.method &&
                        existing.currencyPaid == command.currency &&
                        existing.amountPaid.amount.compareTo(command.amountPaid) == 0 &&
                        existing.reference == reference
                return@runInTransaction if (same) {
                    FinanceResult.Ok(RefundOutcome(existing, position(booking.id)), created = false)
                } else {
                    conflict("idempotency_key_reused")
                }
            }
            if (booking.status != BookingStatus.CANCELLED) return@runInTransaction conflict("booking_not_cancelled")
            if (!command.method.isCash && reference == null) return@runInTransaction invalid("reference_required")
            if (command.method.isCash && reference != null) return@runInTransaction invalid("reference_not_allowed")
            val reason = checkNotNull(FinanceAmount.text(command.reason, "reason", 500, required = true))
            val amountPaid = Money(FinanceAmount.positive(command.amountPaid))

            val before = position(booking.id)
            if (before.refundable.signum() <= 0) return@runInTransaction conflict("nothing_to_refund")
            val exceeds = conflict("amount_exceeds_refundable", mapOf("refundable" to before.refundable.toPlainString()))
            var warning: String? = null
            val settledEur: Money
            val settledRate: FxRate?
            if (command.currency == PaidCurrency.EUR) {
                if (amountPaid.amount > before.refundable) return@runInTransaction exceeds
                settledEur = amountPaid
                settledRate = null
            } else {
                // Review L1: EGP goes back at the rate(s) the customer paid at, FIFO; only the excess uses today's rate.
                val lots = EgpRefundRule.remainingLots(collections.findByBooking(booking.id), refunds.findByBooking(booking.id))
                val conversion = EgpRefundRule.convert(lots, amountPaid.amount)
                var eur = conversion.eurFromLots
                var anchor = conversion.firstRate
                if (conversion.excessEgp.signum() > 0) {
                    val today = fxRates.latestFor(FxRateService.todayInSharm(clock)) ?: return@runInTransaction conflict("fx_rate_not_set")
                    if (command.fxRateId == null) return@runInTransaction invalid("fx_rate_id_required")
                    if (command.fxRateId != today.id) return@runInTransaction conflict("fx_rate_changed")
                    val excessEur = conversion.excessEgp.divide(today.egpPerEur, 2, RoundingMode.HALF_UP)
                    // Never hand back EGP that is worth nothing in EUR (a rounding gift).
                    if (excessEur.signum() <= 0) return@runInTransaction invalid("amount_below_minimum")
                    eur = eur.add(excessEur)
                    anchor = anchor ?: today
                    warning = "TODAY_RATE_USED"
                }
                if (eur.signum() <= 0) return@runInTransaction invalid("amount_below_minimum")
                if (eur > before.refundable) return@runInTransaction exceeds
                val blended = amountPaid.amount.divide(eur, FxRate.RATE_SCALE, RoundingMode.HALF_UP)
                if (blended < FxRate.MIN_RATE || blended > FxRate.MAX_RATE) return@runInTransaction invalid("amount_below_minimum")
                settledEur = Money(eur)
                settledRate = FxRate(checkNotNull(anchor).id, anchor.rateDate, blended, anchor.setByUserId, anchor.setAt)
            }
            if (command.method.isCash && !cashDayGate.lockOpenToday(command.currency)) return@runInTransaction conflict("cash_day_closed")

            val entry =
                OfficeRefund(
                    id = UUID.randomUUID(),
                    bookingId = booking.id,
                    kind = OfficeRefundKind.REFUND,
                    method = command.method,
                    amount = settledEur,
                    currencyPaid = command.currency,
                    amountPaid = amountPaid,
                    fxRate = settledRate,
                    reference = reference,
                    reason = reason,
                    reversesRefundId = null,
                    recordedByUserId = command.actorUserId,
                    clientRequestId = command.clientRequestId,
                    recordedAt = Instant.now(clock),
                )
            refunds.append(entry)
            FinanceResult.Ok(RefundOutcome(entry, position(booking.id), warning))
        }

    /** Cancels one refund in full. Never by the person who recorded it. */
    fun reverse(command: ReverseRefundCommand): FinanceResult<RefundOutcome> =
        transactionRunner.runInTransaction {
            val booking = lock(command.bookingId) ?: return@runInTransaction notFound()
            refunds.findByRequest(command.actorUserId, command.clientRequestId)?.let { existing ->
                return@runInTransaction if (existing.bookingId == booking.id && existing.reversesRefundId == command.refundId) {
                    FinanceResult.Ok(RefundOutcome(existing, position(booking.id)), created = false)
                } else {
                    conflict("idempotency_key_reused")
                }
            }
            val entries = refunds.findByBooking(booking.id)
            val target = entries.firstOrNull { it.id == command.refundId } ?: return@runInTransaction notFound()
            if (target.kind != OfficeRefundKind.REFUND) return@runInTransaction conflict("refund_not_reversible")
            if (entries.any { it.reversesRefundId == target.id }) return@runInTransaction conflict("refund_already_reversed")
            if (target.recordedByUserId == command.actorUserId) return@runInTransaction forbidden("cannot_reverse_own_refund")
            val reason = checkNotNull(FinanceAmount.text(command.reason, "reason", 500, required = true))
            if (target.method.isCash && !cashDayGate.lockOpenToday(target.currencyPaid)) return@runInTransaction conflict("cash_day_closed")
            val entry =
                target.copy(
                    id = UUID.randomUUID(),
                    kind = OfficeRefundKind.REVERSAL,
                    reference = null,
                    reason = reason,
                    reversesRefundId = target.id,
                    recordedByUserId = command.actorUserId,
                    clientRequestId = command.clientRequestId,
                    recordedAt = Instant.now(clock),
                    recordedByEmail = null,
                )
            refunds.append(entry)
            FinanceResult.Ok(RefundOutcome(entry, position(booking.id)))
        }

    private fun lock(id: BookingId): Booking? = bookings.findByIdForUpdate(id)
}
