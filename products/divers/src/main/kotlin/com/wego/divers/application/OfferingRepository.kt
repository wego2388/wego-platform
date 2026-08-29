package com.wego.divers.application

import com.wego.divers.domain.Offering
import com.wego.divers.domain.OfferingId
import com.wego.divers.domain.OfferingStatus
import com.wego.divers.domain.OfferingType

interface OfferingRepository {
    fun findById(id: OfferingId): Offering?

    /**
     * Same lookup as [findById], but takes a row lock (`SELECT ... FOR
     * UPDATE`) for the duration of the caller's transaction. Booking
     * creation must use this — it is the single serialization point that
     * makes the capacity check-then-insert sequence safe under concurrent
     * requests against the same offering, mirroring
     * `JooqUserRepository.findByEmailForUpdate`'s role in login lockout.
     */
    fun findByIdForUpdate(id: OfferingId): Offering?

    fun findAll(
        offeringType: OfferingType?,
        status: OfferingStatus?,
        limit: Int,
        offset: Int,
    ): List<Offering>

    fun save(offering: Offering)
}
