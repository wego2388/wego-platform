package com.wego.travelmarketplace.domain

/**
 * `delivery/01_REQUEST_AND_BOOKING.md`: "guarded transition table, actor
 * requirements and **typed reasons**" — a free-text reason alone is not
 * auditable/reportable, so cancellation always carries one of these codes;
 * [TravelRequest.cancel]'s free-text detail is an optional supplement, never
 * a replacement.
 */
enum class TravelRequestCancelReason {
    CUSTOMER_REQUESTED,
    STAFF_REJECTED,
    SERVICE_UNAVAILABLE,
    DUPLICATE_REQUEST,
    OTHER,
}
