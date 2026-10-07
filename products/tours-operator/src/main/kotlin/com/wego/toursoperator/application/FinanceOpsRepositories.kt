package com.wego.toursoperator.application

import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.CashBoxEvent
import com.wego.toursoperator.domain.CashFlow
import com.wego.toursoperator.domain.CostComponent
import com.wego.toursoperator.domain.OfficeRefund
import com.wego.toursoperator.domain.PaidCurrency
import com.wego.toursoperator.domain.PartyRef
import com.wego.toursoperator.domain.PayableAdjustment
import com.wego.toursoperator.domain.PayableDeparture
import com.wego.toursoperator.domain.ProfitBooking
import com.wego.toursoperator.domain.SettlementApproval
import com.wego.toursoperator.domain.SettlementPayment
import com.wego.toursoperator.domain.TourSlotId
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Cost components are never edited; the only change is ending an open row once. */
interface CostComponentRepository {
    fun findAll(): List<CostComponent>

    fun findById(id: UUID): CostComponent?

    /** Row-locks the component for the rest of the transaction. */
    fun findByIdForUpdate(id: UUID): CostComponent?

    fun append(component: CostComponent)

    fun findByRequest(
        actorUserId: UUID,
        clientRequestId: UUID,
    ): CostComponent?

    /** Serialises cost changes of one tour or driver for the rest of the transaction. */
    fun lockOwner(ownerId: UUID)

    fun end(
        id: UUID,
        validUntil: LocalDate,
        endedByUserId: UUID,
        endedAt: Instant,
    )
}

/** Append-only: there is intentionally no update or delete. */
interface OfficeRefundRepository {
    fun findByBooking(bookingId: BookingId): List<OfficeRefund>

    fun findByRequest(
        actorUserId: UUID,
        clientRequestId: UUID,
    ): OfficeRefund?

    /** Net EUR returned (refunds minus reversals) per booking; bookings with none are absent. */
    fun netRefunded(bookingIds: Collection<BookingId>): Map<BookingId, BigDecimal>

    fun append(refund: OfficeRefund)
}

/** Append-only ledgers of payable adjustments, owner approvals and settlement payments. */
interface SettlementRepository {
    /** Serialises every write for one supplier or driver for the rest of the transaction. */
    fun lockParty(party: PartyRef)

    /** All adjustments of [party], or of every party when null. */
    fun adjustments(party: PartyRef?): List<PayableAdjustment>

    fun adjustmentsForSlots(slotIds: Collection<TourSlotId>): List<PayableAdjustment>

    fun findAdjustmentByRequest(
        actorUserId: UUID,
        clientRequestId: UUID,
    ): PayableAdjustment?

    fun appendAdjustment(adjustment: PayableAdjustment)

    fun payments(party: PartyRef?): List<SettlementPayment>

    fun findPaymentByRequest(
        actorUserId: UUID,
        clientRequestId: UUID,
    ): SettlementPayment?

    fun appendPayment(payment: SettlementPayment)

    fun approvals(party: PartyRef): List<SettlementApproval>

    fun findApproval(id: UUID): SettlementApproval?

    fun findApprovalByRequest(
        actorUserId: UUID,
        clientRequestId: UUID,
    ): SettlementApproval?

    fun appendApproval(approval: SettlementApproval)
}

/** Append-only cash-box events plus the day's cash flows read from the three ledgers. */
interface CashBoxRepository {
    /** Serialises closing and every cash entry of one Cairo day and currency. */
    fun lockDay(
        date: LocalDate,
        currency: PaidCurrency,
    )

    fun events(
        date: LocalDate,
        currency: PaidCurrency,
    ): List<CashBoxEvent>

    fun append(event: CashBoxEvent)

    fun flow(
        date: LocalDate,
        currency: PaidCurrency,
    ): CashFlow

    /** Latest event per (day, currency) for the [days] most recent days that have any event. */
    fun recentDays(days: Int): List<CashBoxEvent>
}

/** Totals of office money over a period, by the Cairo day each entry was recorded. */
data class OfficeMoneySummary(
    val collectedEur: BigDecimal,
    val collectionsReversedEur: BigDecimal,
    val refundedEur: BigDecimal,
    val refundsReversedEur: BigDecimal,
    val collectionCount: Int,
    val refundCount: Int,
) {
    val netEur: BigDecimal get() = collectedEur.subtract(collectionsReversedEur).subtract(refundedEur.subtract(refundsReversedEur))
}

/** Bulk reads for reports; they never lock and never write. */
interface FinanceReadRepository {
    /** Confirmed, completed and cancelled bookings whose tour day is in the range, with their EUR revenue. */
    fun profitBookings(
        from: LocalDate,
        to: LocalDate,
    ): List<ProfitBooking>

    /** Driver assigned to each departure, for the given departures. */
    fun drivers(slotIds: Collection<TourSlotId>): Map<TourSlotId, UUID?>

    /** The driver and suppliers assigned to a departure. */
    fun slotParties(slotId: TourSlotId): Set<PartyRef>

    /** Departures up to and including [to] that have live bookings, with their driver and suppliers. */
    fun payableDepartures(to: LocalDate): List<PayableDeparture>

    fun officeMoney(
        fromInclusive: Instant,
        toExclusive: Instant,
    ): OfficeMoneySummary

    /** The latest manager-set rate for each day of the range that has one. */
    fun ratesBetween(
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, BigDecimal>

    /** The most recently set rate, whatever its day. */
    fun latestRate(): BigDecimal?
}
