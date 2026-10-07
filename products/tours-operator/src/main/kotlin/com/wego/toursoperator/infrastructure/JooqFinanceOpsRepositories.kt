package com.wego.toursoperator.infrastructure

import com.wego.generated.jooq.tables.IdentityUser.IDENTITY_USER
import com.wego.generated.jooq.tables.ToursOperatorBooking.TOURS_OPERATOR_BOOKING
import com.wego.generated.jooq.tables.ToursOperatorCashBoxEvent.TOURS_OPERATOR_CASH_BOX_EVENT
import com.wego.generated.jooq.tables.ToursOperatorCostComponent.TOURS_OPERATOR_COST_COMPONENT
import com.wego.generated.jooq.tables.ToursOperatorFxRate.TOURS_OPERATOR_FX_RATE
import com.wego.generated.jooq.tables.ToursOperatorOfficeCollection.TOURS_OPERATOR_OFFICE_COLLECTION
import com.wego.generated.jooq.tables.ToursOperatorOfficeRefund.TOURS_OPERATOR_OFFICE_REFUND
import com.wego.generated.jooq.tables.ToursOperatorPayableAdjustment.TOURS_OPERATOR_PAYABLE_ADJUSTMENT
import com.wego.generated.jooq.tables.ToursOperatorPayment.TOURS_OPERATOR_PAYMENT
import com.wego.generated.jooq.tables.ToursOperatorSettlementApproval.TOURS_OPERATOR_SETTLEMENT_APPROVAL
import com.wego.generated.jooq.tables.ToursOperatorSettlementPayment.TOURS_OPERATOR_SETTLEMENT_PAYMENT
import com.wego.generated.jooq.tables.ToursOperatorSlotAssignment.TOURS_OPERATOR_SLOT_ASSIGNMENT
import com.wego.generated.jooq.tables.ToursOperatorSlotAssignmentSupplier.TOURS_OPERATOR_SLOT_ASSIGNMENT_SUPPLIER
import com.wego.generated.jooq.tables.records.ToursOperatorBookingRecord
import com.wego.toursoperator.application.CashBoxRepository
import com.wego.toursoperator.application.CostComponentRepository
import com.wego.toursoperator.application.FinanceReadRepository
import com.wego.toursoperator.application.OfficeMoneySummary
import com.wego.toursoperator.application.OfficeRefundRepository
import com.wego.toursoperator.application.SettlementRepository
import com.wego.toursoperator.domain.AdjustmentKind
import com.wego.toursoperator.domain.BookingChannel
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.BookingPricing
import com.wego.toursoperator.domain.BookingStatus
import com.wego.toursoperator.domain.CashBoxEvent
import com.wego.toursoperator.domain.CashBoxEventKind
import com.wego.toursoperator.domain.CashFlow
import com.wego.toursoperator.domain.CostBasis
import com.wego.toursoperator.domain.CostCategory
import com.wego.toursoperator.domain.CostComponent
import com.wego.toursoperator.domain.FxRate
import com.wego.toursoperator.domain.Money
import com.wego.toursoperator.domain.OfficeRefund
import com.wego.toursoperator.domain.OfficeRefundKind
import com.wego.toursoperator.domain.PaidCurrency
import com.wego.toursoperator.domain.PartyRef
import com.wego.toursoperator.domain.PartyType
import com.wego.toursoperator.domain.PayableAdjustment
import com.wego.toursoperator.domain.PayableDeparture
import com.wego.toursoperator.domain.PaymentStatus
import com.wego.toursoperator.domain.ProfitBooking
import com.wego.toursoperator.domain.RefundMethod
import com.wego.toursoperator.domain.SettlementApproval
import com.wego.toursoperator.domain.SettlementMethod
import com.wego.toursoperator.domain.SettlementPayment
import com.wego.toursoperator.domain.SettlementPaymentKind
import com.wego.toursoperator.domain.TimeSlot
import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.TourSlotId
import com.wego.toursoperator.domain.UnitPurchase
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.TableField
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

private val CAIRO: ZoneId = ZoneId.of("Africa/Cairo")
private val ZERO: BigDecimal = BigDecimal.ZERO.setScale(2)

private fun Instant.utc(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)

private fun dayStart(date: LocalDate): OffsetDateTime = date.atStartOfDay(CAIRO).toInstant().utc()

private fun BigDecimal?.money2(): BigDecimal = (this ?: BigDecimal.ZERO).setScale(2)

private fun DSLContext.advisoryLock(key: String) {
    execute("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", key)
}

private fun partyOf(
    type: String,
    supplierId: UUID?,
    driverId: UUID?,
): PartyRef =
    when (PartyType.valueOf(type)) {
        PartyType.SUPPLIER -> PartyRef(PartyType.SUPPLIER, checkNotNull(supplierId))
        PartyType.DRIVER -> PartyRef(PartyType.DRIVER, checkNotNull(driverId))
    }

