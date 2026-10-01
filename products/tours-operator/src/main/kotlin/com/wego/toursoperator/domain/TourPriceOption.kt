package com.wego.toursoperator.domain

/** How a tour is priced. */
enum class PriceBasis {
    /** Adults × adult price + children × child price. */
    PER_PERSON,

    /** Customers buy units (a buggy, a boat, a car); the price is per unit. */
    PER_UNIT,
}

/**
 * One thing a per-unit tour sells: e.g. a two-seat buggy at €30 or a
 * minibus for up to 8 guests at €35. [labelEn] is the catalogue label; the
 * sites translate known codes.
 */
data class TourPriceOption(
    val code: String,
    val labelEn: String,
    val seatsPerUnit: Int,
    val priceCents: Long,
    val sortOrder: Int,
) {
    init {
        require(code.matches(CODE_FORMAT)) { "Price option code must be lowercase letters, digits or hyphens" }
        require(labelEn.isNotBlank() && labelEn.length <= 80) { "Price option label must be 1–80 characters" }
        require(seatsPerUnit in 1..99) { "Seats per unit must be 1–99" }
        require(priceCents > 0) { "A unit must have a positive price" }
        require(sortOrder >= 0) { "Sort order must not be negative" }
    }

    companion object {
        val CODE_FORMAT = Regex("^[a-z0-9][a-z0-9-]{0,39}$")
    }
}
