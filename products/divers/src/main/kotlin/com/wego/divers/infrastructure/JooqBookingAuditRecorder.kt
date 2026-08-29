package com.wego.divers.infrastructure

import com.wego.divers.application.BookingAuditRecorder
import com.wego.divers.domain.BookingId
import com.wego.divers.domain.BookingStatus
import com.wego.divers.domain.PaymentStatus
import com.wego.generated.jooq.tables.DiversBookingAuditEvent.DIVERS_BOOKING_AUDIT_EVENT
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Component
class JooqBookingAuditRecorder(
    private val dsl: DSLContext,
) : BookingAuditRecorder {
    @Transactional
    override fun recordBookingCreated(
        bookingId: BookingId,
        actorUserId: UUID,
        occurredAt: Instant,
        correlationId: UUID?,
    ) = insert(bookingId, "BOOKING_CREATED", actorUserId, occurredAt, null, BookingStatus.CONFIRMED.name, null, correlationId)

    @Transactional
    override fun recordBookingCancelled(
        bookingId: BookingId,
        actorUserId: UUID,
        occurredAt: Instant,
        reason: String,
        correlationId: UUID?,
    ) = insert(
        bookingId,
        "BOOKING_CANCELLED",
        actorUserId,
        occurredAt,
        BookingStatus.CONFIRMED.name,
        BookingStatus.CANCELLED.name,
        reason,
        correlationId,
    )

    @Transactional
    override fun recordPaymentMarkedPaid(
        bookingId: BookingId,
        actorUserId: UUID,
        occurredAt: Instant,
        correlationId: UUID?,
    ) = insert(
        bookingId,
        "PAYMENT_MARKED_PAID",
        actorUserId,
        occurredAt,
        PaymentStatus.UNPAID.name,
        PaymentStatus.PAID.name,
        null,
        correlationId,
    )

    @Transactional
    override fun recordPaymentRefunded(
        bookingId: BookingId,
        actorUserId: UUID,
        occurredAt: Instant,
        reason: String,
        correlationId: UUID?,
    ) = insert(
        bookingId,
        "PAYMENT_REFUNDED",
        actorUserId,
        occurredAt,
        PaymentStatus.PAID.name,
        PaymentStatus.REFUNDED.name,
        reason,
        correlationId,
    )

    private fun insert(
        bookingId: BookingId,
        eventType: String,
        actorUserId: UUID,
        occurredAt: Instant,
        fromStatus: String?,
        toStatus: String?,
        reason: String?,
        correlationId: UUID?,
    ) {
        dsl
            .insertInto(DIVERS_BOOKING_AUDIT_EVENT)
            .set(DIVERS_BOOKING_AUDIT_EVENT.ID, UUID.randomUUID())
            .set(DIVERS_BOOKING_AUDIT_EVENT.BOOKING_ID, bookingId.value)
            .set(DIVERS_BOOKING_AUDIT_EVENT.OCCURRED_AT, OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
            .set(DIVERS_BOOKING_AUDIT_EVENT.EVENT_TYPE, eventType)
            .set(DIVERS_BOOKING_AUDIT_EVENT.ACTOR_USER_ID, actorUserId)
            .set(DIVERS_BOOKING_AUDIT_EVENT.FROM_STATUS, fromStatus)
            .set(DIVERS_BOOKING_AUDIT_EVENT.TO_STATUS, toStatus)
            .set(DIVERS_BOOKING_AUDIT_EVENT.REASON, reason)
            .set(DIVERS_BOOKING_AUDIT_EVENT.CORRELATION_ID, correlationId)
            .execute()
    }
}
