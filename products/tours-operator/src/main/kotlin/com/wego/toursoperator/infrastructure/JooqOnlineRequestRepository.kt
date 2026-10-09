package com.wego.toursoperator.infrastructure

import com.wego.generated.jooq.tables.ToursOperatorOnlineRequest.TOURS_OPERATOR_ONLINE_REQUEST
import com.wego.generated.jooq.tables.ToursOperatorOnlineRequestAudit.TOURS_OPERATOR_ONLINE_REQUEST_AUDIT
import com.wego.generated.jooq.tables.records.ToursOperatorOnlineRequestRecord
import com.wego.toursoperator.application.OnlineRequestRepository
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.CustomerContact
import com.wego.toursoperator.domain.Money
import com.wego.toursoperator.domain.OnlineRequest
import com.wego.toursoperator.domain.OnlineRequestAudit
import com.wego.toursoperator.domain.OnlineRequestDetails
import com.wego.toursoperator.domain.OnlineRequestStatus
import com.wego.toursoperator.domain.TimeSlot
import com.wego.toursoperator.domain.TourId
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class JooqOnlineRequestRepository(
    private val dsl: DSLContext,
) : OnlineRequestRepository {
    private val t = TOURS_OPERATOR_ONLINE_REQUEST
    private val a = TOURS_OPERATOR_ONLINE_REQUEST_AUDIT

    override fun insert(request: OnlineRequest): Boolean {
        val d = request.details
        return dsl
            .insertInto(t)
            .set(t.ID, request.id)
            .set(t.REFERENCE, request.reference)
            .set(t.PAYLOAD_HASH, request.payloadHash)
            .set(t.TOUR_ID, d.tourId.value)
            .set(t.PREFERRED_DATE, d.preferredDate)
            .set(t.PREFERRED_TIME, d.preferredTime?.name)
            .set(t.ADULTS_COUNT, d.adultsCount)
            .set(t.CHILDREN_COUNT, d.childrenCount)
            .set(t.PRICE_OPTION_CODE, d.priceOptionCode)
            .set(t.UNIT_COUNT, d.unitCount)
            .set(t.ESTIMATED_TOTAL, request.estimatedTotal.amount)
            .set(t.FULL_NAME, d.customer.fullName)
            .set(t.PHONE, d.customer.phone)
            .set(t.NATIONALITY, d.customer.nationality)
            .set(t.EMAIL, d.customer.email)
            .set(t.HOTEL_NAME, d.hotelName)
            .set(t.SPECIAL_REQUESTS, d.specialRequests)
            .set(t.LOCALE, d.locale)
            .set(t.STATUS, request.status.name)
            .set(t.REVISION, request.revision)
            .set(t.CREATED_AT, request.createdAt.offset())
            .set(t.UPDATED_AT, request.updatedAt.offset())
            .onConflict(t.ID)
            .doNothing()
            .execute() == 1
    }

    @Transactional(readOnly = true)
    override fun find(
        id: UUID,
        lock: Boolean,
    ): OnlineRequest? {
        val query = dsl.selectFrom(t).where(t.ID.eq(id))
        return (if (lock) query.forUpdate().fetchOne() else query.fetchOne())?.domain()
    }

    @Transactional(readOnly = true)
    override fun list(
        status: OnlineRequestStatus?,
        page: Int,
        size: Int,
    ): List<OnlineRequest> =
        dsl
            .selectFrom(t)
            .where(status?.let { t.STATUS.eq(it.name) } ?: DSL.noCondition())
            .orderBy(t.CREATED_AT.desc(), t.ID.asc())
            .limit(size)
            .offset(page * size)
            .fetch { it.domain() }

    @Transactional(readOnly = true)
    override fun countOpen(): Int = dsl.fetchCount(t, t.STATUS.`in`("NEW", "IN_PROGRESS"))

    override fun save(request: OnlineRequest) {
        check(
            dsl
                .update(t)
                .set(t.STATUS, request.status.name)
                .set(t.REVISION, request.revision)
                .set(t.BOOKING_ID, request.bookingId?.value)
                .set(t.UPDATED_AT, request.updatedAt.offset())
                .where(t.ID.eq(request.id))
                .execute() == 1,
        )
    }

    override fun audit(
        request: OnlineRequest,
        actorUserId: UUID?,
    ) {
        dsl
            .insertInto(a)
            .set(a.ID, UUID.randomUUID())
            .set(a.REQUEST_ID, request.id)
            .set(a.STATUS, request.status.name)
            .set(a.ACTOR_USER_ID, actorUserId)
            .set(a.OCCURRED_AT, request.updatedAt.offset())
            .execute()
    }

    @Transactional(readOnly = true)
    override fun history(id: UUID): List<OnlineRequestAudit> =
        dsl.selectFrom(a).where(a.REQUEST_ID.eq(id)).orderBy(a.OCCURRED_AT.asc(), a.ID.asc()).fetch {
            OnlineRequestAudit(OnlineRequestStatus.valueOf(it.status), it.actorUserId, it.occurredAt.toInstant())
        }

    private fun ToursOperatorOnlineRequestRecord.domain() =
        OnlineRequest(
            id,
            reference,
            payloadHash,
            OnlineRequestDetails(
                TourId(tourId),
                preferredDate,
                preferredTime?.let(TimeSlot::valueOf),
                adultsCount,
                childrenCount,
                priceOptionCode,
                unitCount,
                CustomerContact(fullName, phone, nationality, email),
                hotelName,
                specialRequests,
                locale,
            ),
            Money(estimatedTotal),
            OnlineRequestStatus.valueOf(status),
            revision,
            bookingId?.let(::BookingId),
            createdAt.toInstant(),
            updatedAt.toInstant(),
        )
}

private fun Instant.offset(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)
