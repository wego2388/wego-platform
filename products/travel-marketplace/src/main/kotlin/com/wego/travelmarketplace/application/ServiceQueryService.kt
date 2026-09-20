package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.CategoryId
import com.wego.travelmarketplace.domain.Service
import com.wego.travelmarketplace.domain.ServiceId
import com.wego.travelmarketplace.domain.ServiceStatus

class ServiceQueryService(
    private val serviceRepository: ServiceRepository,
) {
    fun findById(id: ServiceId): Service? = serviceRepository.findById(id)

    fun list(
        status: ServiceStatus?,
        categoryId: CategoryId?,
        page: Int,
        size: Int,
    ): List<Service> = serviceRepository.findAll(status, categoryId, size, page * size)
}
