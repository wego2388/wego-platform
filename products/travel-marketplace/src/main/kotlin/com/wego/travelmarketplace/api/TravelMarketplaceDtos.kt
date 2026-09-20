package com.wego.travelmarketplace.api

import com.wego.travelmarketplace.domain.LocalizedText
import jakarta.validation.constraints.NotBlank

data class ValidationErrorResponse(
    val error: String = "validation_failed",
    val message: String,
)

data class LocalizedTextDto(
    @field:NotBlank val en: String,
    @field:NotBlank val ar: String,
)

fun LocalizedTextDto.toDomain(): LocalizedText = LocalizedText(en, ar)

fun LocalizedText.toDto(): LocalizedTextDto = LocalizedTextDto(en, ar)
