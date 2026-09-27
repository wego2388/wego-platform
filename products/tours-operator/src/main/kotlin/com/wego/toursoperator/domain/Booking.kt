package com.wego.toursoperator.domain

import java.time.Instant
import java.time.LocalDate

/**
 * A customer booking for a specific tour slot.
 *
 * State machine:
 *   NEW → CONFIRMED (payment succeeds within 30 min)
 *   NEW → EXPIRED   (payment timeout — scheduled job)
 *   CONFIRMED → COMPLETED (after tour date, staff action)
 *   CONFIRMED → CANCELLED (customer or operator)
 *   NEW → CANCELLED (operator cancels before payment)
 *
 * The booking reference (STR-YYYY-NNNN) is the public identifier.
 * The UUID id is internal only and never exposed in public responses.
 *
 * Pricing is snapshotted at NEW creation time and is immutable.
 * totalEur on the booking record is the authoritative amount for payment
 * and refund — it never changes even if tour prices change later.
 */
class Booking(
    val id: BookingId,
    /** Human-readable public reference, e.g. STR-2026-4821 */
    val reference: String,
    val tourId: TourId,
    val slotId: TourSlotId,
    val tourDate: LocalDate,
    val timeSlot: TimeSlot,
    val pricing: BookingPricing,
    val customer: CustomerContact,
    val hotelName: String,
    val hotelRoom: String?,
    val specialRequests: String?,
    /** Locale used during booking — drives notification language. */
    val locale: String,
    status: BookingStatus,
    val createdAt: Instant,
    confirmedAt: Instant?,
    cancelledAt: Instant?,
    cancellationReason: String?,
    completedAt: Instant?,
    expiredAt: Instant?,
) {
    var status: BookingStatus = status
        private set

    var confirmedAt: Instant? = confirmedAt
        private set

    var cancelledAt: Instant? = cancelledAt
        private set

    var cancellationReason: String? = cancellationReason
        private set

    var completedAt: Instant? = completedAt
        private set

    var expiredAt: Instant? = expiredAt
        private set

    init {
        require(reference.matches(REFERENCE_FORMAT)) {
            "Booking reference must match STR-YYYY-NNNN format (e.g. STR-2026-4821)"
        }
        require(hotelName.isNotBlank()) { "Hotel name must not be blank" }
        require(locale.matches(LOCALE_FORMAT)) { "Locale must be a 2-3 letter ISO code" }
        validateStatusConsistency()
    }

    private fun validateStatusConsistency() {
        // confirmedAt is a historical timestamp: it must be set once the booking
        // has ever been CONFIRMED and is never cleared on subsequent transitions
        // (COMPLETED, CANCELLED after confirm). It must NOT be set for bookings
        // that were never confirmed (NEW, EXPIRED).
        val wasEverConfirmed = status == BookingStatus.CONFIRMED ||
            status == BookingStatus.COMPLETED ||
            (status == BookingStatus.CANCELLED && confirmedAt != null)
        require(wasEverConfirmed == (confirmedAt != null)) {
            "confirmedAt must be set for bookings that passed through CONFIRMED, and absent for those that did not (status: $status)"
        }
        require((status == BookingStatus.CANCELLED) == (cancelledAt != null)) {
            "cancelledAt must be set if and only if status is CANCELLED"
        }
        require((status == BookingStatus.CANCELLED) == !cancellationReason.isNullOrBlank()) {
            "cancellationReason must be set if and only if status is CANCELLED"
        }
        require((status == BookingStatus.COMPLETED) == (completedAt != null)) {
            "completedAt must be set if and only if status is COMPLETED"
        }
        require((status == BookingStatus.EXPIRED) == (expiredAt != null)) {
            "expiredAt must be set if and only if status is EXPIRED"
        }
    }

    /** Payment succeeded — NEW → CONFIRMED. */
    fun confirm(now: Instant) {
        require(status == BookingStatus.NEW) { "Only a NEW booking can be confirmed (current: $status)" }
        status = BookingStatus.CONFIRMED
        confirmedAt = now
    }

    /** Tour completed — CONFIRMED → COMPLETED. */
    fun complete(now: Instant) {
        require(status == BookingStatus.CONFIRMED) { "Only a CONFIRMED booking can be completed (current: $status)" }
        status = BookingStatus.COMPLETED
        completedAt = now
    }

    /**
     * Cancellation by customer or operator.
     * NEW and CONFIRMED bookings can be cancelled.
     */
    fun cancel(
        now: Instant,
        reason: String,
    ) {
        require(status == BookingStatus.NEW || status == BookingStatus.CONFIRMED) {
            "Only NEW or CONFIRMED bookings can be cancelled (current: $status)"
        }
        require(reason.isNotBlank()) { "Cancellation requires a non-blank reason" }
        status = BookingStatus.CANCELLED
        cancelledAt = now
        cancellationReason = reason
    }

    /** Payment window elapsed — NEW → EXPIRED. */
    fun expire(now: Instant) {
        require(status == BookingStatus.NEW) { "Only a NEW booking can expire (current: $status)" }
        status = BookingStatus.EXPIRED
        expiredAt = now
    }

    companion object {
        private val REFERENCE_FORMAT = Regex("^STR-\\d{4}-\\d+$")
        private val LOCALE_FORMAT = Regex("^[a-z]{2,3}$")

        fun createNew(
            id: BookingId,
            reference: String,
            tourId: TourId,
            slotId: TourSlotId,
            tourDate: LocalDate,
            timeSlot: TimeSlot,
            pricing: BookingPricing,
            customer: CustomerContact,
            hotelName: String,
            hotelRoom: String?,
            specialRequests: String?,
            locale: String,
            now: Instant,
        ): Booking =
            Booking(
                id = id,
                reference = reference,
                tourId = tourId,
                slotId = slotId,
                tourDate = tourDate,
                timeSlot = timeSlot,
                pricing = pricing,
                customer = customer,
                hotelName = hotelName,
                hotelRoom = hotelRoom,
                specialRequests = specialRequests,
                locale = locale,
                status = BookingStatus.NEW,
                createdAt = now,
                confirmedAt = null,
                cancelledAt = null,
                cancellationReason = null,
                completedAt = null,
                expiredAt = null,
            )
    }
}
