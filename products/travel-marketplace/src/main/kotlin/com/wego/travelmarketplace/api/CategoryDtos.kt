package com.wego.travelmarketplace.api

import com.wego.travelmarketplace.domain.CategoryStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.Instant
import java.util.UUID

data class UpsertCategoryRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^[a-z][a-z0-9-]*$", message = "must be lowercase-kebab-case")
    val code: String,
    @field:Valid val name: LocalizedTextDto,
    @field:Valid val description: LocalizedTextDto?,
    @field:Min(0) val displayOrder: Int,
)

data class CategoryResponse(
    val id: UUID,
    val code: String,
    val name: LocalizedTextDto,
    val description: LocalizedTextDto?,
    val displayOrder: Int,
    val status: CategoryStatus,
    val createdAt: Instant,
    val archivedAt: Instant?,
)

data class CategoryErrorResponse(
    val error: String,
)
