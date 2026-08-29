package com.wego.divers.application

import com.wego.divers.domain.CourseEnrollment
import com.wego.divers.domain.CourseEnrollmentId
import com.wego.divers.domain.DiverId
import com.wego.divers.domain.EnrollmentStage
import com.wego.divers.domain.OfferingId

interface CourseEnrollmentRepository {
    fun findById(id: CourseEnrollmentId): CourseEnrollment?

    fun findAll(
        diverId: DiverId?,
        offeringId: OfferingId?,
        stage: EnrollmentStage?,
        limit: Int,
        offset: Int,
    ): List<CourseEnrollment>

    fun save(enrollment: CourseEnrollment)
}
