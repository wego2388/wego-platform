package com.wego.divers.application

import com.wego.divers.domain.CourseEnrollment
import com.wego.divers.domain.CourseEnrollmentId
import com.wego.divers.domain.DiverId
import com.wego.divers.domain.OfferingId
import com.wego.divers.domain.OfferingType
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class EnrollDiverInCourseCommand(
    val diverId: DiverId,
    val offeringId: OfferingId,
    val createdByUserId: UUID?,
    val correlationId: UUID?,
)

sealed interface EnrollDiverInCourseResult {
    data class Enrolled(
        val enrollment: CourseEnrollment,
    ) : EnrollDiverInCourseResult

    data object DiverNotFound : EnrollDiverInCourseResult

    data object OfferingNotFound : EnrollDiverInCourseResult

    /** Only a real COURSE offering has a certification pipeline — a dive trip or rental has nothing to enroll into. */
    data object OfferingIsNotACourse : EnrollDiverInCourseResult
}

class EnrollDiverInCourseService(
    private val diverRepository: DiverRepository,
    private val offeringRepository: OfferingRepository,
    private val enrollmentRepository: CourseEnrollmentRepository,
    private val enrollmentAuditRecorder: CourseEnrollmentAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun enroll(command: EnrollDiverInCourseCommand): EnrollDiverInCourseResult =
        transactionRunner.runInTransaction {
            if (diverRepository.findById(command.diverId) == null) return@runInTransaction EnrollDiverInCourseResult.DiverNotFound
            val offering =
                offeringRepository.findById(command.offeringId) ?: return@runInTransaction EnrollDiverInCourseResult.OfferingNotFound
            if (offering.offeringType != OfferingType.COURSE) return@runInTransaction EnrollDiverInCourseResult.OfferingIsNotACourse

            val now = Instant.now(clock)
            val enrollment =
                CourseEnrollment.create(
                    id = CourseEnrollmentId.generate(),
                    diverId = command.diverId,
                    offeringId = command.offeringId,
                    createdByUserId = command.createdByUserId,
                    now = now,
                )
            enrollmentRepository.save(enrollment)
            enrollmentAuditRecorder.recordEnrollmentCreated(enrollment.id, command.createdByUserId, now, command.correlationId)
            EnrollDiverInCourseResult.Enrolled(enrollment)
        }
}