@Repository("stoCostComponentRepositoryImpl")
class JooqCostComponentRepository(
    private val dsl: DSLContext,
) : CostComponentRepository {
    private val t = TOURS_OPERATOR_COST_COMPONENT

    @Transactional(readOnly = true)
    override fun findAll(): List<CostComponent> =
        dsl
            .selectFrom(t)
            .orderBy(t.VALID_FROM, t.CREATED_AT)
            .fetch()
            .map(::toDomain)

    @Transactional(readOnly = true)
    override fun findById(id: UUID): CostComponent? =
        dsl
            .selectFrom(t)
            .where(t.ID.eq(id))
            .fetchOne()
            ?.let(::toDomain)

    @Transactional
    override fun findByIdForUpdate(id: UUID): CostComponent? =
        dsl
            .selectFrom(t)
            .where(t.ID.eq(id))
            .forUpdate()
            .fetchOne()
            ?.let(::toDomain)

    @Transactional
    override fun append(component: CostComponent) {
        dsl
            .insertInto(t)
            .set(t.ID, component.id)
            .set(t.TOUR_ID, component.tourId?.value)
            .set(t.DRIVER_ID, component.driverId)
            .set(t.CATEGORY, component.category.name)
            .set(t.LABEL, component.label)
            .set(t.BASIS, component.basis.name)
            .set(t.CURRENCY, component.currency.name)
            .set(t.AMOUNT, component.amount)
            .set(t.CHILD_AMOUNT, component.childAmount)
            .set(t.SUPPLIER_ID, component.supplierId)
            .set(t.VALID_FROM, component.validFrom)
            .set(t.VALID_UNTIL, component.validUntil)
            .set(t.REPLACES_COMPONENT_ID, component.replacesComponentId)
            .set(t.NOTE, component.note)
            .set(t.CREATED_BY_USER_ID, component.createdByUserId)
            .set(t.CREATED_AT, component.createdAt.utc())
            .execute()
    }

    @Transactional
    override fun end(
        id: UUID,
        validUntil: LocalDate,
        endedByUserId: UUID,
        endedAt: Instant,
    ) {
        dsl
            .update(t)
            .set(t.VALID_UNTIL, validUntil)
            .set(t.ENDED_AT, endedAt.utc())
            .set(t.ENDED_BY_USER_ID, endedByUserId)
            .where(t.ID.eq(id))
            .and(t.ENDED_AT.isNull)
            .execute()
    }

    private fun toDomain(r: Record): CostComponent =
        CostComponent(
            id = r.get(t.ID),
            tourId = r.get(t.TOUR_ID)?.let(::TourId),
            driverId = r.get(t.DRIVER_ID),
            category = CostCategory.valueOf(r.get(t.CATEGORY)),
            label = r.get(t.LABEL),
            basis = CostBasis.valueOf(r.get(t.BASIS)),
            currency = PaidCurrency.valueOf(r.get(t.CURRENCY)),
            amount = r.get(t.AMOUNT).setScale(2),
            childAmount = r.get(t.CHILD_AMOUNT)?.setScale(2),
            supplierId = r.get(t.SUPPLIER_ID),
            validFrom = r.get(t.VALID_FROM),
            validUntil = r.get(t.VALID_UNTIL),
            replacesComponentId = r.get(t.REPLACES_COMPONENT_ID),
            note = r.get(t.NOTE),
            createdByUserId = r.get(t.CREATED_BY_USER_ID),
            createdAt = r.get(t.CREATED_AT).toInstant(),
            endedByUserId = r.get(t.ENDED_BY_USER_ID),
            endedAt = r.get(t.ENDED_AT)?.toInstant(),
        )
}

