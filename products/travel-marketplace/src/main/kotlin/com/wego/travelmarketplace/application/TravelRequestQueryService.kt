package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.TravelRequest
import com.wego.travelmarketplace.domain.TravelRequestId
import com.wego.travelmarketplace.domain.TravelRequestReference
import com.wego.travelmarketplace.domain.TravelRequestStatus

class TravelRequestQueryService(
    private val requestRepository: TravelRequestRepository,
) {
    fun findById(id: TravelRequestId): TravelRequest? = requestRepository.findById(id)

    /** Public lookup surface — the only way a customer sees their own request again. */
    fun findByReference(reference: TravelRequestReference): TravelRequest? = requestRepository.findByReference(reference)

    fun list(
        status: TravelRequestStatus?,
        page: Int,
        size: Int,
    ): List<TravelRequest> = requestRepository.findAll(status, size, page * size)
}
