package com.wego.divers.infrastructure

import com.wego.divers.application.CourseSkillEvaluationRepository
import com.wego.divers.domain.CourseEnrollmentId
import com.wego.divers.domain.CourseSkillEvaluation
import com.wego.generated.jooq.tables.DiversCourseSkillEvaluation.DIVERS_COURSE_SKILL_EVALUATION
import com.wego.generated.jooq.tables.records.DiversCourseSkillEvaluationRecord
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqCourseSkillEvaluationRepository(
    private val dsl: DSLContext,
) : CourseSkillEvaluationRepository {
    @Transactional(readOnly = true)
    override fun findByEnrollmentId(
        enrollmentId: CourseEnrollmentId,
        limit: Int,
        offset: Int,
    ): List<CourseSkillEvaluation> =
        dsl
            .selectFrom(DIVERS_COURSE_SKILL_EVALUATION)
            .where(DIVERS_COURSE_SKILL_EVALUATION.ENROLLMENT_ID.eq(enrollmentId.value))
            .orderBy(DIVERS_COURSE_SKILL_EVALUATION.EVALUATED_ON.desc(), DIVERS_COURSE_SKILL_EVALUATION.ID)
            .limit(limit)
            .offset(offset)
            .fetch()
            .map(::toDomain)

    @Transactional
    override fun save(evaluation: CourseSkillEvaluation) {
        dsl
            .insertInto(DIVERS_COURSE_SKILL_EVALUATION)
            .set(DIVERS_COURSE_SKILL_EVALUATION.ID, evaluation.id)
            .set(DIVERS_COURSE_SKILL_EVALUATION.ENROLLMENT_ID, evaluation.enrollmentId.value)
            .set(DIVERS_COURSE_SKILL_EVALUATION.SKILL_NAME, evaluation.skillName)
            .set(DIVERS_COURSE_SKILL_EVALUATION.PASSED, evaluation.passed)
            .set(DIVERS_COURSE_SKILL_EVALUATION.EVALUATED_BY_USER_ID, evaluation.evaluatedByUserId)
            .set(DIVERS_COURSE_SKILL_EVALUATION.EVALUATED_ON, evaluation.evaluatedOn)
            .set(DIVERS_COURSE_SKILL_EVALUATION.NOTES, evaluation.notes)
            .set(DIVERS_COURSE_SKILL_EVALUATION.CREATED_AT, toOffset(evaluation.createdAt))
            .execute()
    }

    private fun toDomain(record: DiversCourseSkillEvaluationRecord): CourseSkillEvaluation =
        CourseSkillEvaluation(
            id = record.id,
            enrollmentId = CourseEnrollmentId(record.enrollmentId),
            skillName = record.skillName,
            passed = record.passed,
            evaluatedByUserId = record.evaluatedByUserId,
            evaluatedOn = record.evaluatedOn,
            notes = record.notes,
            createdAt = record.createdAt.toInstant(),
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
