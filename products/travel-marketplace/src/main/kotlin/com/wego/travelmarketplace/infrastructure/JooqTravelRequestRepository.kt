package com.wego.travelmarketplace.infrastructure

import com.wego.generated.jooq.tables.TravelRequest.TRAVEL_REQUEST
import com.wego.generated.jooq.tables.records.TravelRequestRecord
import com.wego.travelmarketplace.application.TravelRequestRepository
import com.wego.travelmarketplace.domain.LocalizedText
import com.wego.travelmarketplace.domain.Money
import com.wego.travelmarketplace.domain.PriceBasis
import com.wego.travelmarketplace.domain.ServiceId
import com.wego.travelmarketplace.domain.TravelRequest
import com.wego.travelmarketplace.domain.TravelRequestCancelReason
import com.wego.travelmarketplace.domain.TravelRequestCustomer
import com.wego.travelmarketplace.domain.TravelRequestId
import com.wego.travelmarketplace.domain.TravelRequestReference
import com.wego.travelmarketplace.domain.TravelRequestSourceChannel
import com.wego.travelmarketplace.domain.TravelRequestStatus
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqTravelRequestRepository(
    private val dsl: DSLContext,
) : TravelRequestRepository {
    @Transactional(readOnly = true)
    override fun findById(id: TravelRequestId): TravelRequest? =
        dsl
            .selectFrom(TRAVEL_REQUEST)
            .where(TRAVEL_REQUEST.ID.eq(id.value))
            .fetchOne()
            ?.let(::toDomain)

    @Transactional
    override fun findByIdForUpdate(id: TravelRequestId): TravelRequest? =
        dsl
            .selectFrom(TRAVEL_REQUEST)
            .where(TRAVEL_REQUEST.ID.eq(id.value))
            .forUpdate()
            .fetchOne()
            ?.let(::toDomain)

    @Transactional(readOnly = true)
    override fun findByReference(reference: TravelRequestReference): TravelRequest? =
        dsl
            .selectFrom(TRAVEL_REQUEST)
            .where(TRAVEL_REQUEST.REFERENCE.eq(reference.value))
            .fetchOne()
            ?.let(::toDomain)

    @Transactional(readOnly = true)
    override fun findByIdempotencyKey(idempotencyKey: String): TravelRequest? =
        dsl
            .selectFrom(TRAVEL_REQUEST)
            .where(TRAVEL_REQUEST.IDEMPOTENCY_KEY.eq(idempotencyKey))
            .fetchOne()
            ?.let(::toDomain)

    @Transactional(readOnly = true)
    override fun findAll(
        status: TravelRequestStatus?,
        limit: Int,
        offset: Int,
    ): List<TravelRequest> {
        var condition = DSL.noCondition()
        if (status != null) condition = condition.and(TRAVEL_REQUEST.STATUS.eq(status.name))
        return dsl
            .selectFrom(TRAVEL_REQUEST)
            .where(condition)
            .orderBy(TRAVEL_REQUEST.CREATED_AT.desc(), TRAVEL_REQUEST.ID)
            .limit(limit)
            .offset(offset)
            .fetch()
            .map(::toDomain)
    }

    @Transactional(readOnly = true)
    override fun findExpirable(asOfDate: java.time.LocalDate): List<TravelRequest> =
        dsl
            .selectFrom(TRAVEL_REQUEST)
            .where(TRAVEL_REQUEST.STATUS.`in`(TravelRequestStatus.NEW.name, TravelRequestStatus.IN_REVIEW.name))
            .and(TRAVEL_REQUEST.REQUESTED_DATE.lt(asOfDate))
            .orderBy(TRAVEL_REQUEST.REQUESTED_DATE)
            .fetch()
            .map(::toDomain)

    @Transactional
    override fun save(request: TravelRequest) {
        dsl
            .insertInto(TRAVEL_REQUEST)
            .set(TRAVEL_REQUEST.ID, request.id.value)
            .set(TRAVEL_REQUEST.REFERENCE, request.reference.value)
            .set(TRAVEL_REQUEST.SERVICE_ID, request.serviceId.value)
            .set(TRAVEL_REQUEST.SERVICE_OPTION_ID, request.serviceOptionId)
            .set(TRAVEL_REQUEST.SERVICE_NAME_EN, request.serviceName.en)
            .set(TRAVEL_REQUEST.SERVICE_NAME_AR, request.serviceName.ar)
            .set(TRAVEL_REQUEST.OPTION_LABEL_EN, request.optionLabel.en)
            .set(TRAVEL_REQUEST.OPTION_LABEL_AR, request.optionLabel.ar)
            .set(TRAVEL_REQUEST.PRICE_AMOUNT, request.price.amount)
            .set(TRAVEL_REQUEST.PRICE_CURRENCY, request.price.currencyCode)
            .set(TRAVEL_REQUEST.PRICE_BASIS, request.priceBasis.name)
            .set(TRAVEL_REQUEST.CANCELLATION_POLICY_EN, request.cancellationPolicy.en)
            .set(TRAVEL_REQUEST.CANCELLATION_POLICY_AR, request.cancellationPolicy.ar)
            .set(TRAVEL_REQUEST.REQUESTED_DATE, request.requestedDate)
            .set(TRAVEL_REQUEST.REQUESTED_TIME, request.requestedTime)
            .set(TRAVEL_REQUEST.ADULTS, request.adults)
            .set(TRAVEL_REQUEST.CHILDREN, request.children)
            .set(TRAVEL_REQUEST.HOTEL_OR_PICKUP, request.hotelOrPickup)
            .set(TRAVEL_REQUEST.LOCALE, request.locale)
            .set(TRAVEL_REQUEST.NOTES, request.notes)
            .set(TRAVEL_REQUEST.SOURCE_CHANNEL, request.sourceChannel.name)
            .set(TRAVEL_REQUEST.CUSTOMER_NAME, request.customer.name)
            .set(TRAVEL_REQUEST.CUSTOMER_PHONE, request.customer.phone)
            .set(TRAVEL_REQUEST.CUSTOMER_EMAIL, request.customer.email)
            .set(TRAVEL_REQUEST.IDEMPOTENCY_KEY, request.idempotencyKey)
            .set(TRAVEL_REQUEST.STATUS, request.status.name)
            .set(TRAVEL_REQUEST.CREATED_AT, toOffset(request.createdAt))
            .set(TRAVEL_REQUEST.CONFIRMED_AT, request.confirmedAt?.let(::toOffset))
            .set(TRAVEL_REQUEST.COMPLETED_AT, request.completedAt?.let(::toOffset))
            .set(TRAVEL_REQUEST.CANCELLED_AT, request.cancelledAt?.let(::toOffset))
            .set(TRAVEL_REQUEST.CANCEL_REASON, request.cancelReason?.name)
            .set(TRAVEL_REQUEST.CANCEL_DETAIL, request.cancelDetail)
            .set(TRAVEL_REQUEST.EXPIRED_AT, request.expiredAt?.let(::toOffset))
            .onConflict(TRAVEL_REQUEST.ID)
            .doUpdate()
            .set(TRAVEL_REQUEST.STATUS, request.status.name)
            .set(TRAVEL_REQUEST.CONFIRMED_AT, request.confirmedAt?.let(::toOffset))
            .set(TRAVEL_REQUEST.COMPLETED_AT, request.completedAt?.let(::toOffset))
            .set(TRAVEL_REQUEST.CANCELLED_AT, request.cancelledAt?.let(::toOffset))
            .set(TRAVEL_REQUEST.CANCEL_REASON, request.cancelReason?.name)
            .set(TRAVEL_REQUEST.CANCEL_DETAIL, request.cancelDetail)
            .set(TRAVEL_REQUEST.EXPIRED_AT, request.expiredAt?.let(::toOffset))
            .execute()
    }

    private fun toDomain(record: TravelRequestRecord): TravelRequest =
        TravelRequest(
            id = TravelRequestId(record.id),
            reference = TravelRequestReference(record.reference),
            serviceId = ServiceId(record.serviceId),
            serviceOptionId = record.serviceOptionId,
            serviceName = LocalizedText(record.serviceNameEn, record.serviceNameAr),
            optionLabel = LocalizedText(record.optionLabelEn, record.optionLabelAr),
            price = Money(record.priceAmount, record.priceCurrency),
            priceBasis = PriceBasis.valueOf(record.priceBasis),
            cancellationPolicy = LocalizedText(record.cancellationPolicyEn, record.cancellationPolicyAr),
            requestedDate = record.requestedDate,
            requestedTime = record.requestedTime,
            adults = record.adults,
            children = record.children,
            hotelOrPickup = record.hotelOrPickup,
            locale = record.locale,
            notes = record.notes,
            sourceChannel = TravelRequestSourceChannel.valueOf(record.sourceChannel),
            customer = TravelRequestCustomer(record.customerName, record.customerPhone, record.customerEmail),
            idempotencyKey = record.idempotencyKey,
            status = TravelRequestStatus.valueOf(record.status),
            createdAt = record.createdAt.toInstant(),
            confirmedAt = record.confirmedAt?.toInstant(),
            completedAt = record.completedAt?.toInstant(),
            cancelledAt = record.cancelledAt?.toInstant(),
            cancelReason = record.cancelReason?.let(TravelRequestCancelReason::valueOf),
            cancelDetail = record.cancelDetail,
            expiredAt = record.expiredAt?.toInstant(),
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
