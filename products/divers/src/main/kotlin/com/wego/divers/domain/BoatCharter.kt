package com.wego.divers.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class BoatCharter(
    val id: BoatCharterId,
    val boatName: String,
    val charterType: CharterType,
    val licensedCapacity: Int,
    val startsOn: LocalDate,
    val endsOn: LocalDate?,
    val notes: String?,
    status: CharterStatus,
    val createdByUserId: UUID?,
    val createdAt: Instant,
    endedAt: Instant?,
) {
    var status: CharterStatus = status
        private set

    var endedAt: Instant? = endedAt
        private set

    init {
        require(boatName.isNotBlank()) { "Boat name must not be blank" }
        require(licensedCapacity > 0) { "Licensed capacity must be positive" }
        require(endsOn == null || !endsOn.isBefore(startsOn)) { "Charter end date must not be before its start date" }
        require((status == CharterStatus.ENDED) == (endedAt != null)) {
            "endedAt must be set if and only if the charter has ended"
        }
    }

    val isActive: Boolean get() = status == CharterStatus.ACTIVE

    /** Terminal: an already-ended charter cannot be ended again. */
    fun end(now: Instant) {
        require(status == CharterStatus.ACTIVE) { "Only an active charter can be ended" }
        status = CharterStatus.ENDED
        endedAt = now
    }

    /** Identity and lifecycle state stay put — only the descriptive/operational fields are editable. */
    fun withUpdatedDetails(
        boatName: String,
        licensedCapacity: Int,
        startsOn: LocalDate,
        endsOn: LocalDate?,
        notes: String?,
    ): BoatCharter =
        BoatCharter(
            id = id,
            boatName = boatName,
            charterType = charterType,
            licensedCapacity = licensedCapacity,
            startsOn = startsOn,
            endsOn = endsOn,
            notes = notes,
            status = status,
            createdByUserId = createdByUserId,
            createdAt = createdAt,
            endedAt = endedAt,
        )

    companion object {
        fun create(
            id: BoatCharterId,
            boatName: String,
            charterType: CharterType,
            licensedCapacity: Int,
            startsOn: LocalDate,
            endsOn: LocalDate?,
            notes: String?,
            createdByUserId: UUID?,
            now: Instant,
        ): BoatCharter =
            BoatCharter(
                id = id,
                boatName = boatName,
                charterType = charterType,
                licensedCapacity = licensedCapacity,
                startsOn = startsOn,
                endsOn = endsOn,
                notes = notes,
                status = CharterStatus.ACTIVE,
                createdByUserId = createdByUserId,
                createdAt = now,
                endedAt = null,
            )
    }
}
