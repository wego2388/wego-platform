package com.wego.travelmarketplace.api

import com.fasterxml.jackson.annotation.JsonFormat
import com.wego.travelmarketplace.domain.ConfirmationType
import com.wego.travelmarketplace.domain.FulfilmentModel
import com.wego.travelmarketplace.domain.PriceBasis
import com.wego.travelmarketplace.domain.ServiceStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class ServiceOptionDto(
    val id: UUID?,
    @field:Valid val label: LocalizedTextDto,
    val durationMinutes: Int?,
    @field:Positive val maxParticipants: Int,
    // Serialized as a JSON string (matches the OpenAPI contract's
    // `priceAmount: type: string`) — plain BigDecimal serialization was
    // caught live sending an unquoted JSON number instead, silently
    // dropping the ".00" a client would need for exact currency display.
    @field:JsonFormat(shape = JsonFormat.Shape.STRING)
    @field:DecimalMin("0.00")
    @field:Digits(integer = 8, fraction = 2)
    val priceAmount: BigDecimal,
    // Restricted to the client's one real organizational currency
    // (LOCALES_AND_CONTENT.md), not any well-formed 3-letter code — an
    // independent review caught live that "ZZZ" (a real ISO 4217 pattern
    // match, but not an assigned currency) was accepted with 201. Multi-
    // currency display is explicitly deferred in that same document; widen
    // this only when that deferral is actually lifted, with a real list of
    // supported codes, not a bare format check.
    @field:Pattern(regexp = "^EGP$") val priceCurrency: String,
    val priceBasis: PriceBasis,
)

data class ServiceMediaDto(
    val id: UUID?,
    @field:NotBlank val assetReference: String,
    @field:NotBlank val rightsEvidence: String,
    @field:NotBlank val locale: String,
)

data class UpsertServiceRequest(
    val categoryId: UUID,
    @field:Valid val name: LocalizedTextDto,
    @field:Valid val description: LocalizedTextDto,
    val fulfilmentModel: FulfilmentModel,
    val providerId: UUID?,
    val confirmationType: ConfirmationType,
    @field:Valid val cancellationPolicy: LocalizedTextDto,
    @field:Valid val pickupInfo: LocalizedTextDto?,
    @field:Valid val inclusions: LocalizedTextDto?,
    @field:Valid val exclusions: LocalizedTextDto?,
    @field:Valid val options: List<ServiceOptionDto>,
    @field:Valid val media: List<ServiceMediaDto>,
)

data class ServiceResponse(
    val id: UUID,
    val categoryId: UUID,
    val name: LocalizedTextDto,
    val description: LocalizedTextDto,
    val fulfilmentModel: FulfilmentModel,
    val providerId: UUID?,
    val confirmationType: ConfirmationType,
    val cancellationPolicy: LocalizedTextDto,
    val pickupInfo: LocalizedTextDto?,
    val inclusions: LocalizedTextDto?,
    val exclusions: LocalizedTextDto?,
    val options: List<ServiceOptionDto>,
    val media: List<ServiceMediaDto>,
    val status: ServiceStatus,
    val createdAt: Instant,
    val publishedAt: Instant?,
    val archivedAt: Instant?,
)

data class ServiceErrorResponse(
    val error: String,
)

data class PublicServiceOptionResponse(
    val label: LocalizedTextDto,
    val durationMinutes: Int?,
    val maxParticipants: Int,
    @field:JsonFormat(shape = JsonFormat.Shape.STRING)
    val priceAmount: BigDecimal,
    val priceCurrency: String,
    val priceBasis: PriceBasis,
)

data class PublicServiceMediaResponse(
    val assetReference: String,
    val locale: String,
)

/**
 * The public shape — deliberately narrower than [ServiceResponse]. Never
 * carries [FulfilmentModel]/`providerId` or media rights evidence; only
 * [operatedBy] (the provider's name, present only for a `PARTNER` service)
 * per `SERVICE_OWNERSHIP.md`'s "Simple public model."
 */
data class PublicServiceResponse(
    val id: UUID,
    val categoryId: UUID,
    val name: LocalizedTextDto,
    val description: LocalizedTextDto,
    val confirmationType: ConfirmationType,
    val cancellationPolicy: LocalizedTextDto,
    val pickupInfo: LocalizedTextDto?,
    val inclusions: LocalizedTextDto?,
    val exclusions: LocalizedTextDto?,
    val operatedBy: String?,
    val options: List<PublicServiceOptionResponse>,
    val media: List<PublicServiceMediaResponse>,
)

data class PublicCategoryResponse(
    val id: UUID,
    val code: String,
    val name: LocalizedTextDto,
    val description: LocalizedTextDto?,
)
