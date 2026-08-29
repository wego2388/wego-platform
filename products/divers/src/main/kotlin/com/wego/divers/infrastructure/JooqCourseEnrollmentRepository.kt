package com.wego.divers.infrastructure

import com.wego.divers.application.CourseEnrollmentRepository
import com.wego.divers.domain.CourseEnrollment
import com.wego.divers.domain.CourseEnrollmentId
import com.wego.divers.domain.DiverId
import com.wego.divers.domain.EnrollmentStage
import com.wego.divers.domain.OfferingId
import com.wego.generated.jooq.tables.DiversCourseEnrollment.DIVERS_COURSE_ENROLLMENT
import com.wego.generated.jooq.tables.records.DiversCourseEnrollmentRecord
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqCourseEnrollmentRepository(
    private val dsl: DSLContext,
) : CourseEnrollmentRepository {
    @Transactional(readOnly = true)
    override fun findById(id: CourseEnrollmentId): CourseEnrollment? {
        val record = dsl.selectFrom(DIVERS_COURSE_ENROLLMENT).where(DIVERS_COURSE_ENROLLMENT.ID.eq(id.value)).fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional(readOnly = true)
    override fun findAll(
        diverId: DiverId?,
        offeringId: OfferingId?,
        stage: EnrollmentStage?,
        limit: Int,
        offset: Int,
    ): List<CourseEnrollment> {
        var condition = DSL.noCondition()
        if (diverId != null) {
            condition = condition.and(DIVERS_COURSE_ENROLLMENT.DIVER_ID.eq(diverId.value))
        }
        if (offeringId != null) {
            condition = condition.and(DIVERS_COURSE_ENROLLMENT.OFFERING_ID.eq(offeringId.value))
        }
        if (stage != null) {
            condition = condition.and(DIVERS_COURSE_ENROLLMENT.STAGE.eq(stage.name))
        }
        return dsl
            .selectFrom(DIVERS_COURSE_ENROLLMENT)
            .where(condition)
            .orderBy(DIVERS_COURSE_ENROLLMENT.STARTED_AT.desc(), DIVERS_COURSE_ENROLLMENT.ID)
            .limit(limit)
            .offset(offset)
            .fetch()
            .map(::toDomain)
    }

    @Transactional
    override fun save(enrollment: CourseEnrollment) {
        dsl
            .insertInto(DIVERS_COURSE_ENROLLMENT)
            .set(DIVERS_COURSE_ENROLLMENT.ID, enrollment.id.value)
            .set(DIVERS_COURSE_ENROLLMENT.DIVER_ID, enrollment.diverId.value)
            .set(DIVERS_COURSE_ENROLLMENT.OFFERING_ID, enrollment.offeringId.value)
            .set(DIVERS_COURSE_ENROLLMENT.INSTRUCTOR_USER_ID, enrollment.instructorUserId)
            .set(DIVERS_COURSE_ENROLLMENT.STAGE, enrollment.stage.name)
            .set(DIVERS_COURSE_ENROLLMENT.STARTED_AT, toOffset(enrollment.startedAt))
            .set(DIVERS_COURSE_ENROLLMENT.CERTIFIED_AT, enrollment.certifiedAt?.let(::toOffset))
            .set(DIVERS_COURSE_ENROLLMENT.WITHDRAWN_AT, enrollment.withdrawnAt?.let(::toOffset))
            .set(DIVERS_COURSE_ENROLLMENT.CREATED_BY_USER_ID, enrollment.createdByUserId)
            .set(DIVERS_COURSE_ENROLLMENT.CREATED_AT, toOffset(enrollment.createdAt))
            .onConflict(DIVERS_COURSE_ENROLLMENT.ID)
            .doUpdate()
            .set(DIVERS_COURSE_ENROLLMENT.INSTRUCTOR_USER_ID, enrollment.instructorUserId)
            .set(DIVERS_COURSE_ENROLLMENT.STAGE, enrollment.stage.name)
            .set(DIVERS_COURSE_ENROLLMENT.CERTIFIED_AT, enrollment.certifiedAt?.let(::toOffset))
            .set(DIVERS_COURSE_ENROLLMENT.WITHDRAWN_AT, enrollment.withdrawnAt?.let(::toOffset))
            .execute()
    }

    private fun toDomain(record: DiversCourseEnrollmentRecord): CourseEnrollment =
        CourseEnrollment(
            id = CourseEnrollmentId(record.id),
            diverId = DiverId(record.diverId),
            offeringId = OfferingId(record.offeringId),
            instructorUserId = record.instructorUserId,
            stage = EnrollmentStage.valueOf(record.stage),
            startedAt = record.startedAt.toInstant(),
            certifiedAt = record.certifiedAt?.toInstant(),
            withdrawnAt = record.withdrawnAt?.toInstant(),
            createdByUserId = record.createdByUserId,
            createdAt = record.createdAt.toInstant(),
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
