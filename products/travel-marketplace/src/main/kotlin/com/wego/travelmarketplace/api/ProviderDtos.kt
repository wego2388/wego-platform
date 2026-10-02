package com.wego.travelmarketplace.api

import com.wego.travelmarketplace.domain.ProviderStatus
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class UpsertProviderRequest(
    @field:NotBlank val name: String,
    val contactEmail: String?,
    val contactPhone: String?,
    // Null on create; required on update — see ServiceDtos.kt's
    // UpsertServiceRequest.expectedVersion for the full reasoning.
    val expectedVersion: Int?,
)

data class ProviderResponse(
    val id: UUID,
    val name: String,
    val contactEmail: String?,
    val contactPhone: String?,
    val status: ProviderStatus,
    val createdAt: Instant,
    val archivedAt: Instant?,
    val version: Int,
)

data class ProviderErrorResponse(
    val error: String,
    val currentVersion: Int? = null,
)
