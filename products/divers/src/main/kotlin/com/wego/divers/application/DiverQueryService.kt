package com.wego.divers.application

import com.wego.divers.domain.Diver
import com.wego.divers.domain.DiverId
import com.wego.divers.domain.DiverStatus

class DiverQueryService(
    private val diverRepository: DiverRepository,
) {
    fun findById(id: DiverId): Diver? = diverRepository.findById(id)

    fun list(
        status: DiverStatus?,
        search: String?,
        page: Int = 0,
        size: Int = Pagination.DEFAULT_PAGE_SIZE,
    ): List<Diver> =
        diverRepository.findAll(
            status,
            search,
            limit = Pagination.boundedSize(size),
            offset = Pagination.offsetFor(page, size),
        )
}
