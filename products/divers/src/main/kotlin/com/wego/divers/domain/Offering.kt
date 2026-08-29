package com.wego.divers.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class Offering(
    val id: OfferingId,
    val offeringType: OfferingType,
    val title: String,
    val description: String?,
    val startsOn: LocalDate,
    val endsOn: LocalDate?,
    val capacity: Int?,
    val pricingBasis: PricingBasis,
    val unitPrice: Money,
    status: OfferingStatus,
    val createdByUserId: UUID?,
    val createdAt: Instant,
    closedAt: Instant?,
) {
    var status: OfferingStatus = status
        private set

    var closedAt: Instant? = closedAt
        private set

    init {
        require(title.isNotBlank()) { "Offering title must not be blank" }
        require(endsOn == null || !endsOn.isBefore(startsOn)) { "Offering end date must not be before its start date" }
        require(capacity == null || capacity > 0) { "Offering capacity must be positive when present" }
        require((status == OfferingStatus.CLOSED) == (closedAt != null)) {
            "closedAt must be set if and only if the offering is closed"
        }
    }

    val isActive: Boolean get() = status == OfferingStatus.ACTIVE

    /** Terminal: an already-closed offering cannot be closed again. */
    fun close(now: Instant) {
        require(status == OfferingStatus.ACTIVE) { "Only an active offering can be closed" }
        status = OfferingStatus.CLOSED
        closedAt = now
    }

    companion object {
        fun create(
            id: OfferingId,
            offeringType: OfferingType,
            title: String,
            description: String?,
            startsOn: LocalDate,
            endsOn: LocalDate?,
            capacity: Int?,
            pricingBasis: PricingBasis,
            unitPrice: Money,
            createdByUserId: UUID?,
            now: Instant,
        ): Offering =
            Offering(
                id = id,
                offeringType = offeringType,
                title = title,
                description = description,
                startsOn = startsOn,
                endsOn = endsOn,
                capacity = capacity,
                pricingBasis = pricingBasis,
                unitPrice = unitPrice,
                status = OfferingStatus.ACTIVE,
                createdByUserId = createdByUserId,
                createdAt = now,
                closedAt = null,
            )
    }
}
