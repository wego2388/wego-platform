package com.wego.toursoperator.domain

/**
 * Tour category — maps to the 5 product lines Safari Tours Sharm operates.
 * TRANSFERS covers room booking and car rental, kept in scope as a product
 * line even though its booking flow differs (price-on-request rather than
 * fixed per-person).
 */
enum class TourCategory {
    DESERT,
    SEA,
    CULTURAL,
    SHOWS,
    TRANSFERS,
}
