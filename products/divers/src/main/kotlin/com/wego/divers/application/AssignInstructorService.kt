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

    /** Not a real, currently-active staff account — a disabled or unrelated user id must never be assignable as an instructor. */
    data object InstructorNotActiveStaff : AssignInstructorResult
}

class AssignInstructorService(
    private val enrollmentRepository: CourseEnrollmentRepository,
    private val staffUserLookup: StaffUserLookup,
    private val transactionRunner: TransactionRunner,
) {
    fun assign(
        enrollmentId: CourseEnrollmentId,
        instructorUserId: UUID,
    ): AssignInstructorResult =
        transactionRunner.runInTransaction {
            val enrollment =
                enrollmentRepository.findByIdForUpdate(enrollmentId) ?: return@runInTransaction AssignInstructorResult.NotFound
            if (enrollment.isFinished) return@runInTransaction AssignInstructorResult.EnrollmentFinished
            if (!staffUserLookup.isActiveStaffUser(instructorUserId)) {
                return@runInTransaction AssignInstructorResult.InstructorNotActiveStaff
            }

            enrollment.assignInstructor(instructorUserId)
            enrollmentRepository.save(enrollment)
            AssignInstructorResult.Assigned(enrollment)
        }
}
