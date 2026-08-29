package com.wego.divers.application

import com.wego.divers.domain.CourseEnrollmentId
import com.wego.divers.domain.EnrollmentStage
import java.time.Instant
import java.util.UUID

interface CourseEnrollmentAuditRecorder {
    fun recordEnrollmentCreated(
        enrollmentId: CourseEnrollmentId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordStageAdvanced(
        enrollmentId: CourseEnrollmentId,
        actorUserId: UUID?,
        occurredAt: Instant,
        fromStage: EnrollmentStage,
        toStage: EnrollmentStage,
        correlationId: UUID?,
    )

    fun recordWithdrawn(
        enrollmentId: CourseEnrollmentId,
        actorUserId: UUID?,
        occurredAt: Instant,
        fromStage: EnrollmentStage,
        correlationId: UUID?,
    )
}
