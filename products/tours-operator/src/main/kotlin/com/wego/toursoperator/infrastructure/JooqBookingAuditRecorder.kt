package com.wego.toursoperator.infrastructure

import com.wego.generated.jooq.tables.IdentityUser.IDENTITY_USER
import com.wego.generated.jooq.tables.ToursOperatorBookingAuditEvent.TOURS_OPERATOR_BOOKING_AUDIT_EVENT
import com.wego.toursoperator.application.BookingAuditRecorder
import com.wego.toursoperator.application.BookingHistoryEntry
import com.wego.toursoperator.application.BookingHistoryQuery
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.BookingStatus
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Component("toursOperatorBookingAuditRecorder")
class JooqBookingAuditRecorder(
    private val dsl: DSLContext,
) : BookingAuditRecorder,
    BookingHistoryQuery {
    @Transactional(readOnly = true)
    override fun findByBooking(bookingId: BookingId): List<BookingHistoryEntry> {
        val t = TOURS_OPERATOR_BOOKING_AUDIT_EVENT
        return dsl
            .select(t.EVENT_TYPE, t.FROM_STATUS, t.TO_STATUS, t.REASON, t.ACTOR_USER_ID, t.OCCURRED_AT, IDENTITY_USER.EMAIL)
            .from(t)
            .leftJoin(IDENTITY_USER)
            .on(IDENTITY_USER.ID.eq(t.ACTOR_USER_ID))
            .where(t.BOOKING_ID.eq(bookingId.value))
            .orderBy(t.OCCURRED_AT.asc(), t.ID.asc())
            .fetch { r ->
                BookingHistoryEntry(
                    eventType = checkNotNull(r[t.EVENT_TYPE]),
                    fromStatus = r[t.FROM_STATUS],
                    toStatus = r[t.TO_STATUS],
                    reason = r[t.REASON],
                    actorUserId = r[t.ACTOR_USER_ID],
                    actorEmail = r[IDENTITY_USER.EMAIL],
                    occurredAt = checkNotNull(r[t.OCCURRED_AT]).toInstant(),
                )
            }
    }

    @Transactional
    override fun recordCreated(
        bookingId: BookingId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    ) = insert(bookingId, "BOOKING_CREATED", null, null, null, actorUserId, occurredAt, correlationId)

    @Transactional
    override fun recordConfirmed(
        bookingId: BookingId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    ) = insert(bookingId, "BOOKING_CONFIRMED", "NEW", "CONFIRMED", null, actorUserId, occurredAt, correlationId)

    @Transactional
    override fun recordCancelled(
        bookingId: BookingId,
        fromStatus: BookingStatus,
        reason: String,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    ) = insert(bookingId, "BOOKING_CANCELLED", fromStatus.name, "CANCELLED", reason, actorUserId, occurredAt, correlationId)

    @Transactional
    override fun recordCompleted(
        bookingId: BookingId,
        actorUserId: UUID,
        occurredAt: Instant,
        correlationId: UUID?,
    ) = insert(bookingId, "BOOKING_COMPLETED", "CONFIRMED", "COMPLETED", null, actorUserId, occurredAt, correlationId)

    @Transactional
    override fun recordExpired(
        bookingId: BookingId,
        occurredAt: Instant,
    ) = insert(bookingId, "BOOKING_EXPIRED", "NEW", "EXPIRED", null, null, occurredAt, null)

    private fun insert(
        bookingId: BookingId,
        eventType: String,
        fromStatus: String?,
        toStatus: String?,
        reason: String?,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    ) {
        val t = TOURS_OPERATOR_BOOKING_AUDIT_EVENT
        dsl
            .insertInto(t)
            .set(t.ID, UUID.randomUUID())
            .set(t.BOOKING_ID, bookingId.value)
            .set(t.EVENT_TYPE, eventType)
            .set(t.FROM_STATUS, fromStatus)
            .set(t.TO_STATUS, toStatus)
            .set(t.REASON, reason)
            .set(t.ACTOR_USER_ID, actorUserId)
            .set(t.OCCURRED_AT, OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
            .set(t.CORRELATION_ID, correlationId)
            .execute()
    }
}
