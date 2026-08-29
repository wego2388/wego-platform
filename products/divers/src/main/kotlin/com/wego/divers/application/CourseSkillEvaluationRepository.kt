package com.wego.divers.application

import com.wego.divers.domain.CourseEnrollmentId
import com.wego.divers.domain.CourseSkillEvaluation

interface CourseSkillEvaluationRepository {
    fun findByEnrollmentId(
        enrollmentId: CourseEnrollmentId,
        limit: Int,
        offset: Int,
    ): List<CourseSkillEvaluation>

    fun save(evaluation: CourseSkillEvaluation)
}
