package com.wego.toursoperator.application

import com.wego.toursoperator.domain.CashBoxEvent
import com.wego.toursoperator.domain.CashBoxEventKind
import com.wego.toursoperator.domain.CashDayState
import com.wego.toursoperator.domain.CashFlow
import com.wego.toursoperator.domain.FinanceAmount
import com.wego.toursoperator.domain.PaidCurrency
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Every cash entry (office cash collection, cash refund, cash settlement
 * payment, and their reversals) asks the gate inside its own transaction. The
 * gate takes the cash-box lock of today and the currency, so it serialises with
 * a manager confirming that day: once a day is closed no cash entry lands on it.
 */
fun interface CashDayGate {
    /** True when today's box for [currency] is open for cash; locks it for the rest of the transaction. */
    fun lockOpenToday(currency: PaidCurrency): Boolean

    companion object {
        val NONE = CashDayGate { true }
    }
}

data class CashDayView(
    val date: LocalDate,
    val currency: PaidCurrency,
    val state: CashDayState,
    val flow: CashFlow,
    val events: List<CashBoxEvent>,
)

/**
 * Daily office cash box per currency (OPS2-F; owner-delegated default: reception
 * counts at the end of the day and hands over to the manager). COUNT by
 * `tours-operator.cash-box:close`; CONFIRM (closes the day) and REOPEN (with a
 * reason) by `tours-operator.cash-box:confirm`, never by the person who counted.
 * The drawer is assumed emptied to the manager every day, so a day starts at zero.
 */
class CashBoxService(
    private val repository: CashBoxRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) : CashDayGate {
    fun today(): LocalDate = FxRateService.todayInSharm(clock)

    fun day(
        date: LocalDate,
        currency: PaidCurrency,
    ): CashDayView {
        val events = repository.events(date, currency)
        return CashDayView(date, currency, CashBoxEvent.state(events), repository.flow(date, currency), events)
    }

    fun recent(days: Int): List<CashBoxEvent> = repository.recentDays(days)

    override fun lockOpenToday(currency: PaidCurrency): Boolean {
        val date = today()
        repository.lockDay(date, currency)
        return CashBoxEvent.state(repository.events(date, currency)) != CashDayState.CLOSED
    }

    fun count(
        date: LocalDate,
        currency: PaidCurrency,
        counted: BigDecimal,
        note: String?,
        actorUserId: UUID,
    ): FinanceResult<CashDayView> =
        transactionRunner.runInTransaction {
            if (date.isAfter(today())) return@runInTransaction invalid("cash_day_in_future")
            val amount = FinanceAmount.nonNegative(counted)
            repository.lockDay(date, currency)
            val events = repository.events(date, currency)
            if (CashBoxEvent.state(events) == CashDayState.CLOSED) return@runInTransaction conflict("cash_day_closed")
            val expected = repository.flow(date, currency).expected
            repository.append(
                CashBoxEvent(
                    id = UUID.randomUUID(),
                    businessDate = date,
                    currency = currency,
                    sequence = next(events),
                    kind = CashBoxEventKind.COUNT,
                    expected = expected,
                    counted = amount,
                    difference = amount.subtract(expected),
                    note = FinanceAmount.text(note, "note", 500),
                    countEventId = null,
                    actorUserId = actorUserId,
                    occurredAt = Instant.now(clock),
                ),
            )
            FinanceResult.Ok(day(date, currency))
        }

    fun confirm(
        date: LocalDate,
        currency: PaidCurrency,
        actorUserId: UUID,
    ): FinanceResult<CashDayView> =
        transactionRunner.runInTransaction {
            repository.lockDay(date, currency)
            val events = repository.events(date, currency)
            when (CashBoxEvent.state(events)) {
                CashDayState.OPEN -> return@runInTransaction conflict("cash_day_not_counted")
                CashDayState.CLOSED -> return@runInTransaction conflict("cash_day_closed")
                CashDayState.COUNTED -> Unit
            }
            val count = events.maxBy { it.sequence }
            if (count.actorUserId == actorUserId) return@runInTransaction forbidden("cannot_confirm_own_count")
            val expected = repository.flow(date, currency).expected
            // Cash recorded after the count changes what the box should hold: count again.
            if (expected.compareTo(count.expected) != 0) {
                return@runInTransaction conflict("cash_expected_changed", mapOf("expected" to expected.toPlainString()))
            }
            repository.append(
                CashBoxEvent(
                    id = UUID.randomUUID(),
                    businessDate = date,
                    currency = currency,
                    sequence = next(events),
                    kind = CashBoxEventKind.CONFIRM,
                    expected = expected,
                    counted = null,
                    difference = null,
                    note = null,
                    countEventId = count.id,
                    actorUserId = actorUserId,
                    occurredAt = Instant.now(clock),
                ),
            )
            FinanceResult.Ok(day(date, currency))
        }

    fun reopen(
        date: LocalDate,
        currency: PaidCurrency,
        reason: String,
        actorUserId: UUID,
    ): FinanceResult<CashDayView> =
        transactionRunner.runInTransaction {
            val why = checkNotNull(FinanceAmount.text(reason, "reason", 500, required = true))
            repository.lockDay(date, currency)
            val events = repository.events(date, currency)
            if (CashBoxEvent.state(events) != CashDayState.CLOSED) return@runInTransaction conflict("cash_day_not_closed")
            repository.append(
                CashBoxEvent(
                    id = UUID.randomUUID(),
                    businessDate = date,
                    currency = currency,
                    sequence = next(events),
                    kind = CashBoxEventKind.REOPEN,
                    expected = null,
                    counted = null,
                    difference = null,
                    note = why,
                    countEventId = null,
                    actorUserId = actorUserId,
                    occurredAt = Instant.now(clock),
                ),
            )
            FinanceResult.Ok(day(date, currency))
        }

    private fun next(events: List<CashBoxEvent>): Int = (events.maxOfOrNull { it.sequence } ?: 0) + 1
}