@Repository("stoOfficeRefundRepositoryImpl")
class JooqOfficeRefundRepository(
    private val dsl: DSLContext,
) : OfficeRefundRepository {
    private val t = TOURS_OPERATOR_OFFICE_REFUND
    private val fx = TOURS_OPERATOR_FX_RATE

    private fun select(condition: Condition): List<OfficeRefund> =
        dsl
            .select(t.asterisk(), fx.RATE_DATE, fx.SET_BY_USER_ID, fx.SET_AT, IDENTITY_USER.EMAIL)
            .from(t)
            .leftJoin(fx)
            .on(fx.ID.eq(t.FX_RATE_ID))
            .leftJoin(IDENTITY_USER)
            .on(IDENTITY_USER.ID.eq(t.RECORDED_BY_USER_ID))
            .where(condition)
            .orderBy(t.RECORDED_AT.asc(), t.ID.asc())
            .fetch()
            .map(::toDomain)

    @Transactional(readOnly = true)
    override fun findByBooking(bookingId: BookingId): List<OfficeRefund> = select(t.BOOKING_ID.eq(bookingId.value))

    @Transactional(readOnly = true)
    override fun findByRequest(
        actorUserId: UUID,
        clientRequestId: UUID,
    ): OfficeRefund? = select(t.RECORDED_BY_USER_ID.eq(actorUserId).and(t.CLIENT_REQUEST_ID.eq(clientRequestId))).firstOrNull()

    @Transactional(readOnly = true)
    override fun netRefunded(bookingIds: Collection<BookingId>): Map<BookingId, BigDecimal> {
        if (bookingIds.isEmpty()) return emptyMap()
        val signed = DSL.`when`(t.KIND.eq(OfficeRefundKind.REFUND.name), t.AMOUNT_EUR).otherwise(t.AMOUNT_EUR.neg())
        return dsl
            .select(t.BOOKING_ID, DSL.sum(signed))
            .from(t)
            .where(t.BOOKING_ID.`in`(bookingIds.map { it.value }))
            .groupBy(t.BOOKING_ID)
            .fetch()
            .associate { BookingId(checkNotNull(it.value1())) to it.value2().money2() }
    }

    @Transactional
    override fun append(refund: OfficeRefund) {
        dsl
            .insertInto(t)
            .set(t.ID, refund.id)
            .set(t.BOOKING_ID, refund.bookingId.value)
            .set(t.KIND, refund.kind.name)
            .set(t.METHOD, refund.method.name)
            .set(t.CURRENCY_PAID, refund.currencyPaid.name)
            .set(t.AMOUNT_PAID, refund.amountPaid.amount)
            .set(t.AMOUNT_EUR, refund.amount.amount)
            .set(t.FX_RATE, refund.fxRate?.egpPerEur)
            .set(t.FX_RATE_ID, refund.fxRate?.id)
            .set(t.REFERENCE, refund.reference)
            .set(t.REASON, refund.reason)
            .set(t.REVERSES_REFUND_ID, refund.reversesRefundId)
            .set(t.RECORDED_BY_USER_ID, refund.recordedByUserId)
            .set(t.CLIENT_REQUEST_ID, refund.clientRequestId)
            .set(t.RECORDED_AT, refund.recordedAt.utc())
            .execute()
    }

    private fun toDomain(r: Record): OfficeRefund {
        val rateId = r.get(t.FX_RATE_ID)
        return OfficeRefund(
            id = r.get(t.ID),
            bookingId = BookingId(r.get(t.BOOKING_ID)),
            kind = OfficeRefundKind.valueOf(r.get(t.KIND)),
            method = RefundMethod.valueOf(r.get(t.METHOD)),
            amount = Money(r.get(t.AMOUNT_EUR).setScale(2)),
            currencyPaid = PaidCurrency.valueOf(r.get(t.CURRENCY_PAID)),
            amountPaid = Money(r.get(t.AMOUNT_PAID).setScale(2)),
            fxRate =
                rateId?.let {
                    FxRate(
                        id = it,
                        rateDate = checkNotNull(r.get(TOURS_OPERATOR_FX_RATE.RATE_DATE)),
                        egpPerEur = checkNotNull(r.get(t.FX_RATE)),
                        setByUserId = r.get(TOURS_OPERATOR_FX_RATE.SET_BY_USER_ID),
                        setAt = checkNotNull(r.get(TOURS_OPERATOR_FX_RATE.SET_AT)).toInstant(),
                    )
                },
            reference = r.get(t.REFERENCE),
            reason = r.get(t.REASON),
            reversesRefundId = r.get(t.REVERSES_REFUND_ID),
            recordedByUserId = r.get(t.RECORDED_BY_USER_ID),
            clientRequestId = r.get(t.CLIENT_REQUEST_ID),
            recordedAt = r.get(t.RECORDED_AT).toInstant(),
            recordedByEmail = r.get(IDENTITY_USER.EMAIL),
        )
    }
}

