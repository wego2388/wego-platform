package com.wego.toursoperator.domain

import java.time.Instant
import java.util.UUID

/**
 * Emergency switch for online sales. [bookingsPaused] stops new public
 * bookings; [paymentsPaused] stops opening or resuming payment checkouts.
 * Webhooks, expiry and staff operations are never affected, so money that is
 * already moving is still recorded.
 */
data class SalesControl(
    val bookingsPaused: Boolean,
    val paymentsPaused: Boolean,
    val reason: String?,
    val updatedByUserId: UUID?,
    val updatedAt: Instant?,
) {
    init {
        require(reason == null || reason.length <= MAX_REASON_LENGTH) { "reason is too long" }
    }

    companion object {
        const val MAX_REASON_LENGTH = 300

        /** No row stored yet: everything open. */
        val OPEN = SalesControl(false, false, null, null, null)
    }
}
