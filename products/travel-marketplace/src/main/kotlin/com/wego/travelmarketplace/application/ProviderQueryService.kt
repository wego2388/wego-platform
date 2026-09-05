package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.Provider
import com.wego.travelmarketplace.domain.ProviderId
import com.wego.travelmarketplace.domain.ProviderStatus

class ProviderQueryService(
    private val providerRepository: ProviderRepository,
) {
    fun findById(id: ProviderId): Provider? = providerRepository.findById(id)

    fun list(
        status: ProviderStatus?,
        search: String?,
        page: Int,
        size: Int,
    ): List<Provider> = providerRepository.findAll(status, search, size, page * size)
}
