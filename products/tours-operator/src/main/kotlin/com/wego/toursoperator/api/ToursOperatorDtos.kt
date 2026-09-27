package com.wego.toursoperator.api

import com.wego.toursoperator.domain.BookingStatus
import com.wego.toursoperator.domain.TimeSlot
import com.wego.toursoperator.domain.TourCategory
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ── Shared ────────────────────────────────────────────────────────────────────

data class MoneyResponse(
    val amount: String,
    val currencyCode: String = "EUR",
)

data class ErrorResponse(
    val error: String,
)

// ── Tour responses ────────────────────────────────────────────────────────────

data class TourSummaryResponse(
    val id: UUID,
    val slug: String,
    val category: TourCategory,
    val durationText: String,
    val priceAdult: MoneyResponse,
    val priceChild: MoneyResponse?,
    val capacity: Int,
    val availableTimeSlots: List<TimeSlot>,
    val sortOrder: Int,
    val isActive: Boolean,
)

// ── Slot responses ────────────────────────────────────────────────────────────

data class TourSlotResponse(
    val id: UUID,
    val tourId: UUID,
    val date: LocalDate,
    val timeSlot: TimeSlot,
    val capacity: Int,
    val bookedCount: Int,
    val available: Int,
    val isBlocked: Boolean,
)

// ── Booking requests ──────────────────────────────────────────────────────────

data class BookingCustomerRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val fullName: String,
    @field:NotBlank
    @field:Size(max = 32)
    val phone: String,
    @field:Pattern(regexp = "^[A-Z]{2}$")
    val nationality: String,
    @field:Email
    @field:Size(max = 320)
    val email: String?,
)

data class CreateBookingRequest(
    val slotId: UUID,
    @field:Min(1)
    val adultsCount: Int,
    @field:Min(0)
    val childrenCount: Int,
    @field:Valid
    val customer: BookingCustomerRequest,
    @field:NotBlank
    @field:Size(max = 200)
    val hotelName: String,
    @field:Size(max = 32)
    val hotelRoom: String?,
    @field:Size(max = 4000)
    val specialRequests: String?,
    @field:Pattern(regexp = "^(en|ar|ru|it)$")
    val locale: String,
)

data class CancelBookingRequest(
    @field:NotBlank
    @field:Size(max = 1000)
    val reason: String,
)

// ── Booking responses ─────────────────────────────────────────────────────────

data class BookingCustomerResponse(
    val fullName: String,
    val phone: String,
    val nationality: String,
    val email: String?,
)

data class BookingResponse(
    val id: UUID,
    val reference: String,
    val tourId: UUID,
    val slotId: UUID,
    val tourDate: LocalDate,
    val timeSlot: TimeSlot,
    val adultsCount: Int,
    val childrenCount: Int,
    val priceAdult: MoneyResponse,
    val priceChild: MoneyResponse?,
    val totalPrice: MoneyResponse,
    val customer: BookingCustomerResponse,
    val hotelName: String,
    val hotelRoom: String?,
    val specialRequests: String?,
    val locale: String,
    val status: BookingStatus,
    val createdAt: Instant,
    val confirmedAt: Instant?,
    val cancelledAt: Instant?,
    val cancellationReason: String?,
    val completedAt: Instant?,
    val expiredAt: Instant?,
)
