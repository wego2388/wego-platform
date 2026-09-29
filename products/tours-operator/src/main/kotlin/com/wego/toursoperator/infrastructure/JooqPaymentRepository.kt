package com.wego.toursoperator.infrastructure

import com.wego.generated.jooq.tables.ToursOperatorPayment.TOURS_OPERATOR_PAYMENT
import com.wego.toursoperator.application.PaymentRepository
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.Payment
import com.wego.toursoperator.domain.PaymentId
import com.wego.toursoperator.domain.PaymentStatus
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime

@Repository("stoPaymentRepositoryImpl")
class JooqPaymentRepository(
    private val dsl: DSLContext,
) : PaymentRepository {
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
            .execute()
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
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, java.time.ZoneOffset.UTC)
}
