package com.wego.divers.infrastructure

import com.wego.divers.application.BookingRepository
import com.wego.divers.domain.Booking
import com.wego.divers.domain.BookingId
import com.wego.divers.domain.BookingPricing
import com.wego.divers.domain.BookingStatus
import com.wego.divers.domain.CustomerContact
import com.wego.divers.domain.Money
import com.wego.divers.domain.OfferingId
import com.wego.divers.domain.PaymentStatus
import com.wego.divers.domain.PricingBasis
import com.wego.generated.jooq.tables.DiversBooking.DIVERS_BOOKING
import com.wego.generated.jooq.tables.records.DiversBookingRecord
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.math.RoundingMode
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class JooqBookingRepository(
    private val dsl: DSLContext,
) : BookingRepository {
    @Transactional
    override fun lockIdempotencyKey(
        actorUserId: UUID,
        idempotencyKey: String,
    ) {
        // hashtextextended: a stable 64-bit hash for this call, which is
        // all pg_advisory_xact_lock needs — it does not need to be stable
        // across Postgres versions, only within one lock acquisition.
        // Parameterized, not string-concatenated: no injection risk.
        dsl
            .query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                "divers-booking-idempotency:$actorUserId:$idempotencyKey",
            ).execute()
    }

    @Transactional(readOnly = true)
    override fun findById(id: BookingId): Booking? {
        val record = dsl.selectFrom(DIVERS_BOOKING).where(DIVERS_BOOKING.ID.eq(id.value)).fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional
    override fun findByIdForUpdate(id: BookingId): Booking? {
        val record =
            dsl
                .selectFrom(DIVERS_BOOKING)
                .where(DIVERS_BOOKING.ID.eq(id.value))
                .forUpdate()
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional(readOnly = true)
    override fun findByIdempotencyKey(
        createdByUserId: UUID,
        idempotencyKey: String,
    ): Booking? {
        val record =
            dsl
                .selectFrom(DIVERS_BOOKING)
                .where(DIVERS_BOOKING.CREATED_BY_USER_ID.eq(createdByUserId))
                .and(DIVERS_BOOKING.IDEMPOTENCY_KEY.eq(idempotencyKey))
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional(readOnly = true)
    override fun sumConfirmedPartySize(offeringId: OfferingId): Int {
        val sum =
            dsl
                .select(DSL.sum(DIVERS_BOOKING.PARTY_SIZE))
                .from(DIVERS_BOOKING)
                .where(DIVERS_BOOKING.OFFERING_ID.eq(offeringId.value))
                .and(DIVERS_BOOKING.STATUS.eq(BookingStatus.CONFIRMED.name))
                .fetchOne(0, java.math.BigDecimal::class.java)
        return sum?.toInt() ?: 0
    }

    @Transactional(readOnly = true)
    override fun findAll(
        offeringId: OfferingId?,
        status: BookingStatus?,
        limit: Int,
        offset: Int,
    ): List<Booking> {
        var condition = DSL.noCondition()
        if (offeringId != null) {
            condition = condition.and(DIVERS_BOOKING.OFFERING_ID.eq(offeringId.value))
        }
        if (status != null) {
            condition = condition.and(DIVERS_BOOKING.STATUS.eq(status.name))
        }
        return dsl
            .selectFrom(DIVERS_BOOKING)
            .where(condition)
            // ID as a tie-breaker — see JooqOfferingRepository's identical
            // reasoning: CREATED_AT alone is not unique enough to guarantee
            // offset pagination never skips or duplicates a row across two
            // separate page queries.
            .orderBy(DIVERS_BOOKING.CREATED_AT.desc(), DIVERS_BOOKING.ID.desc())
            .limit(limit)
            .offset(offset)
            .fetch()
            .map(::toDomain)
    }

    @Transactional(readOnly = true)
    override fun countCreatedBetween(
        from: Instant,
        to: Instant,
    ): Int =
        dsl.fetchCount(
            dsl
                .selectFrom(DIVERS_BOOKING)
                .where(DIVERS_BOOKING.CREATED_AT.ge(toOffset(from)))
                .and(DIVERS_BOOKING.CREATED_AT.lt(toOffset(to))),
        )

    @Transactional(readOnly = true)
    override fun sumPaidTotalsCreatedBetween(
        from: Instant,
        to: Instant,
    ): List<Money> =
        dsl
            .select(DIVERS_BOOKING.CURRENCY_CODE, DSL.sum(DIVERS_BOOKING.TOTAL_PRICE))
            .from(DIVERS_BOOKING)
            .where(DIVERS_BOOKING.STATUS.eq(BookingStatus.CONFIRMED.name))
            .and(DIVERS_BOOKING.PAYMENT_STATUS.eq(PaymentStatus.PAID.name))
            .and(DIVERS_BOOKING.CREATED_AT.ge(toOffset(from)))
            .and(DIVERS_BOOKING.CREATED_AT.lt(toOffset(to)))
            .groupBy(DIVERS_BOOKING.CURRENCY_CODE)
            .fetch { record ->
                Money(record.value2()!!.setScale(Money.REQUIRED_SCALE, RoundingMode.HALF_UP), record.value1())
            }

    @Transactional
    override fun save(booking: Booking) {
        val cancelledAtOffset = booking.cancelledAt?.let(::toOffset)
        dsl
            .insertInto(DIVERS_BOOKING)
            .set(DIVERS_BOOKING.ID, booking.id.value)
            .set(DIVERS_BOOKING.OFFERING_ID, booking.offeringId.value)
            .set(DIVERS_BOOKING.PARTY_SIZE, booking.partySize)
            .set(DIVERS_BOOKING.CUSTOMER_NAME, booking.customer.name)
            .set(DIVERS_BOOKING.CUSTOMER_EMAIL, booking.customer.email)
            .set(DIVERS_BOOKING.CUSTOMER_PHONE, booking.customer.phone)
            .set(DIVERS_BOOKING.STATUS, booking.status.name)
            .set(DIVERS_BOOKING.PAYMENT_STATUS, booking.paymentStatus.name)
            .set(DIVERS_BOOKING.PRICING_BASIS, booking.pricing.pricingBasis.name)
            .set(DIVERS_BOOKING.UNIT_PRICE, booking.pricing.unitPrice.amount)
            .set(DIVERS_BOOKING.BILLABLE_QUANTITY, booking.pricing.billableQuantity)
            .set(DIVERS_BOOKING.TOTAL_PRICE, booking.pricing.totalPrice.amount)
            .set(DIVERS_BOOKING.CURRENCY_CODE, booking.pricing.totalPrice.currencyCode)
            .set(DIVERS_BOOKING.IDEMPOTENCY_KEY, booking.idempotencyKey)
            .set(DIVERS_BOOKING.IDEMPOTENCY_FINGERPRINT, booking.idempotencyFingerprint)
            .set(DIVERS_BOOKING.CREATED_BY_USER_ID, booking.createdByUserId)
            .set(DIVERS_BOOKING.CREATED_AT, toOffset(booking.createdAt))
            .set(DIVERS_BOOKING.CANCELLED_AT, cancelledAtOffset)
            .set(DIVERS_BOOKING.CANCELLATION_REASON, booking.cancellationReason)
            .onConflict(DIVERS_BOOKING.ID)
            .doUpdate()
            .set(DIVERS_BOOKING.STATUS, booking.status.name)
            .set(DIVERS_BOOKING.PAYMENT_STATUS, booking.paymentStatus.name)
            .set(DIVERS_BOOKING.CANCELLED_AT, cancelledAtOffset)
            .set(DIVERS_BOOKING.CANCELLATION_REASON, booking.cancellationReason)
            .execute()
    }

    private fun toDomain(record: DiversBookingRecord): Booking =
        Booking(
            id = BookingId(record.id),
            offeringId = OfferingId(record.offeringId),
            partySize = record.partySize,
            customer = CustomerContact(record.customerName, record.customerEmail, record.customerPhone),
            status = BookingStatus.valueOf(record.status),
            paymentStatus = PaymentStatus.valueOf(record.paymentStatus),
            pricing =
                BookingPricing(
                    pricingBasis = PricingBasis.valueOf(record.pricingBasis),
                    unitPrice = Money(record.unitPrice, record.currencyCode),
                    billableQuantity = record.billableQuantity,
                    totalPrice = Money(record.totalPrice, record.currencyCode),
                ),
            idempotencyKey = record.idempotencyKey,
            idempotencyFingerprint = record.idempotencyFingerprint,
            createdByUserId = record.createdByUserId,
            createdAt = record.createdAt.toInstant(),
            cancelledAt = record.cancelledAt?.toInstant(),
            cancellationReason = record.cancellationReason,
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
