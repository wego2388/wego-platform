package com.wego.divers.application

import com.wego.divers.domain.Booking
import com.wego.divers.domain.BookingId
import com.wego.divers.domain.BookingStatus
import com.wego.divers.domain.OfferingId

class BookingQueryService(
    private val bookingRepository: BookingRepository,
) {
    fun findById(id: BookingId): Booking? = bookingRepository.findById(id)

    fun list(
        offeringId: OfferingId?,
        status: BookingStatus?,
        page: Int = 0,
        size: Int = Pagination.DEFAULT_PAGE_SIZE,
    ): List<Booking> =
        bookingRepository.findAll(
            offeringId,
            status,
            limit = Pagination.boundedSize(size),
            offset = Pagination.offsetFor(page, size),
        )
}
