package com.wego.divers.infrastructure

import com.wego.divers.application.CourseEnrollmentAuditRecorder
import com.wego.divers.domain.CourseEnrollmentId
import com.wego.divers.domain.EnrollmentStage
import com.wego.generated.jooq.tables.DiversCourseEnrollmentAuditEvent.DIVERS_COURSE_ENROLLMENT_AUDIT_EVENT
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Component
class JooqCourseEnrollmentAuditRecorder(
    private val dsl: DSLContext,
) : CourseEnrollmentAuditRecorder {
    @Transactional
    override fun recordEnrollmentCreated(
        enrollmentId: CourseEnrollmentId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    ) = insert(enrollmentId, "ENROLLMENT_CREATED", actorUserId, occurredAt, null, null, correlationId)

    @Transactional
    override fun recordStageAdvanced(
        enrollmentId: CourseEnrollmentId,
        actorUserId: UUID?,
        occurredAt: Instant,
        fromStage: EnrollmentStage,
        toStage: EnrollmentStage,
        correlationId: UUID?,
    ) = insert(enrollmentId, "STAGE_ADVANCED", actorUserId, occurredAt, fromStage.name, toStage.name, correlationId)

    @Transactional
    override fun recordWithdrawn(
        enrollmentId: CourseEnrollmentId,
        actorUserId: UUID?,
        occurredAt: Instant,
        fromStage: EnrollmentStage,
        correlationId: UUID?,
    ) = insert(enrollmentId, "ENROLLMENT_WITHDRAWN", actorUserId, occurredAt, fromStage.name, EnrollmentStage.WITHDRAWN.name, correlationId)

    private fun insert(
        enrollmentId: CourseEnrollmentId,
        eventType: String,
        actorUserId: UUID?,
        occurredAt: Instant,
        fromStage: String?,
        toStage: String?,
        correlationId: UUID?,
    ) {
        dsl
            .insertInto(DIVERS_COURSE_ENROLLMENT_AUDIT_EVENT)
            .set(DIVERS_COURSE_ENROLLMENT_AUDIT_EVENT.ID, UUID.randomUUID())
            .set(DIVERS_COURSE_ENROLLMENT_AUDIT_EVENT.ENROLLMENT_ID, enrollmentId.value)
            .set(DIVERS_COURSE_ENROLLMENT_AUDIT_EVENT.OCCURRED_AT, OffsetDateTime.ofInstant(occurredAt, ZoneOffset.UTC))
            .set(DIVERS_COURSE_ENROLLMENT_AUDIT_EVENT.EVENT_TYPE, eventType)
            .set(DIVERS_COURSE_ENROLLMENT_AUDIT_EVENT.ACTOR_USER_ID, actorUserId)
            .set(DIVERS_COURSE_ENROLLMENT_AUDIT_EVENT.FROM_STAGE, fromStage)
            .set(DIVERS_COURSE_ENROLLMENT_AUDIT_EVENT.TO_STAGE, toStage)
            .set(DIVERS_COURSE_ENROLLMENT_AUDIT_EVENT.CORRELATION_ID, correlationId)
            .execute()
    }
}
