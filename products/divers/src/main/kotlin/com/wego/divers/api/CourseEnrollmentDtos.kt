package com.wego.divers.api

import com.wego.divers.domain.EnrollmentStage
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val MAX_SKILL_NAME_LENGTH = 200
private const val MAX_NOTES_LENGTH = 1000

data class EnrollDiverInCourseRequest(
    @field:NotNull
    val diverId: UUID,
    @field:NotNull
    val offeringId: UUID,
)

data class AssignInstructorRequest(
    @field:NotNull
    val instructorUserId: UUID,
)

data class CourseEnrollmentResponse(
    val id: UUID,
    val diverId: UUID,
    val offeringId: UUID,
    val instructorUserId: UUID?,
    val stage: EnrollmentStage,
    val startedAt: Instant,
    val certifiedAt: Instant?,
    val withdrawnAt: Instant?,
    val createdAt: Instant,
)

data class RecordSkillEvaluationRequest(
    @field:NotBlank
    @field:Size(max = MAX_SKILL_NAME_LENGTH)
    val skillName: String,
    @field:NotNull
    val passed: Boolean,
    val evaluatedOn: LocalDate,
    @field:Size(max = MAX_NOTES_LENGTH)
    val notes: String?,
)

data class SkillEvaluationResponse(
    val id: UUID,
    val enrollmentId: UUID,
    val skillName: String,
    val passed: Boolean,
    val evaluatedByUserId: UUID?,
    val evaluatedOn: LocalDate,
    val notes: String?,
    val createdAt: Instant,
)

data class CourseEnrollmentErrorResponse(
    val error: String,
)
