package com.wego.toursoperator.infrastructure

import com.wego.generated.jooq.tables.ToursOperatorBooking.TOURS_OPERATOR_BOOKING
import com.wego.generated.jooq.tables.ToursOperatorPayment.TOURS_OPERATOR_PAYMENT
import com.wego.generated.jooq.tables.ToursOperatorPaymentAuditEvent.TOURS_OPERATOR_PAYMENT_AUDIT_EVENT
import com.wego.generated.jooq.tables.ToursOperatorPaymentRefundEvent.TOURS_OPERATOR_PAYMENT_REFUND_EVENT
import com.wego.toursoperator.application.PaymentActivity
import com.wego.toursoperator.application.PaymentHistoryEntry
import com.wego.toursoperator.application.PaymentRepository
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.Payment
import com.wego.toursoperator.domain.PaymentId
import com.wego.toursoperator.domain.PaymentStatus
import com.wego.toursoperator.domain.TourId
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

@Repository("stoPaymentRepositoryImpl")
class JooqPaymentRepository(
    private val dsl: DSLContext,
) : PaymentRepository {
    @Transactional(readOnly = true)
    override fun hasAnyPayments(): Boolean = dsl.fetchExists(TOURS_OPERATOR_PAYMENT)

    @Transactional(readOnly = true)
    override fun findById(id: PaymentId): Payment? {
        val t = TOURS_OPERATOR_PAYMENT
        return dsl
            .selectFrom(t)
            .where(t.ID.eq(id.value))
            .fetchOne()
            ?.let(::toDomain)
    }

    @Transactional
    override fun findByIdForUpdate(id: PaymentId): Payment? {
        val t = TOURS_OPERATOR_PAYMENT
        return dsl
            .selectFrom(t)
            .where(t.ID.eq(id.value))
            .forUpdate()
            .fetchOne()
            ?.let(::toDomain)
    }

    @Transactional(readOnly = true)
    override fun findByBookingId(bookingId: BookingId): Payment? {
        val t = TOURS_OPERATOR_PAYMENT
        return dsl
            .selectFrom(t)
            .where(t.BOOKING_ID.eq(bookingId.value))
            .fetchOne()
            ?.let(::toDomain)
    }

    @Transactional
    override fun findByBookingIdForUpdate(bookingId: BookingId): Payment? {
        val t = TOURS_OPERATOR_PAYMENT
        return dsl
            .selectFrom(t)
            .where(t.BOOKING_ID.eq(bookingId.value))
            .forUpdate()
            .fetchOne()
            ?.let(::toDomain)
    }

    @Transactional(readOnly = true)
    override fun findByPaymobOrderId(paymobOrderId: String): Payment? {
        val t = TOURS_OPERATOR_PAYMENT
        return dsl
            .selectFrom(t)
            .where(t.PAYMOB_ORDER_ID.eq(paymobOrderId))
            .fetchOne()
            ?.let(::toDomain)
    }

    @Transactional
    override fun findByPaymobOrderIdForUpdate(paymobOrderId: String): Payment? {
        val t = TOURS_OPERATOR_PAYMENT
        return dsl
            .selectFrom(t)
            .where(t.PAYMOB_ORDER_ID.eq(paymobOrderId))
            .forUpdate()
            .fetchOne()
            ?.let(::toDomain)
    }

    @Transactional(readOnly = true)
    override fun listActivity(
        fromInclusive: Instant,
        toExclusive: Instant,
        status: PaymentStatus?,
        afterId: PaymentId?,
        size: Int,
    ): List<PaymentActivity> {
        val t = TOURS_OPERATOR_PAYMENT
        val from = toOffset(fromInclusive)
        val to = toOffset(toExclusive)
        val activityInRange =
            t.CREATED_AT
                .ge(from)
                .and(t.CREATED_AT.lt(to))
                .or(t.PAID_AT.ge(from).and(t.PAID_AT.lt(to)))
                .or(t.FAILED_AT.ge(from).and(t.FAILED_AT.lt(to)))
                .or(t.REFUNDED_AT.ge(from).and(t.REFUNDED_AT.lt(to)))
        val withStatus = status?.let { activityInRange.and(t.STATUS.eq(it.name)) } ?: activityInRange
        val condition = afterId?.let { withStatus.and(t.ID.gt(it.value)) } ?: withStatus
        val payments =
            dsl
                .selectFrom(t)
                .where(condition)
                .orderBy(t.ID.asc())
                .limit(size)
                .fetch()
                .map(::toDomain)
        if (payments.isEmpty()) return emptyList()
        val bookingContext =
            dsl
                .select(
                    TOURS_OPERATOR_BOOKING.ID,
                    TOURS_OPERATOR_BOOKING.TOUR_ID,
                    TOURS_OPERATOR_BOOKING.ADULTS_COUNT,
                    TOURS_OPERATOR_BOOKING.CHILDREN_COUNT,
                ).from(TOURS_OPERATOR_BOOKING)
                .where(TOURS_OPERATOR_BOOKING.ID.`in`(payments.map { it.bookingId.value }))
                .fetch()
                .associateBy { it[TOURS_OPERATOR_BOOKING.ID] }
        return payments.map { payment ->
            val booking = checkNotNull(bookingContext[payment.bookingId.value])
            PaymentActivity(
                payment = payment,
                tourId = TourId(checkNotNull(booking[TOURS_OPERATOR_BOOKING.TOUR_ID])),
                adultsCount = checkNotNull(booking[TOURS_OPERATOR_BOOKING.ADULTS_COUNT]),
                childrenCount = checkNotNull(booking[TOURS_OPERATOR_BOOKING.CHILDREN_COUNT]),
            )
        }
    }

    @Transactional
    override fun save(payment: Payment) {
        val t = TOURS_OPERATOR_PAYMENT
        dsl
            .insertInto(t)
            .set(t.ID, payment.id.value)
            .set(t.BOOKING_ID, payment.bookingId.value)
            .set(t.AMOUNT_EUR, payment.amountEur)
            .set(t.AMOUNT_MINOR_UNITS, payment.amountMinorUnits)
            .set(t.CURRENCY_CODE, payment.currencyCode)
            .set(t.PROVIDER_REFERENCE, payment.providerReference)
            .set(t.PAYMOB_ORDER_ID, payment.paymobOrderId)
            .set(t.PAYMOB_TRANSACTION_ID, payment.paymobTransactionId)
            .set(t.PROVIDER_CHECKOUT_TOKEN, payment.providerCheckoutToken)
            .set(t.STATUS, payment.status.name)
            .set(t.PROVIDER_STATUS, payment.providerStatus)
            .set(t.LAST_CALLBACK_AUDIT, payment.lastCallbackAudit)
            .set(t.CREATED_AT, toOffset(payment.createdAt))
            .set(t.PAID_AT, payment.paidAt?.let(::toOffset))
            .set(t.FAILED_AT, payment.failedAt?.let(::toOffset))
            .set(t.REFUNDED_AT, payment.refundedAt?.let(::toOffset))
            .set(t.REVENUE_RECOGNISED_AT, payment.revenueRecognisedAt?.let(::toOffset))
            .onConflict(t.ID)
            .doUpdate()
            .set(t.PAYMOB_ORDER_ID, payment.paymobOrderId)
            .set(t.PAYMOB_TRANSACTION_ID, payment.paymobTransactionId)
            .set(t.PROVIDER_CHECKOUT_TOKEN, payment.providerCheckoutToken)
            .set(t.STATUS, payment.status.name)
            .set(t.PROVIDER_STATUS, payment.providerStatus)
            .set(t.LAST_CALLBACK_AUDIT, payment.lastCallbackAudit)
            .set(t.PAID_AT, payment.paidAt?.let(::toOffset))
            .set(t.FAILED_AT, payment.failedAt?.let(::toOffset))
            .set(t.REFUNDED_AT, payment.refundedAt?.let(::toOffset))
            .set(t.REVENUE_RECOGNISED_AT, payment.revenueRecognisedAt?.let(::toOffset))
            .execute()
        val a = TOURS_OPERATOR_PAYMENT_AUDIT_EVENT
        payment.drainTransitions().forEach { transition ->
            dsl
                .insertInto(a)
                .set(a.ID, UUID.randomUUID())
                .set(a.PAYMENT_ID, payment.id.value)
                .set(a.FROM_STATUS, transition.fromStatus?.name)
                .set(a.TO_STATUS, transition.toStatus.name)
                .set(a.PROVIDER_STATUS, transition.providerStatus?.take(32))
                .set(a.OCCURRED_AT, toOffset(transition.occurredAt))
                .set(a.SOURCE, "LIVE")
                .execute()
        }
    }

    @Transactional(readOnly = true)
    override fun historyForBooking(bookingId: BookingId): List<PaymentHistoryEntry> {
        val a = TOURS_OPERATOR_PAYMENT_AUDIT_EVENT
        val t = TOURS_OPERATOR_PAYMENT
        return dsl
            .select(a.PAYMENT_ID, a.FROM_STATUS, a.TO_STATUS, a.PROVIDER_STATUS, a.OCCURRED_AT, a.SOURCE)
            .from(a)
            .join(t)
            .on(t.ID.eq(a.PAYMENT_ID))
            .where(t.BOOKING_ID.eq(bookingId.value))
            .orderBy(a.OCCURRED_AT.asc(), a.SEQ.asc())
            .fetch { r ->
                PaymentHistoryEntry(
                    paymentId = PaymentId(checkNotNull(r[a.PAYMENT_ID])),
                    fromStatus = r[a.FROM_STATUS]?.let(PaymentStatus::valueOf),
                    toStatus = PaymentStatus.valueOf(checkNotNull(r[a.TO_STATUS])),
                    providerStatus = r[a.PROVIDER_STATUS],
                    occurredAt = checkNotNull(r[a.OCCURRED_AT]).toInstant(),
                    recorded = r[a.SOURCE] == "LIVE",
                )
            }
    }

    @Transactional
    override fun claimRefundCallback(
        paymentId: PaymentId,
        providerRefundId: String,
        amountMinorUnits: Long,
        receivedAt: Instant,
    ): Boolean {
        require(providerRefundId.isNotBlank()) { "providerRefundId must not be blank" }
        val r = TOURS_OPERATOR_PAYMENT_REFUND_EVENT
        return dsl
            .insertInto(r)
            .set(r.ID, UUID.randomUUID())
            .set(r.PAYMENT_ID, paymentId.value)
            .set(r.PROVIDER_REFUND_ID, providerRefundId)
            .set(r.AMOUNT_MINOR_UNITS, amountMinorUnits)
            .set(r.RECEIVED_AT, toOffset(receivedAt))
            .onConflict(r.PAYMENT_ID, r.PROVIDER_REFUND_ID)
            .doNothing()
            .execute() == 1
    }

    private fun toDomain(r: com.wego.generated.jooq.tables.records.ToursOperatorPaymentRecord): Payment =
        Payment(
            id = PaymentId(r.id),
            bookingId = BookingId(r.bookingId),
            amountEur = r.amountEur,
            amountMinorUnits = r.amountMinorUnits,
            currencyCode = r.currencyCode,
            providerReference = r.providerReference,
            paymobOrderId = r.paymobOrderId,
            paymobTransactionId = r.paymobTransactionId,
            providerCheckoutToken = r.providerCheckoutToken,
            status = PaymentStatus.valueOf(r.status),
            providerStatus = r.providerStatus,
            lastCallbackAudit = r.lastCallbackAudit,
            createdAt = r.createdAt.toInstant(),
            paidAt = r.paidAt?.toInstant(),
            failedAt = r.failedAt?.toInstant(),
            refundedAt = r.refundedAt?.toInstant(),
            revenueRecognisedAt = r.revenueRecognisedAt?.toInstant(),
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, java.time.ZoneOffset.UTC)
}
