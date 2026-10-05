package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.NotificationKind
import com.wego.travelmarketplace.domain.TravelRequest
import com.wego.travelmarketplace.domain.TravelRequestStatus

/**
 * Whether a notification is still true for the request's current state.
 * Shared by the dispatcher (do not send a stale message) and the staff
 * resend (do not queue one).
 */
object NotificationEligibility {
    fun stateSkipReason(
        kind: NotificationKind,
        request: TravelRequest?,
        confirmationQueued: Boolean,
    ): String? {
        if (request == null) return "request_missing"
        if (!kind.isStillTrueFor(request.status)) return "request_state_changed"
        // A separate confirmation email supersedes the receipt once the
        // request is confirmed; sending both would be redundant noise.
        if (kind == NotificationKind.CUSTOMER_REQUEST_RECEIVED &&
            request.status == TravelRequestStatus.CONFIRMED &&
            confirmationQueued
        ) {
            return "superseded_by_confirmation"
        }
        return null
    }
}
