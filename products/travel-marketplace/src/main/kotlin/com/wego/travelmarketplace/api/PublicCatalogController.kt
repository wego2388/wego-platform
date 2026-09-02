package com.wego.travelmarketplace.api

import com.wego.travelmarketplace.application.ProviderQueryService
import com.wego.travelmarketplace.application.PublicCatalogQueryService
import com.wego.travelmarketplace.domain.Category
import com.wego.travelmarketplace.domain.CategoryId
import com.wego.travelmarketplace.domain.FulfilmentModel
import com.wego.travelmarketplace.domain.Service
import com.wego.travelmarketplace.domain.ServiceId
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Unauthenticated by construction — see
 * `com.wego.travelmarketplace.infrastructure.TravelMarketplaceBeanConfiguration`'s
 * `PublicApiPrefix` contribution for this controller's own route tree,
 * registered before this product's own `AuthenticatedApiPrefix` in kernel
 * `SecurityConfiguration`. Every response here is the narrow
 * `SERVICE_OWNERSHIP.md` "Simple public model" shape — see `ServiceDtos.kt`'s
 * `PublicServiceResponse` doc comment for exactly what is deliberately
 * excluded.
 */
@Validated
@RestController
@RequestMapping("/api/v1/travel-marketplace/public")
class PublicCatalogController(
    private val publicCatalogQueryService: PublicCatalogQueryService,
    private val providerQueryService: ProviderQueryService,
) {
    @GetMapping("/categories")
    fun listCategories(): List<PublicCategoryResponse> = publicCatalogQueryService.listCategories().map { it.toPublicResponse() }

    @GetMapping("/services")
    fun listServices(
        @RequestParam(required = false) categoryId: UUID?,
        @RequestParam(required = false, defaultValue = "0") @Min(0) page: Int,
        @RequestParam(required = false, defaultValue = "50") @Min(1) @Max(200) size: Int,
    ): List<PublicServiceResponse> =
        publicCatalogQueryService
            .listPublishedServices(categoryId?.let(::CategoryId), page, size)
            .map { it.toPublicResponse() }

    @GetMapping("/services/{id}")
    fun findServiceById(
        @PathVariable id: UUID,
    ): ResponseEntity<PublicServiceResponse> {
        val service = publicCatalogQueryService.findPublishedById(ServiceId(id)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(service.toPublicResponse())
    }

    private fun Service.toPublicResponse(): PublicServiceResponse {
        val operatedBy =
            if (fulfilmentModel == FulfilmentModel.PARTNER) {
                providerId?.let { providerQueryService.findById(it) }?.name
            } else {
                null
            }
        return PublicServiceResponse(
            id = id.value,
            categoryId = categoryId.value,
            name = name.toDto(),
            description = description.toDto(),
            confirmationType = confirmationType,
            cancellationPolicy = cancellationPolicy.toDto(),
            pickupInfo = pickupInfo?.toDto(),
            inclusions = inclusions?.toDto(),
            exclusions = exclusions?.toDto(),
            operatedBy = operatedBy,
            options =
                options.map {
                    PublicServiceOptionResponse(
                        label = it.label.toDto(),
                        durationMinutes = it.durationMinutes,
                        maxParticipants = it.maxParticipants,
                        priceAmount = it.price.amount,
                        priceCurrency = it.price.currencyCode,
                        priceBasis = it.priceBasis,
                    )
                },
            media = media.map { PublicServiceMediaResponse(assetReference = it.assetReference, locale = it.locale) },
        )
    }
}

private fun Category.toPublicResponse() =
    PublicCategoryResponse(id = id.value, code = code, name = name.toDto(), description = description?.toDto())
