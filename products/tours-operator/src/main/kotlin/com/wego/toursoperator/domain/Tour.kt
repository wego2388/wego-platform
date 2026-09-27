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
 */
class Tour(
    val id: TourId,
    val slug: String,
    val category: TourCategory,
    val durationText: String,
    /** Adult price in EUR cents — always required. */
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
    }

    fun activate() {
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
            )
    }
}
