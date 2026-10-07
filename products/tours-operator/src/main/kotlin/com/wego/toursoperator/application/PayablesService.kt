package com.wego.toursoperator.application

import com.wego.toursoperator.domain.AdjustmentKind
import com.wego.toursoperator.domain.CollectionReference
import com.wego.toursoperator.domain.CurrencyBalance
import com.wego.toursoperator.domain.FinanceAmount
import com.wego.toursoperator.domain.OwedIssue
import com.wego.toursoperator.domain.PaidCurrency
import com.wego.toursoperator.domain.PartyRef
import com.wego.toursoperator.domain.PartyType
import com.wego.toursoperator.domain.PayableAdjustment
import com.wego.toursoperator.domain.PayableDeparture
import com.wego.toursoperator.domain.PayableMovement
import com.wego.toursoperator.domain.PayablesCalculator
import com.wego.toursoperator.domain.SettlementApproval
import com.wego.toursoperator.domain.SettlementMethod
import com.wego.toursoperator.domain.SettlementPayment
import com.wego.toursoperator.domain.SettlementPaymentKind
import com.wego.toursoperator.domain.SettlementPolicy
import com.wego.toursoperator.domain.TourSlotId
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

data class PartyInfo(
    val party: PartyRef,
    val name: String,
    val code: String?,
    val active: Boolean,
)

data class PartySummary(
    val info: PartyInfo,
    /** All-time position up to today, per currency. */
    val balances: List<CurrencyBalance>,
    val openIssues: Int,
    val pendingApprovals: Int,
)

data class PartyStatement(
    val info: PartyInfo,
    val from: LocalDate?,
    val to: LocalDate,
    val balances: List<CurrencyBalance>,
    val movements: List<PayableMovement>,
    val issues: List<OwedIssue>,
    val approvals: List<SettlementApproval>,
    /** All-time position up to today, per currency (what a payment is checked against). */
    val current: List<CurrencyBalance>,
    /** Today's (Cairo) non-reversed payments to the party in EGP, counted against the daily manager limit; null = unknown. */
    val paidTodayEgp: BigDecimal? = null,
)

data class PayCommand(
    val party: PartyRef,
    val method: SettlementMethod,
    val currency: PaidCurrency,
    val amount: BigDecimal,
    val reference: String?,
    val note: String?,
    val approvalId: UUID?,
    val actorUserId: UUID,
    val clientRequestId: UUID,
)

data class ApproveCommand(
    val party: PartyRef,
    val currency: PaidCurrency,
    val amount: BigDecimal,
    val note: String?,
    val actorUserId: UUID,
    val clientRequestId: UUID,
)

data class AdjustCommand(
    val party: PartyRef,
    val slotId: TourSlotId?,
    val serviceDate: LocalDate?,
    val kind: AdjustmentKind,
    val currency: PaidCurrency,
    val amount: BigDecimal,
    val reason: String,
    val actorUserId: UUID,
    val clientRequestId: UUID,
    /** The actor holds tours-operator.settlement:approve (needed for a charge above 5000 EGP). */
    val actorCanApprove: Boolean = false,
)

data class ReverseEntryCommand(
    val party: PartyRef,
    val entryId: UUID,
    val reason: String,
    val actorUserId: UUID,
    val clientRequestId: UUID,
)

/**
 * Supplier and driver payables (OPS2-F). What is owed is derived from
 * departures that ran (today or earlier) with live bookings, plus documented
 * adjustments; what was paid is the settlement ledger. Every write takes the
 * party lock, so two payments can never together exceed the balance and an
 * approval pays exactly once. A manager (`settlement:pay`) pays up to
 * 5000 EGP per payment; above that the payment must consume an owner
 * approval (`settlement:approve`). Reversals need `settlement:approve`.
 */
