package com.wego.toursoperator.application

import com.wego.toursoperator.domain.Booking
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.BookingStatus
import com.wego.toursoperator.domain.TourId
import java.time.LocalDate

class BookingQueryService(
    private val bookingRepository: BookingRepository,
) {
    fun findById(id: BookingId): Booking? = bookingRepository.findById(id)

    fun findByReference(reference: String): Booking? = bookingRepository.findByReference(reference)

    fun findByReferenceAndPhone(
        reference: String,
        phone: String,
    ): Booking? = bookingRepository.findByReferenceAndPhone(reference, phone)

    fun list(
        tourId: TourId?,
        status: BookingStatus?,
        date: LocalDate?,
        page: Int,
        size: Int,
    ): List<Booking> =
        bookingRepository.findAll(
            tourId = tourId,
            status = status,
            date = date,
            limit = size,
            offset = page * size,
        )
}
