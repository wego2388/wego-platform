package com.wego.toursoperator.application

import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.BookingStatus
import java.time.Instant
import java.util.UUID

interface BookingAuditRecorder {
    fun recordCreated(
        bookingId: BookingId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordConfirmed(
        bookingId: BookingId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordCancelled(
        bookingId: BookingId,
        fromStatus: BookingStatus,
        reason: String,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordCompleted(
        bookingId: BookingId,
        actorUserId: UUID,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordExpired(
        bookingId: BookingId,
        occurredAt: Instant,
    )
}
