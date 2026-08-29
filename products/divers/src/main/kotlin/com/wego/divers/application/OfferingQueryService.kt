package com.wego.divers.application

import com.wego.divers.domain.Offering
import com.wego.divers.domain.OfferingId
import com.wego.divers.domain.OfferingStatus
import com.wego.divers.domain.OfferingType

class OfferingQueryService(
    private val offeringRepository: OfferingRepository,
) {
    fun findById(id: OfferingId): Offering? = offeringRepository.findById(id)

    fun list(
        offeringType: OfferingType?,
        status: OfferingStatus?,
        page: Int = 0,
        size: Int = Pagination.DEFAULT_PAGE_SIZE,
    ): List<Offering> =
        offeringRepository.findAll(
            offeringType,
            status,
            limit = Pagination.boundedSize(size),
            offset = Pagination.offsetFor(page, size),
        )
}
