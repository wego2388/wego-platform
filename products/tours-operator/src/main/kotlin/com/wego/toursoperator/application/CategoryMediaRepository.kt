package com.wego.toursoperator.application

import com.wego.toursoperator.domain.CategoryMedia

/** Repository for category cover images. */
interface CategoryMediaRepository {
    /** All categories known to the system (seeded by V29 migration). */
    fun findAll(): List<CategoryMedia>

    fun findByCategory(category: String): CategoryMedia?

    fun findByCategoryForUpdate(category: String): CategoryMedia?

    /** Insert-or-update a category media record. */
    fun save(media: CategoryMedia)

    /** Return the approved cover path for a category, or null if no approved cover exists. */
    fun findApprovedCoverPath(category: String): String?
}
