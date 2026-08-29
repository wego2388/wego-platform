package com.wego.divers.api

import com.wego.divers.domain.DiverStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val MAX_NAME_LENGTH = 200
private const val MAX_EMAIL_LENGTH = 320
private const val MAX_PHONE_LENGTH = 32
private const val MAX_SHORT_TEXT_LENGTH = 100
private const val MAX_MEDICAL_NOTES_LENGTH = 4000
private const val MAX_SIZE_LENGTH = 16
private const val MAX_CERTIFICATION_TEXT_LENGTH = 200
private const val MAX_CERTIFICATION_NUMBER_LENGTH = 64

data class DiverCertificationDto(
    val id: UUID?,
    @field:NotBlank
    @field:Size(max = MAX_CERTIFICATION_TEXT_LENGTH)
    val agency: String,
    @field:NotBlank
    @field:Size(max = MAX_CERTIFICATION_TEXT_LENGTH)
    val level: String,
    @field:Size(max = MAX_CERTIFICATION_NUMBER_LENGTH)
    val certificationNumber: String?,
    val issuedOn: LocalDate?,
)

data class UpsertDiverRequest(
    @field:NotBlank
    @field:Size(max = MAX_NAME_LENGTH)
    val fullName: String,
    @field:Size(max = MAX_SHORT_TEXT_LENGTH)
    val nationality: String?,
    @field:Size(max = MAX_SHORT_TEXT_LENGTH)
    val primaryLanguage: String?,
    @field:Email
    @field:Size(max = MAX_EMAIL_LENGTH)
    val email: String?,
    @field:Size(max = MAX_PHONE_LENGTH)
    val phone: String?,
    @field:Size(max = MAX_NAME_LENGTH)
    val emergencyContactName: String?,
    @field:Size(max = MAX_PHONE_LENGTH)
    val emergencyContactPhone: String?,
    @field:Size(max = MAX_MEDICAL_NOTES_LENGTH)
    val medicalNotes: String?,
    @field:PositiveOrZero
    val totalLoggedDives: Int = 0,
    @field:PositiveOrZero
    @field:Digits(integer = 4, fraction = 1)
    val maxDepthMeters: BigDecimal?,
    val lastDiveOn: LocalDate?,
    @field:Size(max = MAX_SIZE_LENGTH)
    val bcdSize: String?,
    @field:Size(max = MAX_SIZE_LENGTH)
    val finSize: String?,
    @field:Size(max = MAX_SIZE_LENGTH)
    val wetsuitSize: String?,
    @field:Valid
    val certifications: List<DiverCertificationDto> = emptyList(),
)

data class DiverResponse(
    val id: UUID,
    val fullName: String,
    val nationality: String?,
    val primaryLanguage: String?,
    val email: String?,
    val phone: String?,
    val emergencyContactName: String?,
    val emergencyContactPhone: String?,
    val medicalNotes: String?,
    val totalLoggedDives: Int,
    val maxDepthMeters: BigDecimal?,
    val lastDiveOn: LocalDate?,
    val bcdSize: String?,
    val finSize: String?,
    val wetsuitSize: String?,
    val certifications: List<DiverCertificationDto>,
    val status: DiverStatus,
    val createdAt: Instant,
    val archivedAt: Instant?,
)

data class DiverCertificationSummaryDto(
    val agency: String,
    val level: String,
    val issuedOn: LocalDate?,
)

/**
 * The roster/list projection — deliberately excludes email, phone,
 * emergency contact, medical notes and certification numbers. `diver:view`
 * covers everyday search/browse, and bulk-serializing that sensitive PII
 * for every row of a page-sized list (up to 200) was a real over-exposure
 * Tier 1 review found. Dive stats and the certification agency/level
 * (without the number) stay — they're operational, not personally
 * sensitive. The full [DiverResponse] stays on the single-record `GET /{id}`.
 */
data class DiverSummaryResponse(
    val id: UUID,
    val fullName: String,
    val nationality: String?,
    val primaryLanguage: String?,
    val totalLoggedDives: Int,
    val maxDepthMeters: BigDecimal?,
    val lastDiveOn: LocalDate?,
    val certifications: List<DiverCertificationSummaryDto>,
    val status: DiverStatus,
    val createdAt: Instant,
    val archivedAt: Instant?,
)

data class DiverErrorResponse(
    val error: String,
)
