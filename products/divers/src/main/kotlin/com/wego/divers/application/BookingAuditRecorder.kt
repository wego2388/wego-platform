package com.wego.divers.application

import com.wego.divers.domain.BookingId
import java.time.Instant
import java.util.UUID

interface BookingAuditRecorder {
    fun recordBookingCreated(
        bookingId: BookingId,
        actorUserId: UUID,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordBookingCancelled(
        bookingId: BookingId,
        actorUserId: UUID,
        occurredAt: Instant,
        reason: String,
        correlationId: UUID?,
    )

    fun recordPaymentMarkedPaid(
        bookingId: BookingId,
        actorUserId: UUID,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordPaymentRefunded(
        bookingId: BookingId,
        actorUserId: UUID,
        occurredAt: Instant,
        reason: String,
        correlationId: UUID?,
    )
}
