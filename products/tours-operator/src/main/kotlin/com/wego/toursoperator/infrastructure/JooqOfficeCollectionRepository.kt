package com.wego.toursoperator.infrastructure

import com.wego.generated.jooq.tables.ToursOperatorFxRate.TOURS_OPERATOR_FX_RATE
import com.wego.generated.jooq.tables.ToursOperatorOfficeCollection.TOURS_OPERATOR_OFFICE_COLLECTION
import com.wego.toursoperator.application.OfficeCollectionRepository
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.CollectionMethod
import com.wego.toursoperator.domain.FxRate
import com.wego.toursoperator.domain.Money
import com.wego.toursoperator.domain.OfficeCollection
import com.wego.toursoperator.domain.OfficeCollectionKind
import com.wego.toursoperator.domain.PaidCurrency
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository("stoOfficeCollectionRepositoryImpl")
class JooqOfficeCollectionRepository(
    private val dsl: DSLContext,
) : OfficeCollectionRepository {
    private val t = TOURS_OPERATOR_OFFICE_COLLECTION

    private val fx = TOURS_OPERATOR_FX_RATE

    @Transactional(readOnly = true)
    override fun findByBooking(bookingId: BookingId): List<OfficeCollection> =
        dsl
            .select(t.asterisk(), fx.RATE_DATE, fx.SET_BY_USER_ID, fx.SET_AT)
            .from(t)
            .leftJoin(fx)
            .on(fx.ID.eq(t.FX_RATE_ID))
            .where(t.BOOKING_ID.eq(bookingId.value))
            .orderBy(t.RECORDED_AT.asc(), t.ID.asc())
            .fetch()
            .map(::toDomain)

    @Transactional(readOnly = true)
    override fun referenceExists(
        method: CollectionMethod,
        reference: String,
    ): Boolean = dsl.fetchExists(t, t.METHOD.eq(method.name).and(t.REFERENCE.eq(reference)))

    @Transactional(readOnly = true)
    override fun findByRequest(
        actorUserId: UUID,
        clientRequestId: UUID,
    ): OfficeCollection? =
        dsl
            .select(t.asterisk(), fx.RATE_DATE, fx.SET_BY_USER_ID, fx.SET_AT)
            .from(t)
            .leftJoin(fx)
            .on(fx.ID.eq(t.FX_RATE_ID))
            .where(t.RECORDED_BY_USER_ID.eq(actorUserId))
            .and(t.CLIENT_REQUEST_ID.eq(clientRequestId))
            .fetchOne()
            ?.let(::toDomain)

    @Transactional(readOnly = true)
    override fun netCollected(bookingIds: Collection<BookingId>): Map<BookingId, BigDecimal> {
        if (bookingIds.isEmpty()) return emptyMap()
        val signed =
            DSL
                .`when`(t.KIND.eq(OfficeCollectionKind.COLLECTION.name), t.AMOUNT_EUR)
                .otherwise(t.AMOUNT_EUR.neg())
        return dsl
            .select(t.BOOKING_ID, DSL.sum(signed))
            .from(t)
            .where(t.BOOKING_ID.`in`(bookingIds.map { it.value }))
            .groupBy(t.BOOKING_ID)
            .fetch()
            .associate { BookingId(checkNotNull(it.value1())) to (it.value2() ?: BigDecimal.ZERO).setScale(Money.REQUIRED_SCALE) }
    }

    @Transactional
    override fun append(collection: OfficeCollection) {
        dsl
            .insertInto(t)
            .set(t.ID, collection.id)
            .set(t.BOOKING_ID, collection.bookingId.value)
            .set(t.KIND, collection.kind.name)
            .set(t.METHOD, collection.method.name)
            .set(t.CURRENCY_PAID, collection.currencyPaid.name)
            .set(t.AMOUNT_PAID, collection.amountPaid.amount)
            .set(t.AMOUNT_EUR, collection.amount.amount)
            .set(t.FX_RATE, collection.fxRate?.egpPerEur)
            .set(t.FX_RATE_ID, collection.fxRate?.id)
            .set(t.REFERENCE, collection.reference)
            .set(t.REVERSES_COLLECTION_ID, collection.reversesCollectionId)
            .set(t.REASON, collection.reason)
            .set(t.RECORDED_BY_USER_ID, collection.recordedByUserId)
            .set(t.CLIENT_REQUEST_ID, collection.clientRequestId)
            .set(t.RECORDED_AT, OffsetDateTime.ofInstant(collection.recordedAt, ZoneOffset.UTC))
            .execute()
    }

    private fun toDomain(r: org.jooq.Record): OfficeCollection {
        val rateId = r.get(t.FX_RATE_ID)
        return OfficeCollection(
            id = r.get(t.ID),
            bookingId = BookingId(r.get(t.BOOKING_ID)),
            kind = OfficeCollectionKind.valueOf(r.get(t.KIND)),
            method = CollectionMethod.valueOf(r.get(t.METHOD)),
            amount = Money(r.get(t.AMOUNT_EUR).setScale(Money.REQUIRED_SCALE)),
            currencyPaid = PaidCurrency.valueOf(r.get(t.CURRENCY_PAID)),
            amountPaid = Money(r.get(t.AMOUNT_PAID).setScale(Money.REQUIRED_SCALE)),
            fxRate =
                rateId?.let {
                    FxRate(
                        id = it,
                        rateDate = checkNotNull(r.get(fx.RATE_DATE)),
                        egpPerEur = checkNotNull(r.get(t.FX_RATE)),
                        setByUserId = r.get(fx.SET_BY_USER_ID),
                        setAt = checkNotNull(r.get(fx.SET_AT)).toInstant(),
                    )
                },
            reference = r.get(t.REFERENCE),
            reversesCollectionId = r.get(t.REVERSES_COLLECTION_ID),
            reason = r.get(t.REASON),
            recordedByUserId = r.get(t.RECORDED_BY_USER_ID),
            clientRequestId = r.get(t.CLIENT_REQUEST_ID),
            recordedAt = r.get(t.RECORDED_AT).toInstant(),
        )
    }
}
