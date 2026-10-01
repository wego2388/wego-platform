package com.wego.toursoperator.domain

import java.time.Instant
import java.util.UUID

/**
 * A tour offered by Safari Tours Sharm. Content is multilingual (en/ru/ar/it).
 * Price fields are stored as EUR cents (Long) to avoid floating-point issues
 * at the domain level; Money wrappers are used at the booking-pricing layer.
 *
 * isActive controls public visibility. sortOrder controls display order within
 * a category. Neither field affects booking invariants — capacity and slot
 * availability do.
 *
 * tourType: TOUR = standard bookable; TRANSFER = point-to-point;
 * REQUEST_ONLY = price on request, must stay inactive.
 *
 * Multilingual fields (nameAr, nameRu, nameIt, descriptionEn, shortDescEn)
 * exist in the V16 schema but have no owner-approved content yet. They are
 * intentionally absent from the domain until translations are supplied and
 * reviewed; V16 columns are nullable and unused columns are left NULL.
 * pricingNote is present in approved-catalog.json for a subset of tours and
 * is therefore modelled here.
 */
class Tour(
    val id: TourId,
    val slug: String,
    val category: TourCategory,
    val durationText: String,
    /** Adult price in EUR cents — required for TOUR/TRANSFER; 0 for REQUEST_ONLY. */
    val priceAdultCents: Long,
    /** Child price in EUR cents — null when not applicable or same as adult. */
    val priceChildCents: Long?,
    val capacity: Int,
    val availableTimeSlots: Set<TimeSlot>,
    val sortOrder: Int,
    isActive: Boolean,
    val createdAt: Instant,
    /** Nullable: the creating user may be deleted (ON DELETE SET NULL in DB). */
    val createdByUserId: UUID?,
    val nameEn: String? = null,
    val tourType: TourType = TourType.TOUR,
    val imageUrl: String? = null,
    val cancellationPolicy: CancellationPolicy = CancellationPolicy.STANDARD,
    /**
     * Optional pricing clarification displayed alongside the price
     * (e.g. "Price per buggy", "Price on request"). Present for a subset
     * of tours in approved-catalog.json.
     */
    val pricingNote: String? = null,
    /**
     * PER_PERSON (default) or PER_UNIT. Per-unit tours sell [priceOptions]
     * (a buggy, a boat, a car); priceAdultCents is then the "from" price
     * shown in listings. Options are catalogue data maintained by migration.
     */
    val priceBasis: PriceBasis = PriceBasis.PER_PERSON,
    val priceOptions: List<TourPriceOption> = emptyList(),
) {
    var isActive: Boolean = isActive
        private set

    init {
        require(slug.matches(SLUG_FORMAT)) {
            "Tour slug must be lowercase alphanumeric with hyphens, 3–80 characters"
        }
        require(durationText.isNotBlank()) { "Duration text must not be blank" }
        require(priceAdultCents >= 0) { "Adult price must not be negative" }
        require(priceChildCents == null || priceChildCents >= 0) { "Child price must not be negative" }
        require(capacity >= 1) { "Capacity must be at least 1" }
        require(availableTimeSlots.isNotEmpty()) { "At least one time slot must be available" }
        require(sortOrder >= 0) { "Sort order must not be negative" }
        require((priceBasis == PriceBasis.PER_UNIT) == priceOptions.isNotEmpty()) {
            "Per-unit tours need price options and per-person tours must not have any"
        }
        require(priceOptions.map { it.code }.toSet().size == priceOptions.size) { "Price option codes must be unique" }
        // REQUEST_ONLY tours must stay inactive — they never enter the paid booking flow
        require(tourType != TourType.REQUEST_ONLY || !isActive) {
            "A REQUEST_ONLY tour must not be active"
        }
    }

    fun priceOption(code: String): TourPriceOption? = priceOptions.firstOrNull { it.code == code }

    fun activate() {
        require(tourType != TourType.REQUEST_ONLY) {
            "A REQUEST_ONLY tour cannot be activated — it must not enter the paid booking flow"
        }
        isActive = true
    }

    fun deactivate() {
        isActive = false
    }

    companion object {
        private val SLUG_FORMAT = Regex("^[a-z0-9][a-z0-9-]{1,78}[a-z0-9]$")

        fun create(
            id: TourId,
            slug: String,
            category: TourCategory,
            durationText: String,
            priceAdultCents: Long,
            priceChildCents: Long?,
            capacity: Int,
            availableTimeSlots: Set<TimeSlot>,
            sortOrder: Int,
            createdByUserId: UUID?,
            now: Instant,
            nameEn: String? = null,
            tourType: TourType = TourType.TOUR,
            imageUrl: String? = null,
            cancellationPolicy: CancellationPolicy = CancellationPolicy.STANDARD,
            pricingNote: String? = null,
        ): Tour =
            Tour(
                id = id,
                slug = slug,
                category = category,
                durationText = durationText,
                priceAdultCents = priceAdultCents,
                priceChildCents = priceChildCents,
                capacity = capacity,
                availableTimeSlots = availableTimeSlots,
                sortOrder = sortOrder,
                isActive = false,
                createdAt = now,
                createdByUserId = createdByUserId,
                nameEn = nameEn,
                tourType = tourType,
                imageUrl = imageUrl,
                cancellationPolicy = cancellationPolicy,
                pricingNote = pricingNote,
            )
    }
}
