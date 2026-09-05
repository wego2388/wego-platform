package com.wego.divers.application

import com.wego.divers.domain.Booking
import com.wego.divers.domain.BookingId
import com.wego.divers.domain.BookingStatus
import com.wego.divers.domain.Money
import com.wego.divers.domain.OfferingId
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

class BookingQueryService(
    private val bookingRepository: BookingRepository,
    private val clock: Clock,
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

    /** Bookings created since UTC midnight today — the dashboard's "bookings today" tally. */
    fun countCreatedToday(): Int {
        val startOfToday = LocalDate.now(clock).atStartOfDay(ZoneOffset.UTC).toInstant()
        return bookingRepository.countCreatedBetween(startOfToday, startOfToday.plusSeconds(SECONDS_PER_DAY))
    }

    /** Confirmed+paid booking totals created since UTC midnight on the 1st of the current month, grouped by currency. */
    fun paidRevenueThisMonth(): List<Money> {
        val today = LocalDate.now(clock)
        val startOfMonth = today.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val startOfNextMonth =
            today
                .withDayOfMonth(1)
                .plusMonths(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
        return bookingRepository.sumPaidTotalsCreatedBetween(startOfMonth, startOfNextMonth)
    }

    companion object {
        private const val SECONDS_PER_DAY = 86_400L
    }
}
