package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.Category
import com.wego.travelmarketplace.domain.CategoryId
import com.wego.travelmarketplace.domain.CategoryStatus
import com.wego.travelmarketplace.domain.Service
import com.wego.travelmarketplace.domain.ServiceId

/**
 * The unauthenticated, customer-facing read path — separate from
 * [ServiceQueryService]/[CategoryQueryService] (staff, every status) so a
 * public route can never accidentally leak a `DRAFT`/`REVIEW`/`SUSPENDED`/
 * `ARCHIVED` service or an archived category, and never accidentally exposes
 * an internal field: see `ServiceOwnershipView` in the API layer for exactly
 * what public DTOs are allowed to carry (no provider contact/commission
 * details — only the "Operated by" name per `SERVICE_OWNERSHIP.md`).
 */
class PublicCatalogQueryService(
    private val serviceRepository: ServiceRepository,
    private val categoryRepository: CategoryRepository,
) {
    fun listCategories(): List<Category> = categoryRepository.findAll(CategoryStatus.ACTIVE)

    fun listPublishedServices(
        categoryId: CategoryId?,
        page: Int,
        size: Int,
    ): List<Service> = serviceRepository.findAllPublished(categoryId, size, page * size)

    fun findPublishedById(id: ServiceId): Service? = serviceRepository.findPublishedById(id)
}
