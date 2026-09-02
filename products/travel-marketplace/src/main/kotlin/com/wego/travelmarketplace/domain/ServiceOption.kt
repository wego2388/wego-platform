package com.wego.travelmarketplace.domain

import java.util.UUID

/**
 * A bookable duration/price variant of one [Service] — e.g. "2-hour" vs
 * "half-day" desert safari. A service with no [ServiceOption] at all has
 * nothing a customer could actually book; see [Service.publish]'s
 * `hasPublishableOption` gate.
 */
data class ServiceOption(
    val id: UUID,
    val label: LocalizedText,
    val durationMinutes: Int?,
    val maxParticipants: Int,
    val price: Money,
    val priceBasis: PriceBasis,
) {
    init {
        require(durationMinutes == null || durationMinutes > 0) { "Duration must be positive when present" }
        require(maxParticipants > 0) { "Maximum participants must be positive" }
    }
}
