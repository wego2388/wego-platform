package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.Category
import com.wego.travelmarketplace.domain.CategoryId
import com.wego.travelmarketplace.domain.CategoryStatus

interface CategoryRepository {
    fun findById(id: CategoryId): Category?

    fun findByIdForUpdate(id: CategoryId): Category?

    fun findByCode(code: String): Category?

    fun findAll(status: CategoryStatus?): List<Category>

    fun save(category: Category)
}
