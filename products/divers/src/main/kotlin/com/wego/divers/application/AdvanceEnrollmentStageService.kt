package com.wego.divers.application

import com.wego.divers.domain.CourseEnrollment
import com.wego.divers.domain.CourseEnrollmentId
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface AdvanceEnrollmentStageResult {
    data class Advanced(
        val enrollment: CourseEnrollment,
    ) : AdvanceEnrollmentStageResult

    data object NotFound : AdvanceEnrollmentStageResult

    data object EnrollmentFinished : AdvanceEnrollmentStageResult
}

class AdvanceEnrollmentStageService(
    private val enrollmentRepository: CourseEnrollmentRepository,
    private val enrollmentAuditRecorder: CourseEnrollmentAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun advance(
        enrollmentId: CourseEnrollmentId,
        actorUserId: UUID?,
        correlationId: UUID?,
    ): AdvanceEnrollmentStageResult =
        transactionRunner.runInTransaction {
            val enrollment = enrollmentRepository.findById(enrollmentId) ?: return@runInTransaction AdvanceEnrollmentStageResult.NotFound
            if (enrollment.isFinished) return@runInTransaction AdvanceEnrollmentStageResult.EnrollmentFinished

            val fromStage = enrollment.stage
            val now = Instant.now(clock)
            enrollment.advance(now)
            enrollmentRepository.save(enrollment)
            enrollmentAuditRecorder.recordStageAdvanced(enrollment.id, actorUserId, now, fromStage, enrollment.stage, correlationId)
            AdvanceEnrollmentStageResult.Advanced(enrollment)
        }
}
