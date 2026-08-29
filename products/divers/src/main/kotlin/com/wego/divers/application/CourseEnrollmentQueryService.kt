package com.wego.divers.application

import com.wego.divers.domain.CourseEnrollment
import com.wego.divers.domain.CourseEnrollmentId
import com.wego.divers.domain.CourseSkillEvaluation
import com.wego.divers.domain.DiverId
import com.wego.divers.domain.EnrollmentStage
import com.wego.divers.domain.OfferingId

class CourseEnrollmentQueryService(
    private val enrollmentRepository: CourseEnrollmentRepository,
    private val skillEvaluationRepository: CourseSkillEvaluationRepository,
) {
    fun findById(id: CourseEnrollmentId): CourseEnrollment? = enrollmentRepository.findById(id)

    fun list(
        diverId: DiverId?,
        offeringId: OfferingId?,
        stage: EnrollmentStage?,
        page: Int = 0,
        size: Int = Pagination.DEFAULT_PAGE_SIZE,
    ): List<CourseEnrollment> =
        enrollmentRepository.findAll(
            diverId,
            offeringId,
            stage,
            limit = Pagination.boundedSize(size),
            offset = Pagination.offsetFor(page, size),
        )

    fun listSkillEvaluations(
        enrollmentId: CourseEnrollmentId,
        page: Int = 0,
        size: Int = Pagination.DEFAULT_PAGE_SIZE,
    ): List<CourseSkillEvaluation> =
        skillEvaluationRepository.findByEnrollmentId(
            enrollmentId,
            limit = Pagination.boundedSize(size),
            offset = Pagination.offsetFor(page, size),
        )
}
