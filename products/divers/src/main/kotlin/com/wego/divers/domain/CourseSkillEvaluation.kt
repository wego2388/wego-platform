package com.wego.divers.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** One real skill assessment during a course — append-only, never edited or removed. */
data class CourseSkillEvaluation(
    val id: UUID,
    val enrollmentId: CourseEnrollmentId,
    val skillName: String,
    val passed: Boolean,
    val evaluatedByUserId: UUID?,
    val evaluatedOn: LocalDate,
    val notes: String?,
    val createdAt: Instant,
) {
    init {
        require(skillName.isNotBlank()) { "Skill name must not be blank" }
    }
}
