package com.wego.toursoperator.application

import com.wego.toursoperator.domain.Tour
import com.wego.toursoperator.domain.TourCategory
import com.wego.toursoperator.domain.TourId

class TourQueryService(
    private val tourRepository: TourRepository,
) {
    fun findById(id: TourId): Tour? = tourRepository.findById(id)

    fun findBySlug(slug: String): Tour? = tourRepository.findBySlug(slug)

    fun list(
        category: TourCategory?,
        activeOnly: Boolean,
        page: Int,
        size: Int,
    ): List<Tour> =
        tourRepository.findAll(
            category = category,
            activeOnly = activeOnly,
            limit = size,
            offset = page * size,
        )
}
