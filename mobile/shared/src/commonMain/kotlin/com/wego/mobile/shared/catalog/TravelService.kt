package com.wego.mobile.shared.catalog

/**
 * Mirrors `products/travel-marketplace`'s public projection exactly
 * (`platform/contracts/openapi/v1/sharm-to-go-api.yaml`'s
 * `PublicCategoryResponse`/`PublicServiceResponse` schemas) — a different
 * product's catalog shape from `Offering`/`DiveCatalog` in this same file's
 * package (that pair is the Sharm Divers Club diving product; this one is
 * the Travel Marketplace product any Sharm To Go surface, mobile included,
 * draws from). Field-for-field with the real backend response, not a mobile
 * app's own invented shape.
 */
data class TravelCategory(
    val id: String,
    val code: String,
    val name: LocalizedText,
    val description: LocalizedText?,
)

enum class TravelConfirmationType {
    INSTANT,
    STAFF_REVIEW,
}

enum class TravelPriceBasis {
    PER_PERSON,
    PER_GROUP,
    PER_VEHICLE,
    FLAT,
}

data class TravelServiceOption(
    val label: LocalizedText,
    val durationMinutes: Int?,
    val maxParticipants: Int,
    val priceAmount: String,
    val priceCurrency: String,
    val priceBasis: TravelPriceBasis,
)

data class TravelServiceMedia(
    val assetReference: String,
    val locale: String,
)

data class TravelService(
    val id: String,
    val categoryId: String,
    val name: LocalizedText,
    val description: LocalizedText,
    val confirmationType: TravelConfirmationType,
    val cancellationPolicy: LocalizedText,
    val pickupInfo: LocalizedText?,
    val inclusions: LocalizedText?,
    val exclusions: LocalizedText?,
    /** The provider's name — present only for a PARTNER service. Never contact/commission details (same public-model rule as the backend). */
    val operatedBy: String?,
    val options: List<TravelServiceOption>,
    val media: List<TravelServiceMedia>,
)
