package com.wego.toursoperator.application

import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.CollectionMethod
import com.wego.toursoperator.domain.OfficeCollection
import java.math.BigDecimal
import java.util.UUID

/** Append-only: there is intentionally no update or delete. */
interface OfficeCollectionRepository {
    fun findByBooking(bookingId: BookingId): List<OfficeCollection>

    fun findById(id: UUID): OfficeCollection?

    fun findByRequest(
        actorUserId: UUID,
        clientRequestId: UUID,
    ): OfficeCollection?

    /** True when this receipt reference was already recorded for the method (reversed ones included). */
    fun referenceExists(
        method: CollectionMethod,
        reference: String,
    ): Boolean

    /** Net EUR settled (collections minus reversals) per booking; bookings with no entries are absent. */
    fun netCollected(bookingIds: Collection<BookingId>): Map<BookingId, BigDecimal>

    fun append(collection: OfficeCollection)
}
