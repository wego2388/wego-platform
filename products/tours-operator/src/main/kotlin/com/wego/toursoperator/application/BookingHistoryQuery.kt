package com.wego.toursoperator.application

import com.wego.toursoperator.domain.BookingId
import java.time.Instant
import java.util.UUID

/** One append-only booking lifecycle event, as shown to staff. */
data class BookingHistoryEntry(
    val eventType: String,
    val fromStatus: String?,
    val toStatus: String?,
    val reason: String?,
    val actorUserId: UUID?,
    val actorEmail: String?,
    val occurredAt: Instant,
)

/** Read side of the booking audit trail, oldest event first. */
interface BookingHistoryQuery {
    fun findByBooking(bookingId: BookingId): List<BookingHistoryEntry>
}