class PayablesService(
    private val settlements: SettlementRepository,
    private val reads: FinanceReadRepository,
    private val costs: CostComponentRepository,
    private val suppliers: SupplierRepository,
    private val drivers: DriverRepository,
    private val slots: TourSlotRepository,
    private val fxRates: FxRateRepository,
    private val cashDayGate: CashDayGate,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun today(): LocalDate = FxRateService.todayInSharm(clock)

    fun party(ref: PartyRef): PartyInfo? =
        when (ref.type) {
            PartyType.SUPPLIER -> suppliers.findById(ref.id)?.let { PartyInfo(ref, it.name, it.code, it.active) }
            PartyType.DRIVER -> drivers.findById(ref.id)?.let { PartyInfo(ref, it.name, null, it.active) }
        }

    fun overview(): List<PartySummary> {
        val departures = reads.payableDepartures(today())
        val components = costs.findAll()
        val adjustments = settlements.adjustments(null).groupBy { it.party }
        val payments = settlements.payments(null).groupBy { it.party }
        val parties =
            suppliers.findAll(null).map { PartyInfo(PartyRef(PartyType.SUPPLIER, it.id), it.name, it.code, it.active) } +
                drivers.findAll(null).map { PartyInfo(PartyRef(PartyType.DRIVER, it.id), it.name, null, it.active) }
        return parties.mapNotNull { info ->
            val partyAdjustments = adjustments[info.party].orEmpty()
            val (owed, issues) = PayablesCalculator.derive(info.party, departures, components, partyAdjustments)
            val movements = PayablesCalculator.movements(owed, partyAdjustments, payments[info.party].orEmpty(), ::cairoDay)
            val balances = PayablesCalculator.balances(movements, null, today())
            val pending = settlements.approvals(info.party).count { it.usedByPaymentId == null }
            if (!info.active && balances.all { it.closing.signum() == 0 } && issues.isEmpty() && pending == 0) {
                null
            } else {
                PartySummary(info, balances, issues.size, pending)
            }
        }
    }

    fun statement(
        ref: PartyRef,
        from: LocalDate,
        to: LocalDate,
    ): PartyStatement? {
        val info = party(ref) ?: return null
        val (movements, issues) = movements(ref, maxOf(to, today()))
        return PartyStatement(
            info = info,
            from = from,
            to = to,
            balances = PayablesCalculator.balances(movements, from, to),
            movements = movements.filter { !it.date.isBefore(from) && !it.date.isAfter(to) },
            issues = issues.filter { !it.date.isBefore(from) && !it.date.isAfter(to) },
            approvals = settlements.approvals(ref),
            current = PayablesCalculator.balances(movements, null, today()),
            paidTodayEgp = paidTodayEgp(ref),
        )
    }

    fun pay(command: PayCommand): FinanceResult<SettlementPayment> =
        transactionRunner.runInTransaction {
            party(command.party) ?: return@runInTransaction notFound("party_not_found")
            settlements.lockParty(command.party)
            settlements.findPaymentByRequest(command.actorUserId, command.clientRequestId)?.let { existing ->
                val same =
                    existing.party == command.party &&
                        existing.kind == SettlementPaymentKind.PAYMENT &&
                        existing.method == command.method &&
                        existing.currency == command.currency &&
                        existing.amount.compareTo(command.amount) == 0 &&
                        existing.approvalId == command.approvalId
                return@runInTransaction if (same) FinanceResult.Ok(existing, created = false) else conflict("idempotency_key_reused")
            }
            val reference =
                command.reference
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(CollectionReference::of)
            if (!command.method.isCash && reference == null) return@runInTransaction invalid("reference_required")
            val amount = FinanceAmount.positive(command.amount)
            val note = FinanceAmount.text(command.note, "note", 500)

            val rate = if (command.currency == PaidCurrency.EUR) fxRates.latestFor(today()) else null
            val trusted = command.currency == PaidCurrency.EGP || rateTrusted()
            val egp = SettlementPolicy.egpEquivalent(command.currency, amount, rate)
            // Review M2: the 5000 EGP manager limit is per party per Cairo day (today's payments + this one).
            val paidToday = paidTodayEgp(command.party)
            if (command.approvalId == null && SettlementPolicy.dailyNeedsApproval(paidToday, egp, command.currency, trusted)) {
                return@runInTransaction conflict(
                    "approval_required",
                    mapOf(
                        "limitEgp" to SettlementPolicy.MANAGER_LIMIT_EGP.toPlainString(),
                        "paidTodayEgp" to (paidToday?.toPlainString() ?: "unknown"),
                    ),
                )
            }
            command.approvalId?.let { approvalId ->
                val approval = settlements.findApproval(approvalId) ?: return@runInTransaction invalid("approval_not_found")
                if (approval.party != command.party || approval.currency != command.currency || approval.amount.compareTo(amount) != 0) {
                    return@runInTransaction conflict("approval_mismatch")
                }
                // Review L2: four eyes — whoever approved a payment cannot also record it.
                if (approval.approvedByUserId == command.actorUserId) return@runInTransaction forbidden("approver_cannot_pay")
                if (approval.usedByPaymentId != null) return@runInTransaction conflict("approval_already_used")
            }
            val balance = balance(command.party, command.currency)
            if (amount > balance) return@runInTransaction conflict("amount_exceeds_balance", mapOf("balance" to balance.toPlainString()))
            if (command.method.isCash && !cashDayGate.lockOpenToday(command.currency)) return@runInTransaction conflict("cash_day_closed")

            val payment =
                SettlementPayment(
                    id = UUID.randomUUID(),
                    party = command.party,
                    kind = SettlementPaymentKind.PAYMENT,
                    method = command.method,
                    currency = command.currency,
                    amount = amount,
                    egpEquivalent = egp,
                    fxRateId = rate?.id,
                    reference = reference,
                    note = note,
                    approvalId = command.approvalId,
                    reversesPaymentId = null,
                    reason = null,
                    recordedByUserId = command.actorUserId,
                    clientRequestId = command.clientRequestId,
                    recordedAt = Instant.now(clock),
                )
            settlements.appendPayment(payment)
            FinanceResult.Ok(payment)
        }

    fun approve(command: ApproveCommand): FinanceResult<SettlementApproval> =
        transactionRunner.runInTransaction {
            party(command.party) ?: return@runInTransaction notFound("party_not_found")
            settlements.lockParty(command.party)
            settlements.findApprovalByRequest(command.actorUserId, command.clientRequestId)?.let { existing ->
                val same =
                    existing.party == command.party &&
                        existing.currency == command.currency &&
                        existing.amount.compareTo(command.amount) == 0
                return@runInTransaction if (same) FinanceResult.Ok(existing, created = false) else conflict("idempotency_key_reused")
            }
            val amount = FinanceAmount.positive(command.amount)
            val balance = balance(command.party, command.currency)
            if (amount > balance) return@runInTransaction conflict("amount_exceeds_balance", mapOf("balance" to balance.toPlainString()))
            val approval =
                SettlementApproval(
                    id = UUID.randomUUID(),
                    party = command.party,
                    currency = command.currency,
                    amount = amount,
                    note = FinanceAmount.text(command.note, "note", 500),
                    approvedByUserId = command.actorUserId,
                    clientRequestId = command.clientRequestId,
                    approvedAt = Instant.now(clock),
                )
            settlements.appendApproval(approval)
            FinanceResult.Ok(approval)
        }

    fun adjust(command: AdjustCommand): FinanceResult<PayableAdjustment> =
        transactionRunner.runInTransaction {
            if (command.kind == AdjustmentKind.REVERSAL) return@runInTransaction invalid("invalid_adjustment_kind")
            party(command.party) ?: return@runInTransaction notFound("party_not_found")
            val slot = command.slotId?.let { slots.findById(it) ?: return@runInTransaction invalid("slot_not_found") }
            val serviceDate = slot?.date ?: command.serviceDate ?: return@runInTransaction invalid("service_date_required")
            if (serviceDate.isAfter(today())) return@runInTransaction invalid("service_date_in_future")
            // Review M2/L6: an adjustment named to a departure must be for a party that served it.
            if (slot != null && command.party !in reads.slotParties(slot.id)) return@runInTransaction invalid("slot_not_served_by_party")
            if (command.kind == AdjustmentKind.CHARGE && !command.actorCanApprove) {
                val amount = FinanceAmount.positive(command.amount)
                val trusted = command.currency == PaidCurrency.EGP || rateTrusted()
                val egp = if (trusted) SettlementPolicy.egpEquivalent(command.currency, amount, fxRates.latestFor(today())) else null
                if (egp == null || egp > SettlementPolicy.MANAGER_LIMIT_EGP) return@runInTransaction forbidden("charge_needs_approval")
            }
            settlements.lockParty(command.party)
            settlements.findAdjustmentByRequest(command.actorUserId, command.clientRequestId)?.let { existing ->
                val same =
                    existing.party == command.party &&
                        existing.kind == command.kind &&
                        existing.currency == command.currency &&
                        existing.amount.compareTo(command.amount) == 0 &&
                        existing.slotId == command.slotId
                return@runInTransaction if (same) FinanceResult.Ok(existing, created = false) else conflict("idempotency_key_reused")
            }
            val adjustment =
                PayableAdjustment(
                    id = UUID.randomUUID(),
                    party = command.party,
                    slotId = slot?.id,
                    serviceDate = serviceDate,
                    kind = command.kind,
                    currency = command.currency,
                    amount = FinanceAmount.positive(command.amount),
                    reason = checkNotNull(FinanceAmount.text(command.reason, "reason", 500, required = true)),
                    reversesAdjustmentId = null,
                    recordedByUserId = command.actorUserId,
                    clientRequestId = command.clientRequestId,
                    recordedAt = Instant.now(clock),
                )
            settlements.appendAdjustment(adjustment)
            FinanceResult.Ok(adjustment)
        }

    /** Cancels an adjustment in full; the reversal is dated today (history before it is unchanged). */
    fun reverseAdjustment(command: ReverseEntryCommand): FinanceResult<PayableAdjustment> =
        transactionRunner.runInTransaction {
            party(command.party) ?: return@runInTransaction notFound("party_not_found")
            settlements.lockParty(command.party)
            settlements.findAdjustmentByRequest(command.actorUserId, command.clientRequestId)?.let { existing ->
                return@runInTransaction if (existing.reversesAdjustmentId == command.entryId) {
                    FinanceResult.Ok(existing, created = false)
                } else {
                    conflict("idempotency_key_reused")
                }
            }
            val all = settlements.adjustments(command.party)
            val target = all.firstOrNull { it.id == command.entryId } ?: return@runInTransaction notFound()
            if (target.kind == AdjustmentKind.REVERSAL) return@runInTransaction conflict("entry_not_reversible")
            if (all.any { it.reversesAdjustmentId == target.id }) return@runInTransaction conflict("entry_already_reversed")
            val reversal =
                target.copy(
                    id = UUID.randomUUID(),
                    slotId = null,
                    serviceDate = today(),
                    kind = AdjustmentKind.REVERSAL,
                    reason = checkNotNull(FinanceAmount.text(command.reason, "reason", 500, required = true)),
                    reversesAdjustmentId = target.id,
                    recordedByUserId = command.actorUserId,
                    clientRequestId = command.clientRequestId,
                    recordedAt = Instant.now(clock),
                    recordedByEmail = null,
                )
            settlements.appendAdjustment(reversal)
            FinanceResult.Ok(reversal)
        }

    fun reversePayment(command: ReverseEntryCommand): FinanceResult<SettlementPayment> =
        transactionRunner.runInTransaction {
            party(command.party) ?: return@runInTransaction notFound("party_not_found")
            settlements.lockParty(command.party)
            settlements.findPaymentByRequest(command.actorUserId, command.clientRequestId)?.let { existing ->
                return@runInTransaction if (existing.reversesPaymentId == command.entryId) {
                    FinanceResult.Ok(existing, created = false)
                } else {
                    conflict("idempotency_key_reused")
                }
            }
            val all = settlements.payments(command.party)
            val target = all.firstOrNull { it.id == command.entryId } ?: return@runInTransaction notFound()
            if (target.kind != SettlementPaymentKind.PAYMENT) return@runInTransaction conflict("entry_not_reversible")
            if (all.any { it.reversesPaymentId == target.id }) return@runInTransaction conflict("entry_already_reversed")
            val reason = checkNotNull(FinanceAmount.text(command.reason, "reason", 500, required = true))
            // Reversing a cash payment puts the cash back in today's box.
            if (target.method.isCash && !cashDayGate.lockOpenToday(target.currency)) return@runInTransaction conflict("cash_day_closed")
            val reversal =
                target.copy(
                    id = UUID.randomUUID(),
                    kind = SettlementPaymentKind.REVERSAL,
                    reference = null,
                    note = null,
                    approvalId = null,
                    reversesPaymentId = target.id,
                    reason = reason,
                    recordedByUserId = command.actorUserId,
                    clientRequestId = command.clientRequestId,
                    recordedAt = Instant.now(clock),
                    recordedByEmail = null,
                )
            settlements.appendPayment(reversal)
            FinanceResult.Ok(reversal)
        }

    /** What we owe [party] in [currency] today (call under the party lock before paying). */
    fun balance(
        party: PartyRef,
        currency: PaidCurrency,
    ): BigDecimal {
        val (movements, _) = movements(party, today())
        return PayablesCalculator.balances(movements, null, today()).firstOrNull { it.currency == currency }?.closing
            ?: BigDecimal.ZERO.setScale(2)
    }

    private fun movements(
        party: PartyRef,
        to: LocalDate,
    ): Pair<List<PayableMovement>, List<OwedIssue>> {
        // Departures are owed once they have run: never after today, whatever the statement's end date.
        val upTo = if (to.isAfter(today())) today() else to
        val departures: List<PayableDeparture> = reads.payableDepartures(upTo)
        val adjustments = settlements.adjustments(party)
        val (owed, issues) = PayablesCalculator.derive(party, departures, costs.findAll(), adjustments)
        return PayablesCalculator.movements(owed, adjustments, settlements.payments(party), ::cairoDay) to issues
    }

    private fun cairoDay(instant: Instant): LocalDate = instant.atZone(CAIRO).toLocalDate()

    private fun paidTodayEgp(party: PartyRef): BigDecimal? =
        SettlementPolicy.paidTodayEgp(settlements.payments(party)) { cairoDay(it) == today() }

    /** A EUR amount is valued only with a rate set today and within 20 % of the previous day's rate. */
    private fun rateTrusted(): Boolean = SettlementPolicy.rateTrusted(fxRates.latestFor(today()), fxRates.latestBefore(today()))

    companion object {
        private val CAIRO: ZoneId = ZoneId.of("Africa/Cairo")
    }
}
