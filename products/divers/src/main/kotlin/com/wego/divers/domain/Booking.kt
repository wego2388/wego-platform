package com.wego.divers.domain

import java.time.Instant
import java.util.UUID

sealed class PaymentTransitionResult {
    /** The transition happened; caller must record audit/outbox. */
    data object Applied : PaymentTransitionResult()

    /** Already in the requested state — a safe replay, not an error, but no new audit/outbox. */
    data object AlreadyInTargetState : PaymentTransitionResult()

    /** The transition is not allowed from the booking's current state. */
    data class Rejected(
        val message: String,
    ) : PaymentTransitionResult()
}

class Booking(
    val id: BookingId,
    val offeringId: OfferingId,
    val partySize: Int,
    val customer: CustomerContact,
    status: BookingStatus,
    paymentStatus: PaymentStatus,
    val pricing: BookingPricing,
    val idempotencyKey: String,
    val idempotencyFingerprint: String,
    val createdByUserId: UUID,
    val createdAt: Instant,
    cancelledAt: Instant?,
    cancellationReason: String?,
) {
    var status: BookingStatus = status
        private set

    var paymentStatus: PaymentStatus = paymentStatus
        private set

    var cancelledAt: Instant? = cancelledAt
        private set

    var cancellationReason: String? = cancellationReason
        private set

    init {
        require(partySize > 0) { "Party size must be positive" }
        require(idempotencyKey.isNotBlank() && idempotencyKey.length <= MAX_IDEMPOTENCY_KEY_LENGTH) {
            "Idempotency key must be between 1 and $MAX_IDEMPOTENCY_KEY_LENGTH characters"
        }
        require(FINGERPRINT_FORMAT.matches(idempotencyFingerprint)) {
            "Idempotency fingerprint must be a 64-character lowercase hex SHA-256 digest"
        }
        require((status == BookingStatus.CANCELLED) == (cancelledAt != null)) {
            "cancelledAt must be set if and only if the booking is cancelled"
        }
        require((status == BookingStatus.CANCELLED) == !cancellationReason.isNullOrBlank()) {
            "cancellationReason must be a non-blank value if and only if the booking is cancelled"
        }
    }

    fun cancel(
        now: Instant,
        reason: String,
    ) {
        require(status == BookingStatus.CONFIRMED) { "Only a confirmed booking can be cancelled" }
        require(reason.isNotBlank()) { "Cancellation requires a non-blank reason" }
        status = BookingStatus.CANCELLED
        cancelledAt = now
        cancellationReason = reason
    }

    /**
     * UNPAID -> PAID only. A cancelled booking can never be marked paid,
     * regardless of its current payment status.
     */
    fun markPaid(): PaymentTransitionResult {
        if (status == BookingStatus.CANCELLED) {
            return PaymentTransitionResult.Rejected("A cancelled booking cannot be marked paid")
        }
        return when (paymentStatus) {
            PaymentStatus.PAID -> PaymentTransitionResult.AlreadyInTargetState
            PaymentStatus.UNPAID -> {
                paymentStatus = PaymentStatus.PAID
                PaymentTransitionResult.Applied
            }
            PaymentStatus.REFUNDED -> PaymentTransitionResult.Rejected("A refunded booking cannot be marked paid again")
        }
    }

    /**
     * PAID -> REFUNDED only, and only with a non-blank reason. Allowed even
     * on a cancelled booking — cancellation and payment are independent
     * axes; a customer who cancelled after paying still needs a refund.
     */
    fun refund(reason: String): PaymentTransitionResult {
        require(reason.isNotBlank()) { "Refund requires a non-blank reason" }
        return when (paymentStatus) {
            PaymentStatus.REFUNDED -> PaymentTransitionResult.AlreadyInTargetState
            PaymentStatus.PAID -> {
                paymentStatus = PaymentStatus.REFUNDED
                PaymentTransitionResult.Applied
            }
            PaymentStatus.UNPAID -> PaymentTransitionResult.Rejected("Only a paid booking can be refunded")
        }
    }

    companion object {
        const val MAX_IDEMPOTENCY_KEY_LENGTH = 128
        private val FINGERPRINT_FORMAT = Regex("^[a-f0-9]{64}$")

        // Bookings confirm immediately on creation — there is no PENDING
        // state and no separate confirm step (owner's explicit decision).
        fun confirm(
            id: BookingId,
            offeringId: OfferingId,
            partySize: Int,
            customer: CustomerContact,
            pricing: BookingPricing,
            idempotencyKey: String,
            idempotencyFingerprint: String,
            createdByUserId: UUID,
            now: Instant,
        ): Booking =
            Booking(
                id = id,
                offeringId = offeringId,
                partySize = partySize,
                customer = customer,
                status = BookingStatus.CONFIRMED,
                paymentStatus = PaymentStatus.UNPAID,
                pricing = pricing,
                idempotencyKey = idempotencyKey,
                idempotencyFingerprint = idempotencyFingerprint,
                createdByUserId = createdByUserId,
                createdAt = now,
                cancelledAt = null,
                cancellationReason = null,
            )
    }
}
