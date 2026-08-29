package com.wego.divers.application

import com.wego.divers.domain.BoatCharter
import com.wego.divers.domain.BoatCharterId
import com.wego.divers.domain.CharterStatus
import com.wego.divers.domain.CharterType
import com.wego.divers.domain.OfferingBoatCharterLink
import com.wego.divers.domain.OfferingId

class BoatCharterQueryService(
    private val boatCharterRepository: BoatCharterRepository,
    private val linkRepository: OfferingBoatCharterLinkRepository,
) {
    fun findById(id: BoatCharterId): BoatCharter? = boatCharterRepository.findById(id)

    fun list(
        charterType: CharterType?,
        status: CharterStatus?,
        search: String?,
        page: Int = 0,
        size: Int = Pagination.DEFAULT_PAGE_SIZE,
    ): List<BoatCharter> =
        boatCharterRepository.findAll(
            charterType,
            status,
            search,
            limit = Pagination.boundedSize(size),
            offset = Pagination.offsetFor(page, size),
        )

    fun findLinkForOffering(offeringId: OfferingId): OfferingBoatCharterLink? = linkRepository.findByOfferingId(offeringId)
}
