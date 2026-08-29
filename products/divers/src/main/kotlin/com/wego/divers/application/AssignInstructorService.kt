package com.wego.divers.application

import com.wego.divers.domain.CourseEnrollment
import com.wego.divers.domain.CourseEnrollmentId
import java.util.UUID

sealed interface AssignInstructorResult {
    data class Assigned(
        val enrollment: CourseEnrollment,
    ) : AssignInstructorResult

    data object NotFound : AssignInstructorResult

    data object EnrollmentFinished : AssignInstructorResult
}

class AssignInstructorService(
    private val enrollmentRepository: CourseEnrollmentRepository,
    private val transactionRunner: TransactionRunner,
) {
    fun assign(
        enrollmentId: CourseEnrollmentId,
        instructorUserId: UUID,
    ): AssignInstructorResult =
        transactionRunner.runInTransaction {
            val enrollment = enrollmentRepository.findById(enrollmentId) ?: return@runInTransaction AssignInstructorResult.NotFound
            if (enrollment.isFinished) return@runInTransaction AssignInstructorResult.EnrollmentFinished

            enrollment.assignInstructor(instructorUserId)
            enrollmentRepository.save(enrollment)
            AssignInstructorResult.Assigned(enrollment)
        }
}
