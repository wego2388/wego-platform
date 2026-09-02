package com.wego.travelmarketplace.api

import com.wego.identity.AuthenticatedUser
import com.wego.travelmarketplace.application.ArchiveProviderResult
import com.wego.travelmarketplace.application.ArchiveProviderService
import com.wego.travelmarketplace.application.CreateProviderCommand
import com.wego.travelmarketplace.application.CreateProviderService
import com.wego.travelmarketplace.application.ProviderQueryService
import com.wego.travelmarketplace.application.UpdateProviderCommand
import com.wego.travelmarketplace.application.UpdateProviderResult
import com.wego.travelmarketplace.application.UpdateProviderService
import com.wego.travelmarketplace.domain.Provider
import com.wego.travelmarketplace.domain.ProviderId
import com.wego.travelmarketplace.domain.ProviderStatus
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
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
@RequestMapping("/api/v1/travel-marketplace/providers")
class ProviderController(
    private val createProviderService: CreateProviderService,
    private val updateProviderService: UpdateProviderService,
    private val archiveProviderService: ArchiveProviderService,
    private val providerQueryService: ProviderQueryService,
) {
    @PostMapping
    @PreAuthorize("hasAuthority('provider:manage')")
    fun create(
        @Valid @RequestBody request: UpsertProviderRequest,
        authentication: Authentication,
    ): ResponseEntity<ProviderResponse> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        val provider =
            createProviderService.create(
                CreateProviderCommand(request.name, request.contactEmail, request.contactPhone, actorUserId),
            )
        return ResponseEntity.status(HttpStatus.CREATED).body(provider.toResponse())
    }

    @GetMapping
    @PreAuthorize("hasAuthority('provider:view')")
    fun list(
        @RequestParam(required = false) status: ProviderStatus?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false, defaultValue = "0") page: Int,
        @RequestParam(required = false, defaultValue = "50") size: Int,
    ): List<ProviderResponse> = providerQueryService.list(status, search, page, size).map { it.toResponse() }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('provider:view')")
    fun findById(
        @PathVariable id: UUID,
    ): ResponseEntity<ProviderResponse> {
        val provider = providerQueryService.findById(ProviderId(id)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(provider.toResponse())
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('provider:manage')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpsertProviderRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                updateProviderService.update(
                    UpdateProviderCommand(ProviderId(id), request.name, request.contactEmail, request.contactPhone, actorUserId),
                )
        ) {
            is UpdateProviderResult.Updated -> ResponseEntity.ok(result.provider.toResponse())
            UpdateProviderResult.NotFound -> ResponseEntity.notFound().build()
            UpdateProviderResult.Archived -> ResponseEntity.status(HttpStatus.CONFLICT).body(ProviderErrorResponse("archived"))
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('provider:manage')")
    fun archive(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (val result = archiveProviderService.archive(ProviderId(id), actorUserId)) {
            is ArchiveProviderResult.Archived -> ResponseEntity.ok(result.provider.toResponse())
            ArchiveProviderResult.NotFound -> ResponseEntity.notFound().build()
            ArchiveProviderResult.AlreadyArchived ->
                ResponseEntity
                    .status(
                        HttpStatus.CONFLICT,
                    ).body(ProviderErrorResponse("already_archived"))
        }
    }
}

private fun Provider.toResponse() =
    ProviderResponse(
        id = id.value,
        name = name,
        contactEmail = contactEmail,
        contactPhone = contactPhone,
        status = status,
        createdAt = createdAt,
        archivedAt = archivedAt,
    )
