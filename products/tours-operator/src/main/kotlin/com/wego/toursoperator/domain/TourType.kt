package com.wego.toursoperator.domain

enum class TourType {
    /** Standard bookable tour with a fixed per-person price. */
    TOUR,

    /** Airport or point-to-point transfer. */
    TRANSFER,

    /**
     * Price-on-request only — not available in the paid booking flow.
     * Must remain inactive in the public catalog.
     */
    REQUEST_ONLY,
}
