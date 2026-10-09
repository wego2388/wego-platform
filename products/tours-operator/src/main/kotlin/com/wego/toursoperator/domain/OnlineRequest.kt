package com.wego.toursoperator.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class OnlineRequestStatus { NEW, IN_PROGRESS, CONVERTED, CLOSED }

/** Immutable guest intent. A preferred day/time is not a confirmed departure. */
data class OnlineRequestDetails(
    val tourId: TourId,
    val preferredDate: LocalDate,
    val preferredTime: TimeSlot?,
    val adultsCount: Int,
    val childrenCount: Int,
    val priceOptionCode: String?,
    val unitCount: Int?,
    val customer: CustomerContact,
    val hotelName: String,
    val specialRequests: String?,
    val locale: String,
)

data class OnlineRequest(
    val id: UUID,
    val reference: String,
    val payloadHash: String,
    val details: OnlineRequestDetails,
    val estimatedTotal: Money,
    val status: OnlineRequestStatus,
    val revision: Int,
    val bookingId: BookingId?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class OnlineRequestAudit(
    val status: OnlineRequestStatus,
    val actorUserId: UUID?,
    val occurredAt: Instant,
)
