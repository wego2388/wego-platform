package com.wego.divers.application

import com.wego.divers.domain.Offering
import com.wego.divers.domain.OfferingId
import com.wego.divers.domain.OfferingStatus
import com.wego.divers.domain.OfferingType
import java.time.Clock
import java.time.LocalDate

class OfferingQueryService(
    private val offeringRepository: OfferingRepository,
    private val clock: Clock,
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

    /** Active offerings starting in the next 7 days (inclusive of today) — the dashboard's "coming up" widget. */
    fun listUpcoming(limit: Int = UPCOMING_LIMIT): List<Offering> {
        val today = LocalDate.now(clock)
        return offeringRepository.findUpcoming(today, today.plusDays(UPCOMING_WINDOW_DAYS), limit)
    }

    companion object {
        private const val UPCOMING_WINDOW_DAYS = 7L
        private const val UPCOMING_LIMIT = 20
    }
}
