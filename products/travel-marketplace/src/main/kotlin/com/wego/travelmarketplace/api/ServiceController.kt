package com.wego.travelmarketplace.api

import com.wego.identity.AuthenticatedUser
import com.wego.travelmarketplace.application.ApproveServiceResult
import com.wego.travelmarketplace.application.ApproveServiceService
import com.wego.travelmarketplace.application.ArchiveServiceResult
import com.wego.travelmarketplace.application.ArchiveServiceService
import com.wego.travelmarketplace.application.CreateServiceCommand
import com.wego.travelmarketplace.application.CreateServiceResult
import com.wego.travelmarketplace.application.CreateServiceService
import com.wego.travelmarketplace.application.PublishServiceResult
import com.wego.travelmarketplace.application.PublishServiceService
import com.wego.travelmarketplace.application.ServiceQueryService
import com.wego.travelmarketplace.application.SubmitServiceForReviewResult
import com.wego.travelmarketplace.application.SubmitServiceForReviewService
import com.wego.travelmarketplace.application.SuspendServiceResult
import com.wego.travelmarketplace.application.SuspendServiceService
import com.wego.travelmarketplace.application.UpdateServiceCommand
import com.wego.travelmarketplace.application.UpdateServiceResult
import com.wego.travelmarketplace.application.UpdateServiceService
import com.wego.travelmarketplace.domain.CategoryId
import com.wego.travelmarketplace.domain.Money
import com.wego.travelmarketplace.domain.ProviderId
import com.wego.travelmarketplace.domain.Service
import com.wego.travelmarketplace.domain.ServiceId
import com.wego.travelmarketplace.domain.ServiceMedia
import com.wego.travelmarketplace.domain.ServiceOption
import com.wego.travelmarketplace.domain.ServiceStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Validated
@RestController
@RequestMapping("/api/v1/travel-marketplace/services")
class ServiceController(
    private val createServiceService: CreateServiceService,
    private val updateServiceService: UpdateServiceService,
    private val submitServiceForReviewService: SubmitServiceForReviewService,
    private val approveServiceService: ApproveServiceService,
    private val publishServiceService: PublishServiceService,
    private val suspendServiceService: SuspendServiceService,
    private val archiveServiceService: ArchiveServiceService,
    private val serviceQueryService: ServiceQueryService,
) {
    @PostMapping
    @PreAuthorize("hasAuthority('service:manage')")
    fun create(
        @Valid @RequestBody request: UpsertServiceRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (val result = createServiceService.create(request.toCommand(actorUserId))) {
            is CreateServiceResult.Created -> ResponseEntity.status(HttpStatus.CREATED).body(result.service.toResponse())
            CreateServiceResult.CategoryNotFound ->
                ResponseEntity
                    .status(
                        HttpStatus.BAD_REQUEST,
                    ).body(ServiceErrorResponse("category_not_found"))
            CreateServiceResult.ProviderNotFound ->
                ResponseEntity
                    .status(
                        HttpStatus.BAD_REQUEST,
                    ).body(ServiceErrorResponse("provider_not_found"))
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('service:view')")
    fun list(
        @RequestParam(required = false) status: ServiceStatus?,
        @RequestParam(required = false) categoryId: UUID?,
        @RequestParam(required = false, defaultValue = "0") @Min(0) page: Int,
        @RequestParam(required = false, defaultValue = "50") @Min(1) @Max(200) size: Int,
    ): List<ServiceResponse> = serviceQueryService.list(status, categoryId?.let(::CategoryId), page, size).map { it.toResponse() }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('service:view')")
    fun findById(
        @PathVariable id: UUID,
    ): ResponseEntity<ServiceResponse> {
        val service = serviceQueryService.findById(ServiceId(id)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(service.toResponse())
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('service:manage')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpsertServiceRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (val result = updateServiceService.update(request.toUpdateCommand(ServiceId(id), actorUserId))) {
            is UpdateServiceResult.Updated -> ResponseEntity.ok(result.service.toResponse())
            UpdateServiceResult.NotFound -> ResponseEntity.notFound().build()
            UpdateServiceResult.Archived -> ResponseEntity.status(HttpStatus.CONFLICT).body(ServiceErrorResponse("archived"))
            UpdateServiceResult.CategoryNotFound ->
                ResponseEntity
                    .status(
                        HttpStatus.BAD_REQUEST,
                    ).body(ServiceErrorResponse("category_not_found"))
            UpdateServiceResult.ProviderNotFound ->
                ResponseEntity
                    .status(
                        HttpStatus.BAD_REQUEST,
                    ).body(ServiceErrorResponse("provider_not_found"))
        }
    }

    @PostMapping("/{id}/submit-for-review")
    @PreAuthorize("hasAuthority('service:manage')")
    fun submitForReview(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (val result = submitServiceForReviewService.submit(ServiceId(id), actorUserId)) {
            is SubmitServiceForReviewResult.Submitted -> ResponseEntity.ok(result.service.toResponse())
            SubmitServiceForReviewResult.NotFound -> ResponseEntity.notFound().build()
            SubmitServiceForReviewResult.InvalidTransition ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(ServiceErrorResponse("invalid_transition"))
        }
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('service:manage')")
    fun approve(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (val result = approveServiceService.approve(ServiceId(id), actorUserId)) {
            is ApproveServiceResult.Approved -> ResponseEntity.ok(result.service.toResponse())
            ApproveServiceResult.NotFound -> ResponseEntity.notFound().build()
            ApproveServiceResult.InvalidTransition ->
                ResponseEntity
                    .status(
                        HttpStatus.CONFLICT,
                    ).body(ServiceErrorResponse("invalid_transition"))
        }
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('service:manage')")
    fun publish(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (val result = publishServiceService.publish(ServiceId(id), actorUserId)) {
            is PublishServiceResult.Published -> ResponseEntity.ok(result.service.toResponse())
            PublishServiceResult.NotFound -> ResponseEntity.notFound().build()
            PublishServiceResult.InvalidTransition ->
                ResponseEntity
                    .status(
                        HttpStatus.CONFLICT,
                    ).body(ServiceErrorResponse("invalid_transition"))
            PublishServiceResult.MissingPublishableOption ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(ServiceErrorResponse("missing_publishable_option"))
            PublishServiceResult.MissingRightsClearedMedia ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(ServiceErrorResponse("missing_rights_cleared_media"))
        }
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasAuthority('service:manage')")
    fun suspend(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (val result = suspendServiceService.suspend(ServiceId(id), actorUserId)) {
            is SuspendServiceResult.Suspended -> ResponseEntity.ok(result.service.toResponse())
            SuspendServiceResult.NotFound -> ResponseEntity.notFound().build()
            SuspendServiceResult.InvalidTransition ->
                ResponseEntity
                    .status(
                        HttpStatus.CONFLICT,
                    ).body(ServiceErrorResponse("invalid_transition"))
        }
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('service:manage')")
    fun archive(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (val result = archiveServiceService.archive(ServiceId(id), actorUserId)) {
            is ArchiveServiceResult.Archived -> ResponseEntity.ok(result.service.toResponse())
            ArchiveServiceResult.NotFound -> ResponseEntity.notFound().build()
            ArchiveServiceResult.AlreadyArchived ->
                ResponseEntity
                    .status(
                        HttpStatus.CONFLICT,
                    ).body(ServiceErrorResponse("already_archived"))
        }
    }
}

private fun UpsertServiceRequest.toCommand(actorUserId: UUID?) =
    CreateServiceCommand(
        categoryId = CategoryId(categoryId),
        name = name.toDomain(),
        description = description.toDomain(),
        fulfilmentModel = fulfilmentModel,
        providerId = providerId?.let(::ProviderId),
        confirmationType = confirmationType,
        cancellationPolicy = cancellationPolicy.toDomain(),
        pickupInfo = pickupInfo?.toDomain(),
        inclusions = inclusions?.toDomain(),
        exclusions = exclusions?.toDomain(),
        options = options.map { it.toDomain() },
        media = media.map { it.toDomain() },
        actorUserId = actorUserId,
    )

private fun UpsertServiceRequest.toUpdateCommand(
    serviceId: ServiceId,
    actorUserId: UUID?,
) = UpdateServiceCommand(
    serviceId = serviceId,
    categoryId = CategoryId(categoryId),
    name = name.toDomain(),
    description = description.toDomain(),
    fulfilmentModel = fulfilmentModel,
    providerId = providerId?.let(::ProviderId),
    confirmationType = confirmationType,
    cancellationPolicy = cancellationPolicy.toDomain(),
    pickupInfo = pickupInfo?.toDomain(),
    inclusions = inclusions?.toDomain(),
    exclusions = exclusions?.toDomain(),
    options = options.map { it.toDomain() },
    media = media.map { it.toDomain() },
    actorUserId = actorUserId,
)

private fun ServiceOptionDto.toDomain(): ServiceOption =
    ServiceOption(
        id = id ?: UUID.randomUUID(),
        label = label.toDomain(),
        durationMinutes = durationMinutes,
        maxParticipants = maxParticipants,
        price = Money(priceAmount.setScale(2), priceCurrency),
        priceBasis = priceBasis,
    )

private fun ServiceMediaDto.toDomain(): ServiceMedia = ServiceMedia(id = id ?: UUID.randomUUID(), assetReference, rightsEvidence, locale)

private fun ServiceOption.toDto(): ServiceOptionDto =
    ServiceOptionDto(
        id = id,
        label = label.toDto(),
        durationMinutes = durationMinutes,
        maxParticipants = maxParticipants,
        priceAmount = price.amount,
        priceCurrency = price.currencyCode,
        priceBasis = priceBasis,
    )

private fun ServiceMedia.toDto(): ServiceMediaDto = ServiceMediaDto(id, assetReference, rightsEvidence, locale)

private fun Service.toResponse() =
    ServiceResponse(
        id = id.value,
        categoryId = categoryId.value,
        name = name.toDto(),
        description = description.toDto(),
        fulfilmentModel = fulfilmentModel,
        providerId = providerId?.value,
        confirmationType = confirmationType,
        cancellationPolicy = cancellationPolicy.toDto(),
        pickupInfo = pickupInfo?.toDto(),
        inclusions = inclusions?.toDto(),
        exclusions = exclusions?.toDto(),
        options = options.map { it.toDto() },
        media = media.map { it.toDto() },
        status = status,
        createdAt = createdAt,
        publishedAt = publishedAt,
        archivedAt = archivedAt,
    )