@Repository("stoSettlementRepositoryImpl")
class JooqSettlementRepository(
    private val dsl: DSLContext,
) : SettlementRepository {
    private val a = TOURS_OPERATOR_PAYABLE_ADJUSTMENT
    private val p = TOURS_OPERATOR_SETTLEMENT_PAYMENT
    private val ap = TOURS_OPERATOR_SETTLEMENT_APPROVAL

    @Transactional
    override fun lockParty(party: PartyRef) = dsl.advisoryLock("tours-operator-settlement:${party.key}")

    private fun <R : Record> partyCondition(
        party: PartyRef?,
        supplier: TableField<R, UUID?>,
        driver: TableField<R, UUID?>,
    ): Condition =
        when (party?.type) {
            null -> DSL.noCondition()
            PartyType.SUPPLIER -> supplier.eq(party.id)
            PartyType.DRIVER -> driver.eq(party.id)
        }

    private fun adjustmentsWhere(condition: Condition): List<PayableAdjustment> =
        dsl
            .select(a.asterisk(), IDENTITY_USER.EMAIL)
            .from(a)
            .leftJoin(IDENTITY_USER)
            .on(IDENTITY_USER.ID.eq(a.RECORDED_BY_USER_ID))
            .where(condition)
            .orderBy(a.RECORDED_AT.asc(), a.ID.asc())
            .fetch()
            .map { r ->
                PayableAdjustment(
                    id = r.get(a.ID),
                    party = partyOf(r.get(a.PARTY_TYPE), r.get(a.SUPPLIER_ID), r.get(a.DRIVER_ID)),
                    slotId = r.get(a.SLOT_ID)?.let(::TourSlotId),
                    serviceDate = r.get(a.SERVICE_DATE),
                    kind = AdjustmentKind.valueOf(r.get(a.KIND)),
                    currency = PaidCurrency.valueOf(r.get(a.CURRENCY)),
                    amount = r.get(a.AMOUNT).setScale(2),
                    reason = r.get(a.REASON),
                    reversesAdjustmentId = r.get(a.REVERSES_ADJUSTMENT_ID),
                    recordedByUserId = r.get(a.RECORDED_BY_USER_ID),
                    clientRequestId = r.get(a.CLIENT_REQUEST_ID),
                    recordedAt = r.get(a.RECORDED_AT).toInstant(),
                    recordedByEmail = r.get(IDENTITY_USER.EMAIL),
                )
            }

    @Transactional(readOnly = true)
    override fun adjustments(party: PartyRef?): List<PayableAdjustment> =
        adjustmentsWhere(partyCondition(party, a.SUPPLIER_ID, a.DRIVER_ID))

    @Transactional(readOnly = true)
    override fun adjustmentsForSlots(slotIds: Collection<TourSlotId>): List<PayableAdjustment> =
        if (slotIds.isEmpty()) emptyList() else adjustmentsWhere(a.SLOT_ID.`in`(slotIds.map { it.value }))

    @Transactional(readOnly = true)
    override fun findAdjustmentByRequest(
        actorUserId: UUID,
        clientRequestId: UUID,
    ): PayableAdjustment? =
        adjustmentsWhere(a.RECORDED_BY_USER_ID.eq(actorUserId).and(a.CLIENT_REQUEST_ID.eq(clientRequestId))).firstOrNull()

    @Transactional
    override fun appendAdjustment(adjustment: PayableAdjustment) {
        dsl
            .insertInto(a)
            .set(a.ID, adjustment.id)
            .set(a.PARTY_TYPE, adjustment.party.type.name)
            .set(a.SUPPLIER_ID, adjustment.party.id.takeIf { adjustment.party.type == PartyType.SUPPLIER })
            .set(a.DRIVER_ID, adjustment.party.id.takeIf { adjustment.party.type == PartyType.DRIVER })
            .set(a.SLOT_ID, adjustment.slotId?.value)
            .set(a.SERVICE_DATE, adjustment.serviceDate)
            .set(a.KIND, adjustment.kind.name)
            .set(a.CURRENCY, adjustment.currency.name)
            .set(a.AMOUNT, adjustment.amount)
            .set(a.REASON, adjustment.reason)
            .set(a.REVERSES_ADJUSTMENT_ID, adjustment.reversesAdjustmentId)
            .set(a.RECORDED_BY_USER_ID, adjustment.recordedByUserId)
            .set(a.CLIENT_REQUEST_ID, adjustment.clientRequestId)
            .set(a.RECORDED_AT, adjustment.recordedAt.utc())
            .execute()
    }

    private fun paymentsWhere(condition: Condition): List<SettlementPayment> =
        dsl
            .select(p.asterisk(), IDENTITY_USER.EMAIL)
            .from(p)
            .leftJoin(IDENTITY_USER)
            .on(IDENTITY_USER.ID.eq(p.RECORDED_BY_USER_ID))
            .where(condition)
            .orderBy(p.RECORDED_AT.asc(), p.ID.asc())
            .fetch()
            .map { r ->
                SettlementPayment(
                    id = r.get(p.ID),
                    party = partyOf(r.get(p.PARTY_TYPE), r.get(p.SUPPLIER_ID), r.get(p.DRIVER_ID)),
                    kind = SettlementPaymentKind.valueOf(r.get(p.KIND)),
                    method = SettlementMethod.valueOf(r.get(p.METHOD)),
                    currency = PaidCurrency.valueOf(r.get(p.CURRENCY)),
                    amount = r.get(p.AMOUNT).setScale(2),
                    egpEquivalent = r.get(p.EGP_EQUIVALENT)?.setScale(2),
                    fxRateId = r.get(p.FX_RATE_ID),
                    reference = r.get(p.REFERENCE),
                    note = r.get(p.NOTE),
                    approvalId = r.get(p.APPROVAL_ID),
                    reversesPaymentId = r.get(p.REVERSES_PAYMENT_ID),
                    reason = r.get(p.REASON),
                    recordedByUserId = r.get(p.RECORDED_BY_USER_ID),
                    clientRequestId = r.get(p.CLIENT_REQUEST_ID),
                    recordedAt = r.get(p.RECORDED_AT).toInstant(),
                    recordedByEmail = r.get(IDENTITY_USER.EMAIL),
                )
            }

    @Transactional(readOnly = true)
    override fun payments(party: PartyRef?): List<SettlementPayment> = paymentsWhere(partyCondition(party, p.SUPPLIER_ID, p.DRIVER_ID))

    @Transactional(readOnly = true)
    override fun findPaymentByRequest(
        actorUserId: UUID,
        clientRequestId: UUID,
    ): SettlementPayment? = paymentsWhere(p.RECORDED_BY_USER_ID.eq(actorUserId).and(p.CLIENT_REQUEST_ID.eq(clientRequestId))).firstOrNull()

    @Transactional
    override fun appendPayment(payment: SettlementPayment) {
        dsl
            .insertInto(p)
            .set(p.ID, payment.id)
            .set(p.PARTY_TYPE, payment.party.type.name)
            .set(p.SUPPLIER_ID, payment.party.id.takeIf { payment.party.type == PartyType.SUPPLIER })
            .set(p.DRIVER_ID, payment.party.id.takeIf { payment.party.type == PartyType.DRIVER })
            .set(p.KIND, payment.kind.name)
            .set(p.METHOD, payment.method.name)
            .set(p.CURRENCY, payment.currency.name)
            .set(p.AMOUNT, payment.amount)
            .set(p.EGP_EQUIVALENT, payment.egpEquivalent)
            .set(p.FX_RATE_ID, payment.fxRateId)
            .set(p.REFERENCE, payment.reference)
            .set(p.NOTE, payment.note)
            .set(p.APPROVAL_ID, payment.approvalId)
            .set(p.REVERSES_PAYMENT_ID, payment.reversesPaymentId)
            .set(p.REASON, payment.reason)
            .set(p.RECORDED_BY_USER_ID, payment.recordedByUserId)
            .set(p.CLIENT_REQUEST_ID, payment.clientRequestId)
            .set(p.RECORDED_AT, payment.recordedAt.utc())
            .execute()
    }

    private fun approvalsWhere(condition: Condition): List<SettlementApproval> {
        val used = p.`as`("used")
        return dsl
            .select(ap.asterisk(), IDENTITY_USER.EMAIL, used.ID)
            .from(ap)
            .leftJoin(IDENTITY_USER)
            .on(IDENTITY_USER.ID.eq(ap.APPROVED_BY_USER_ID))
            .leftJoin(used)
            .on(used.APPROVAL_ID.eq(ap.ID))
            .where(condition)
            .orderBy(ap.APPROVED_AT.asc(), ap.ID.asc())
            .fetch()
            .map { r ->
                SettlementApproval(
                    id = r.get(ap.ID),
                    party = partyOf(r.get(ap.PARTY_TYPE), r.get(ap.SUPPLIER_ID), r.get(ap.DRIVER_ID)),
                    currency = PaidCurrency.valueOf(r.get(ap.CURRENCY)),
                    amount = r.get(ap.AMOUNT).setScale(2),
                    note = r.get(ap.NOTE),
                    approvedByUserId = r.get(ap.APPROVED_BY_USER_ID),
                    clientRequestId = r.get(ap.CLIENT_REQUEST_ID),
                    approvedAt = r.get(ap.APPROVED_AT).toInstant(),
                    approvedByEmail = r.get(IDENTITY_USER.EMAIL),
                    usedByPaymentId = r.get(used.ID),
                )
            }
    }

    @Transactional(readOnly = true)
    override fun approvals(party: PartyRef): List<SettlementApproval> = approvalsWhere(partyCondition(party, ap.SUPPLIER_ID, ap.DRIVER_ID))

    @Transactional(readOnly = true)
    override fun findApproval(id: UUID): SettlementApproval? = approvalsWhere(ap.ID.eq(id)).firstOrNull()

    @Transactional(readOnly = true)
    override fun findApprovalByRequest(
        actorUserId: UUID,
        clientRequestId: UUID,
    ): SettlementApproval? =
        approvalsWhere(ap.APPROVED_BY_USER_ID.eq(actorUserId).and(ap.CLIENT_REQUEST_ID.eq(clientRequestId))).firstOrNull()

    @Transactional
    override fun appendApproval(approval: SettlementApproval) {
        dsl
            .insertInto(ap)
            .set(ap.ID, approval.id)
            .set(ap.PARTY_TYPE, approval.party.type.name)
            .set(ap.SUPPLIER_ID, approval.party.id.takeIf { approval.party.type == PartyType.SUPPLIER })
            .set(ap.DRIVER_ID, approval.party.id.takeIf { approval.party.type == PartyType.DRIVER })
            .set(ap.CURRENCY, approval.currency.name)
            .set(ap.AMOUNT, approval.amount)
            .set(ap.NOTE, approval.note)
            .set(ap.APPROVED_BY_USER_ID, approval.approvedByUserId)
            .set(ap.CLIENT_REQUEST_ID, approval.clientRequestId)
            .set(ap.APPROVED_AT, approval.approvedAt.utc())
            .execute()
    }
}

