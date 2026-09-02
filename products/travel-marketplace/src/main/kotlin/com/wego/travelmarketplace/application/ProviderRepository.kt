package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.Provider
import com.wego.travelmarketplace.domain.ProviderId
import com.wego.travelmarketplace.domain.ProviderStatus

interface ProviderRepository {
    fun findById(id: ProviderId): Provider?

    /** Row-locked read for a read-modify-write cycle — see JooqOfferingRepository.findByIdForUpdate (products/divers) for the established pattern. */
    fun findByIdForUpdate(id: ProviderId): Provider?

    fun findAll(
        status: ProviderStatus?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<Provider>

    fun save(provider: Provider)
}
