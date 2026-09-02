package com.wego.travelmarketplace.api

import com.wego.travelmarketplace.domain.ProviderStatus
import jakarta.validation.constraints.NotBlank
import java.time.Instant
import java.util.UUID

data class UpsertProviderRequest(
    @field:NotBlank val name: String,
    val contactEmail: String?,
    val contactPhone: String?,
)

data class ProviderResponse(
    val id: UUID,
    val name: String,
    val contactEmail: String?,
    val contactPhone: String?,
    val status: ProviderStatus,
    val createdAt: Instant,
    val archivedAt: Instant?,
)

data class ProviderErrorResponse(
    val error: String,
)
