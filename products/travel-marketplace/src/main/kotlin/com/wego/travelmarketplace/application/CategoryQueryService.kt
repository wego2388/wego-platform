package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.Category
import com.wego.travelmarketplace.domain.CategoryId
import com.wego.travelmarketplace.domain.CategoryStatus

class CategoryQueryService(
    private val categoryRepository: CategoryRepository,
) {
    fun findById(id: CategoryId): Category? = categoryRepository.findById(id)

    fun list(status: CategoryStatus?): List<Category> = categoryRepository.findAll(status)
}
