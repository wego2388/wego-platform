package com.wego.divers.application
import com.wego.divers.domain.CourseEnrollment
import com.wego.divers.domain.CourseEnrollmentId
import com.wego.divers.domain.DiverId
import com.wego.divers.domain.OfferingId
import com.wego.divers.domain.OfferingStatus
import com.wego.divers.domain.OfferingType
import com.wego.transaction.TransactionRunner
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

    /** An archived diver has ended their relationship with the center — starting a new course for them is a real-world error, not a state transition. */
    data object DiverNotActive : EnrollDiverInCourseResult

    /** A closed course offering no longer runs — nothing to enroll into. */
    data object OfferingNotActive : EnrollDiverInCourseResult
}

class EnrollDiverInCourseService(
    private val diverRepository: DiverRepository,
    private val offeringRepository: OfferingRepository,
    private val enrollmentRepository: CourseEnrollmentRepository,
    private val enrollmentAuditRecorder: CourseEnrollmentAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    // Locks both the diver and the offering row — in that fixed order, always — for the duration of the
    // check-then-create. Without this, a concurrent ArchiveDiverService/CloseOfferingService call (which lock
    // the same rows) can commit its own transition in between this service's unlocked read and its insert,
    // leaving a real enrollment attached to a diver/offering that was already archived/closed by the time this
    // transaction committed. No other path in this codebase locks both a diver and an offering row in one
    // transaction, so this fixed order (diver first) cannot deadlock against anything else.
    fun enroll(command: EnrollDiverInCourseCommand): EnrollDiverInCourseResult =
        transactionRunner.runInTransaction {
            val diver =
                diverRepository.findByIdForUpdate(command.diverId) ?: return@runInTransaction EnrollDiverInCourseResult.DiverNotFound
            if (!diver.isActive) return@runInTransaction EnrollDiverInCourseResult.DiverNotActive
            val offering =
                offeringRepository.findByIdForUpdate(command.offeringId)
                    ?: return@runInTransaction EnrollDiverInCourseResult.OfferingNotFound
            if (offering.offeringType != OfferingType.COURSE) return@runInTransaction EnrollDiverInCourseResult.OfferingIsNotACourse
            if (offering.status != OfferingStatus.ACTIVE) return@runInTransaction EnrollDiverInCourseResult.OfferingNotActive

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
