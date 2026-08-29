package com.wego.divers.application

import com.wego.divers.domain.CourseEnrollment
import com.wego.divers.domain.CourseEnrollmentId
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface WithdrawEnrollmentResult {
    data class Withdrawn(
        val enrollment: CourseEnrollment,
    ) : WithdrawEnrollmentResult

    data object NotFound : WithdrawEnrollmentResult

    data object EnrollmentFinished : WithdrawEnrollmentResult
}

class WithdrawEnrollmentService(
    private val enrollmentRepository: CourseEnrollmentRepository,
    private val enrollmentAuditRecorder: CourseEnrollmentAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun withdraw(
        enrollmentId: CourseEnrollmentId,
        actorUserId: UUID?,
        correlationId: UUID?,
    ): WithdrawEnrollmentResult =
        transactionRunner.runInTransaction {
            val enrollment = enrollmentRepository.findById(enrollmentId) ?: return@runInTransaction WithdrawEnrollmentResult.NotFound
            if (enrollment.isFinished) return@runInTransaction WithdrawEnrollmentResult.EnrollmentFinished

            val fromStage = enrollment.stage
            val now = Instant.now(clock)
            enrollment.withdraw(now)
            enrollmentRepository.save(enrollment)
            enrollmentAuditRecorder.recordWithdrawn(enrollment.id, actorUserId, now, fromStage, correlationId)
            WithdrawEnrollmentResult.Withdrawn(enrollment)
        }
}
