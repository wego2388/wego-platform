package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.CategoryId
import com.wego.travelmarketplace.domain.Service
import com.wego.travelmarketplace.domain.ServiceId
import com.wego.travelmarketplace.domain.ServiceStatus

interface ServiceRepository {
    fun findById(id: ServiceId): Service?

    fun findByIdForUpdate(id: ServiceId): Service?

    /** Staff-facing: every status, optionally filtered. */
    fun findAll(
        status: ServiceStatus?,
        categoryId: CategoryId?,
        limit: Int,
        offset: Int,
    ): List<Service>

    /** Public-facing: only `PUBLISHED` services, never leaking a draft/suspended/archived one. */
    fun findAllPublished(
        categoryId: CategoryId?,
        limit: Int,
        offset: Int,
    ): List<Service>

    fun findPublishedById(id: ServiceId): Service?

    fun save(service: Service)
}
