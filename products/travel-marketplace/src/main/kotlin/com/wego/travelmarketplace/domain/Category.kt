package com.wego.travelmarketplace.domain

import java.time.Instant

/**
 * Staff-defined navigation grouping for services. No category is seeded by
 * this packet — `SERVICE_OWNERSHIP.md`'s "Proposed launch categories" are
 * explicitly "navigation hypotheses only," not approved inventory, so
 * creating them here as real rows would be exactly the kind of invented
 * content this repository's non-negotiable rule forbids. Staff create real
 * categories through the API once the owner approves a launch taxonomy.
 */
class Category(
    val id: CategoryId,
    val code: String,
    val name: LocalizedText,
    val description: LocalizedText?,
    val displayOrder: Int,
    status: CategoryStatus,
    val createdAt: Instant,
    archivedAt: Instant?,
) {
    var status: CategoryStatus = status
        private set

    var archivedAt: Instant? = archivedAt
        private set

    init {
        require(CODE_FORMAT.matches(code)) { "Category code must be lowercase-kebab-case" }
        require(displayOrder >= 0) { "Display order must not be negative" }
        require((status == CategoryStatus.ARCHIVED) == (archivedAt != null)) {
            "archivedAt must be set if and only if the category is archived"
        }
    }

    val isActive: Boolean get() = status == CategoryStatus.ACTIVE

    /** Terminal: an already-archived category cannot be archived again. */
    fun archive(now: Instant) {
        require(status == CategoryStatus.ACTIVE) { "Only an active category can be archived" }
        status = CategoryStatus.ARCHIVED
        archivedAt = now
    }

    fun withUpdatedDetails(
        name: LocalizedText,
        description: LocalizedText?,
        displayOrder: Int,
    ): Category =
        Category(
            id = id,
            code = code,
            name = name,
            description = description,
            displayOrder = displayOrder,
            status = status,
            createdAt = createdAt,
            archivedAt = archivedAt,
        )

    companion object {
        private val CODE_FORMAT = Regex("^[a-z][a-z0-9-]*$")

        fun create(
            id: CategoryId,
            code: String,
            name: LocalizedText,
            description: LocalizedText?,
            displayOrder: Int,
            now: Instant,
        ): Category =
            Category(
                id = id,
                code = code,
                name = name,
                description = description,
                displayOrder = displayOrder,
                status = CategoryStatus.ACTIVE,
                createdAt = now,
                archivedAt = null,
            )
    }
}
