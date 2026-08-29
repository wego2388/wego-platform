package com.wego.divers.domain

import java.time.Instant
import java.util.UUID

class CourseEnrollment(
    val id: CourseEnrollmentId,
    val diverId: DiverId,
    val offeringId: OfferingId,
    instructorUserId: UUID?,
    stage: EnrollmentStage,
    val startedAt: Instant,
    certifiedAt: Instant?,
    withdrawnAt: Instant?,
    val createdByUserId: UUID?,
    val createdAt: Instant,
) {
    var instructorUserId: UUID? = instructorUserId
        private set

    var stage: EnrollmentStage = stage
        private set

    var certifiedAt: Instant? = certifiedAt
        private set

    var withdrawnAt: Instant? = withdrawnAt
        private set

    init {
        require((stage == EnrollmentStage.CERTIFIED) == (certifiedAt != null)) {
            "certifiedAt must be set if and only if the enrollment is certified"
        }
        require((stage == EnrollmentStage.WITHDRAWN) == (withdrawnAt != null)) {
            "withdrawnAt must be set if and only if the enrollment is withdrawn"
        }
    }

    val isFinished: Boolean get() = stage == EnrollmentStage.CERTIFIED || stage == EnrollmentStage.WITHDRAWN

    fun assignInstructor(userId: UUID) {
        require(!isFinished) { "Cannot assign an instructor to a finished enrollment" }
        instructorUserId = userId
    }

    /** Moves exactly one step forward in the real Lead -> Theory -> Pool -> Open Water -> Certified pipeline — never skips a stage. */
    fun advance(now: Instant) {
        require(!isFinished) { "Enrollment has already finished" }
        val currentIndex = EnrollmentStage.PROGRESSION.indexOf(stage)
        stage = EnrollmentStage.PROGRESSION[currentIndex + 1]
        if (stage == EnrollmentStage.CERTIFIED) certifiedAt = now
    }

    /** Terminal from any non-finished stage. */
    fun withdraw(now: Instant) {
        require(!isFinished) { "Enrollment has already finished" }
        stage = EnrollmentStage.WITHDRAWN
        withdrawnAt = now
    }

    companion object {
        fun create(
            id: CourseEnrollmentId,
            diverId: DiverId,
            offeringId: OfferingId,
            createdByUserId: UUID?,
            now: Instant,
        ): CourseEnrollment =
            CourseEnrollment(
                id = id,
                diverId = diverId,
                offeringId = offeringId,
                instructorUserId = null,
                stage = EnrollmentStage.LEAD,
                startedAt = now,
                certifiedAt = null,
                withdrawnAt = null,
                createdByUserId = createdByUserId,
                createdAt = now,
            )
    }
}
