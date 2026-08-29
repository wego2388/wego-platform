package com.wego.divers.api

import com.wego.divers.domain.CharterStatus
import com.wego.divers.domain.CharterType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val MAX_BOAT_NAME_LENGTH = 200
private const val MAX_NOTES_LENGTH = 1000

data class CreateBoatCharterRequest(
    @field:NotBlank
    @field:Size(max = MAX_BOAT_NAME_LENGTH)
    val boatName: String,
    val charterType: CharterType,
    @field:Positive
    val licensedCapacity: Int,
    val startsOn: LocalDate,
    val endsOn: LocalDate?,
    @field:Size(max = MAX_NOTES_LENGTH)
    val notes: String?,
)

data class UpdateBoatCharterRequest(
    @field:NotBlank
    @field:Size(max = MAX_BOAT_NAME_LENGTH)
    val boatName: String,
    @field:Positive
    val licensedCapacity: Int,
    val startsOn: LocalDate,
    val endsOn: LocalDate?,
    @field:Size(max = MAX_NOTES_LENGTH)
    val notes: String?,
)

data class BoatCharterResponse(
    val id: UUID,
    val boatName: String,
    val charterType: CharterType,
    val licensedCapacity: Int,
    val startsOn: LocalDate,
    val endsOn: LocalDate?,
    val notes: String?,
    val status: CharterStatus,
    val createdAt: Instant,
    val endedAt: Instant?,
)

data class LinkBoatCharterRequest(
    val boatCharterId: UUID,
)

data class OfferingBoatCharterLinkResponse(
    val offeringId: UUID,
    val boatCharterId: UUID,
    val linkedAt: Instant,
)

data class BoatCharterErrorResponse(
    val error: String,
)
