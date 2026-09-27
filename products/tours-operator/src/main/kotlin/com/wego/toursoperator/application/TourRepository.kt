package com.wego.toursoperator.application

import com.wego.toursoperator.domain.Tour
import com.wego.toursoperator.domain.TourCategory
import com.wego.toursoperator.domain.TourId

interface TourRepository {
    fun findById(id: TourId): Tour?

    /** Row-lock for write operations — prevents concurrent activation races. */
    fun findByIdForUpdate(id: TourId): Tour?

    fun findBySlug(slug: String): Tour?

    fun existsBySlug(slug: String): Boolean

    fun findAll(
        category: TourCategory?,
        activeOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<Tour>

    fun save(tour: Tour)
}