@Repository("stoCashBoxRepositoryImpl")
class JooqCashBoxRepository(
    private val dsl: DSLContext,
) : CashBoxRepository {
    private val e = TOURS_OPERATOR_CASH_BOX_EVENT
    private val c = TOURS_OPERATOR_OFFICE_COLLECTION
    private val r = TOURS_OPERATOR_OFFICE_REFUND
    private val p = TOURS_OPERATOR_SETTLEMENT_PAYMENT

    @Transactional
    override fun lockDay(
        date: LocalDate,
        currency: PaidCurrency,
    ) = dsl.advisoryLock("tours-operator-cash-box:$date:${currency.name}")

    private fun eventsWhere(condition: Condition): List<CashBoxEvent> =
        dsl
            .select(e.asterisk(), IDENTITY_USER.EMAIL)
            .from(e)
            .leftJoin(IDENTITY_USER)
            .on(IDENTITY_USER.ID.eq(e.ACTOR_USER_ID))
            .where(condition)
            .orderBy(e.BUSINESS_DATE.desc(), e.CURRENCY.asc(), e.SEQUENCE.asc())
            .fetch()
            .map { row ->
                CashBoxEvent(
                    id = row.get(e.ID),
                    businessDate = row.get(e.BUSINESS_DATE),
                    currency = PaidCurrency.valueOf(row.get(e.CURRENCY)),
                    sequence = row.get(e.SEQUENCE),
                    kind = CashBoxEventKind.valueOf(row.get(e.KIND)),
                    expected = row.get(e.EXPECTED)?.setScale(2),
                    counted = row.get(e.COUNTED)?.setScale(2),
                    difference = row.get(e.DIFFERENCE)?.setScale(2),
                    note = row.get(e.NOTE),
                    countEventId = row.get(e.COUNT_EVENT_ID),
                    actorUserId = row.get(e.ACTOR_USER_ID),
                    occurredAt = row.get(e.OCCURRED_AT).toInstant(),
                    actorEmail = row.get(IDENTITY_USER.EMAIL),
                )
            }

    @Transactional(readOnly = true)
    override fun events(
        date: LocalDate,
        currency: PaidCurrency,
    ): List<CashBoxEvent> = eventsWhere(e.BUSINESS_DATE.eq(date).and(e.CURRENCY.eq(currency.name)))

    @Transactional
    override fun append(event: CashBoxEvent) {
        dsl
            .insertInto(e)
            .set(e.ID, event.id)
            .set(e.BUSINESS_DATE, event.businessDate)
            .set(e.CURRENCY, event.currency.name)
            .set(e.SEQUENCE, event.sequence)
            .set(e.KIND, event.kind.name)
            .set(e.EXPECTED, event.expected)
            .set(e.COUNTED, event.counted)
            .set(e.DIFFERENCE, event.difference)
            .set(e.NOTE, event.note)
            .set(e.COUNT_EVENT_ID, event.countEventId)
            .set(e.ACTOR_USER_ID, event.actorUserId)
            .set(e.OCCURRED_AT, event.occurredAt.utc())
            .execute()
    }

    @Transactional(readOnly = true)
    override fun flow(
        date: LocalDate,
        currency: PaidCurrency,
    ): CashFlow {
        val start = dayStart(date)
        val end = dayStart(date.plusDays(1))

        fun sum(
            amount: TableField<*, BigDecimal?>,
            condition: Condition,
            table: org.jooq.Table<*>,
        ): BigDecimal =
            dsl
                .select(DSL.sum(amount))
                .from(table)
                .where(condition)
                .fetchOne()
                ?.value1()
                .money2()

        val cashMethods = listOf("CASH_AT_OFFICE", "CASH_ON_PICKUP")
        val collections =
            c.METHOD
                .`in`(
                    cashMethods,
                ).and(c.CURRENCY_PAID.eq(currency.name))
                .and(c.RECORDED_AT.ge(start))
                .and(c.RECORDED_AT.lt(end))
        val refunds =
            r.METHOD
                .eq(
                    RefundMethod.CASH.name,
                ).and(r.CURRENCY_PAID.eq(currency.name))
                .and(r.RECORDED_AT.ge(start))
                .and(r.RECORDED_AT.lt(end))
        val payments =
            p.METHOD
                .eq(
                    SettlementMethod.CASH.name,
                ).and(p.CURRENCY.eq(currency.name))
                .and(p.RECORDED_AT.ge(start))
                .and(p.RECORDED_AT.lt(end))
        return CashFlow(
            collected = sum(c.AMOUNT_PAID, collections.and(c.KIND.eq("COLLECTION")), c),
            collectionsReversed = sum(c.AMOUNT_PAID, collections.and(c.KIND.eq("REVERSAL")), c),
            refunded = sum(r.AMOUNT_PAID, refunds.and(r.KIND.eq(OfficeRefundKind.REFUND.name)), r),
            refundsReversed = sum(r.AMOUNT_PAID, refunds.and(r.KIND.eq(OfficeRefundKind.REVERSAL.name)), r),
            settlementsPaid = sum(p.AMOUNT, payments.and(p.KIND.eq(SettlementPaymentKind.PAYMENT.name)), p),
            settlementsReversed = sum(p.AMOUNT, payments.and(p.KIND.eq(SettlementPaymentKind.REVERSAL.name)), p),
        )
    }

    @Transactional(readOnly = true)
    override fun recentDays(days: Int): List<CashBoxEvent> {
        val since = LocalDate.now(CAIRO).minusDays(days.toLong())
        return eventsWhere(e.BUSINESS_DATE.ge(since))
            .groupBy { it.businessDate to it.currency }
            .map { (_, list) -> list.maxBy { it.sequence } }
            .sortedWith(compareByDescending<CashBoxEvent> { it.businessDate }.thenBy { it.currency })
    }
}

@Repository("stoFinanceReadRepositoryImpl")
class JooqFinanceReadRepository(
    private val dsl: DSLContext,
) : FinanceReadRepository {
    private val b = TOURS_OPERATOR_BOOKING
    private val pay = TOURS_OPERATOR_PAYMENT
    private val c = TOURS_OPERATOR_OFFICE_COLLECTION
    private val r = TOURS_OPERATOR_OFFICE_REFUND
    private val sa = TOURS_OPERATOR_SLOT_ASSIGNMENT
    private val sas = TOURS_OPERATOR_SLOT_ASSIGNMENT_SUPPLIER
    private val fx = TOURS_OPERATOR_FX_RATE

    private val live = listOf(BookingStatus.CONFIRMED.name, BookingStatus.COMPLETED.name)

    @Transactional(readOnly = true)
    override fun profitBookings(
        from: LocalDate,
        to: LocalDate,
    ): List<ProfitBooking> {
        val rows =
            dsl
                .selectFrom(b)
                .where(b.TOUR_DATE.between(from, to))
                .and(b.STATUS.`in`(live + BookingStatus.CANCELLED.name))
                .fetch()
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map { it.id }
        val online =
            dsl
                .select(pay.BOOKING_ID, pay.AMOUNT_EUR)
                .from(pay)
                .where(pay.BOOKING_ID.`in`(ids))
                .and(pay.STATUS.eq(PaymentStatus.PAID.name))
                .and(pay.REVENUE_RECOGNISED_AT.isNotNull)
                .fetch()
                .associate { it.value1() to it.value2().money2() }
        val collected = signedSums(c.BOOKING_ID, c.KIND.eq("COLLECTION"), c.AMOUNT_EUR, c.BOOKING_ID.`in`(ids), c)
        val refunded = signedSums(r.BOOKING_ID, r.KIND.eq(OfficeRefundKind.REFUND.name), r.AMOUNT_EUR, r.BOOKING_ID.`in`(ids), r)
        return rows.map { row ->
            ProfitBooking(
                bookingId = BookingId(row.id),
                reference = row.reference,
                tourId = TourId(row.tourId),
                slotId = TourSlotId(row.slotId),
                tourDate = row.tourDate,
                timeSlot = TimeSlot.valueOf(row.timeSlot),
                status = BookingStatus.valueOf(row.status),
                channel = BookingChannel.valueOf(row.channel),
                pricing = pricing(row),
                onlineRevenue = online[row.id] ?: ZERO,
                officeRevenue = (collected[row.id] ?: ZERO).subtract(refunded[row.id] ?: ZERO),
            )
        }
    }

    private fun signedSums(
        key: TableField<*, UUID?>,
        positive: Condition,
        amount: TableField<*, BigDecimal?>,
        where: Condition,
        table: org.jooq.Table<*>,
    ): Map<UUID, BigDecimal> {
        val signed = DSL.`when`(positive, amount).otherwise(amount.neg())
        return dsl
            .select(key, DSL.sum(signed))
            .from(table)
            .where(where)
            .groupBy(key)
            .fetch()
            .associate { checkNotNull(it.value1()) to it.value2().money2() }
    }

    @Transactional(readOnly = true)
    override fun drivers(slotIds: Collection<TourSlotId>): Map<TourSlotId, UUID?> {
        if (slotIds.isEmpty()) return emptyMap()
        return dsl
            .select(sa.SLOT_ID, sa.DRIVER_ID)
            .from(sa)
            .where(sa.SLOT_ID.`in`(slotIds.map { it.value }))
            .fetch()
            .associate { TourSlotId(checkNotNull(it.value1())) to it.value2() }
    }

    @Transactional(readOnly = true)
    override fun payableDepartures(to: LocalDate): List<PayableDeparture> {
        val assignments =
            dsl
                .selectFrom(sa)
                .where(sa.SERVICE_DATE.le(to))
                .fetch()
        if (assignments.isEmpty()) return emptyList()
        val slotIds = assignments.map { it.slotId }
        val suppliers =
            dsl
                .selectFrom(sas)
                .where(sas.SLOT_ID.`in`(slotIds))
                .fetch()
                .groupBy({ it.slotId }, { it.supplierId })
        val bookings =
            dsl
                .selectFrom(b)
                .where(b.SLOT_ID.`in`(slotIds))
                .and(b.STATUS.`in`(live))
                .fetch()
                .groupBy { it.slotId }
        return assignments.mapNotNull { a ->
            val rows = bookings[a.slotId].orEmpty()
            if (rows.isEmpty()) return@mapNotNull null
            PayableDeparture(
                slotId = TourSlotId(a.slotId),
                tourId = TourId(rows.first().tourId),
                date = a.serviceDate,
                timeSlot = TimeSlot.valueOf(a.timeSlot),
                driverId = a.driverId,
                supplierIds = suppliers[a.slotId].orEmpty().toSet(),
                live = rows.map(::pricing),
            )
        }
    }

    @Transactional(readOnly = true)
    override fun officeMoney(
        fromInclusive: Instant,
        toExclusive: Instant,
    ): OfficeMoneySummary {
        val inC = c.RECORDED_AT.ge(fromInclusive.utc()).and(c.RECORDED_AT.lt(toExclusive.utc()))
        val inR = r.RECORDED_AT.ge(fromInclusive.utc()).and(r.RECORDED_AT.lt(toExclusive.utc()))

        fun total(
            amount: TableField<*, BigDecimal?>,
            condition: Condition,
            table: org.jooq.Table<*>,
        ): Pair<BigDecimal, Int> =
            dsl
                .select(DSL.sum(amount), DSL.count())
                .from(table)
                .where(condition)
                .fetchOne()
                ?.let { it.value1().money2() to (it.value2() ?: 0) } ?: (ZERO to 0)
        val (collected, collectionCount) = total(c.AMOUNT_EUR, inC.and(c.KIND.eq("COLLECTION")), c)
        val (reversed, _) = total(c.AMOUNT_EUR, inC.and(c.KIND.eq("REVERSAL")), c)
        val (refunded, refundCount) = total(r.AMOUNT_EUR, inR.and(r.KIND.eq(OfficeRefundKind.REFUND.name)), r)
        val (refundsReversed, _) = total(r.AMOUNT_EUR, inR.and(r.KIND.eq(OfficeRefundKind.REVERSAL.name)), r)
        return OfficeMoneySummary(collected, reversed, refunded, refundsReversed, collectionCount, refundCount)
    }

    @Transactional(readOnly = true)
    override fun ratesBetween(
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, BigDecimal> =
        dsl
            .selectFrom(fx)
            .where(fx.RATE_DATE.between(from, to))
            .orderBy(fx.RATE_DATE, fx.SET_AT.asc(), fx.ID.asc())
            .fetch()
            // Ascending by set time: the last row of a day wins, as everywhere else.
            .associate { it.rateDate to it.egpPerEur }

    @Transactional(readOnly = true)
    override fun latestRate(): BigDecimal? =
        dsl
            .selectFrom(fx)
            .orderBy(fx.SET_AT.desc(), fx.ID.desc())
            .limit(1)
            .fetchOne()
            ?.egpPerEur

    private fun pricing(row: ToursOperatorBookingRecord): BookingPricing =
        BookingPricing(
            adultsCount = row.adultsCount,
            childrenCount = row.childrenCount,
            priceAdult = Money(row.priceAdultEur.setScale(2)),
            priceChild = row.priceChildEur?.let { Money(it.setScale(2)) },
            totalEur = Money(row.totalEur.setScale(2)),
            unit =
                row.unitCount?.let { units ->
                    UnitPurchase(row.priceOptionCode, row.priceOptionLabel, row.seatsPerUnit, units, Money(row.unitPriceEur.setScale(2)))
                },
        )
}
