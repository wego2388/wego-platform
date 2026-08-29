package com.wego.divers.api

import com.wego.divers.application.AdvanceEnrollmentStageResult
import com.wego.divers.application.AdvanceEnrollmentStageService
import com.wego.divers.application.AssignInstructorResult
import com.wego.divers.application.AssignInstructorService
import com.wego.divers.application.CourseEnrollmentQueryService
import com.wego.divers.application.EnrollDiverInCourseCommand
import com.wego.divers.application.EnrollDiverInCourseResult
import com.wego.divers.application.EnrollDiverInCourseService
import com.wego.divers.application.RecordSkillEvaluationCommand
import com.wego.divers.application.RecordSkillEvaluationResult
import com.wego.divers.application.RecordSkillEvaluationService
import com.wego.divers.application.WithdrawEnrollmentResult
import com.wego.divers.application.WithdrawEnrollmentService
import com.wego.divers.domain.CourseEnrollment
import com.wego.divers.domain.CourseEnrollmentId
import com.wego.divers.domain.CourseSkillEvaluation
import com.wego.divers.domain.DiverId
import com.wego.divers.domain.EnrollmentStage
import com.wego.divers.domain.OfferingId
import com.wego.events.CorrelationContext
import com.wego.identity.AuthenticatedUser
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Validated
@RestController
@RequestMapping("/api/v1/divers/course-enrollments")
class CourseEnrollmentController(
    private val enrollDiverInCourseService: EnrollDiverInCourseService,
    private val assignInstructorService: AssignInstructorService,
    private val advanceEnrollmentStageService: AdvanceEnrollmentStageService,
    private val withdrawEnrollmentService: WithdrawEnrollmentService,
    private val recordSkillEvaluationService: RecordSkillEvaluationService,
    private val courseEnrollmentQueryService: CourseEnrollmentQueryService,
) {
    @PostMapping
    @PreAuthorize("hasAuthority('course:manage')")
    fun enroll(
        @Valid @RequestBody request: EnrollDiverInCourseRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                enrollDiverInCourseService.enroll(
                    EnrollDiverInCourseCommand(
                        diverId = DiverId(request.diverId),
                        offeringId = OfferingId(request.offeringId),
                        createdByUserId = actorUserId,
                        correlationId = CorrelationContext.currentCorrelationId(),
                    ),
                )
        ) {
            is EnrollDiverInCourseResult.Enrolled -> ResponseEntity.status(HttpStatus.CREATED).body(result.enrollment.toResponse())
            EnrollDiverInCourseResult.DiverNotFound ->
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CourseEnrollmentErrorResponse("diver_not_found"))
            EnrollDiverInCourseResult.OfferingNotFound ->
                ResponseEntity.status(HttpStatus.BAD_REQUEST).body(CourseEnrollmentErrorResponse("offering_not_found"))
            EnrollDiverInCourseResult.OfferingIsNotACourse ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(CourseEnrollmentErrorResponse("offering_is_not_a_course"))
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('course:view')")
    fun list(
        @RequestParam(required = false) diverId: UUID?,
        @RequestParam(required = false) offeringId: UUID?,
        @RequestParam(required = false) stage: EnrollmentStage?,
        @RequestParam(required = false, defaultValue = "0") @Min(0) page: Int,
        @RequestParam(required = false, defaultValue = "50") @Min(1) @Max(200) size: Int,
    ): List<CourseEnrollmentResponse> =
        courseEnrollmentQueryService
            .list(diverId?.let(::DiverId), offeringId?.let(::OfferingId), stage, page, size)
            .map { it.toResponse() }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('course:view')")
    fun findById(
        @PathVariable id: UUID,
    ): ResponseEntity<CourseEnrollmentResponse> {
        val enrollment = courseEnrollmentQueryService.findById(CourseEnrollmentId(id)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(enrollment.toResponse())
    }

    @PutMapping("/{id}/instructor")
    @PreAuthorize("hasAuthority('course:manage')")
    fun assignInstructor(
        @PathVariable id: UUID,
        @Valid @RequestBody request: AssignInstructorRequest,
    ): ResponseEntity<Any> =
        when (val result = assignInstructorService.assign(CourseEnrollmentId(id), request.instructorUserId)) {
            is AssignInstructorResult.Assigned -> ResponseEntity.ok(result.enrollment.toResponse())
            AssignInstructorResult.NotFound -> ResponseEntity.notFound().build()
            AssignInstructorResult.EnrollmentFinished ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(CourseEnrollmentErrorResponse("enrollment_finished"))
        }

    @PostMapping("/{id}/advance")
    @PreAuthorize("hasAuthority('course:manage')")
    fun advance(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                advanceEnrollmentStageService.advance(
                    CourseEnrollmentId(id),
                    actorUserId,
                    CorrelationContext.currentCorrelationId(),
                )
        ) {
            is AdvanceEnrollmentStageResult.Advanced -> ResponseEntity.ok(result.enrollment.toResponse())
            AdvanceEnrollmentStageResult.NotFound -> ResponseEntity.notFound().build()
            AdvanceEnrollmentStageResult.EnrollmentFinished ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(CourseEnrollmentErrorResponse("enrollment_finished"))
        }
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAuthority('course:manage')")
    fun withdraw(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result = withdrawEnrollmentService.withdraw(CourseEnrollmentId(id), actorUserId, CorrelationContext.currentCorrelationId())
        ) {
            is WithdrawEnrollmentResult.Withdrawn -> ResponseEntity.ok(result.enrollment.toResponse())
            WithdrawEnrollmentResult.NotFound -> ResponseEntity.notFound().build()
            WithdrawEnrollmentResult.EnrollmentFinished ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(CourseEnrollmentErrorResponse("enrollment_finished"))
        }
    }

    @GetMapping("/{id}/skill-evaluations")
    @PreAuthorize("hasAuthority('course:view')")
    fun listSkillEvaluations(
        @PathVariable id: UUID,
        @RequestParam(required = false, defaultValue = "0") @Min(0) page: Int,
        @RequestParam(required = false, defaultValue = "50") @Min(1) @Max(200) size: Int,
    ): List<SkillEvaluationResponse> =
        courseEnrollmentQueryService.listSkillEvaluations(CourseEnrollmentId(id), page, size).map {
            it.toResponse()
        }

    @PostMapping("/{id}/skill-evaluations")
    @PreAuthorize("hasAuthority('course:manage')")
    fun recordSkillEvaluation(
        @PathVariable id: UUID,
        @Valid @RequestBody request: RecordSkillEvaluationRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                recordSkillEvaluationService.record(
                    RecordSkillEvaluationCommand(
                        enrollmentId = CourseEnrollmentId(id),
                        skillName = request.skillName,
                        passed = request.passed,
                        evaluatedByUserId = actorUserId,
                        evaluatedOn = request.evaluatedOn,
                        notes = request.notes,
                    ),
                )
        ) {
            is RecordSkillEvaluationResult.Recorded -> ResponseEntity.status(HttpStatus.CREATED).body(result.evaluation.toResponse())
            RecordSkillEvaluationResult.EnrollmentNotFound -> ResponseEntity.notFound().build()
        }
    }
}

private fun CourseEnrollment.toResponse() =
    CourseEnrollmentResponse(
        id = id.value,
        diverId = diverId.value,
        offeringId = offeringId.value,
        instructorUserId = instructorUserId,
        stage = stage,
        startedAt = startedAt,
        certifiedAt = certifiedAt,
        withdrawnAt = withdrawnAt,
        createdAt = createdAt,
    )

private fun CourseSkillEvaluation.toResponse() =
    SkillEvaluationResponse(
        id = id,
        enrollmentId = enrollmentId.value,
        skillName = skillName,
        passed = passed,
        evaluatedByUserId = evaluatedByUserId,
        evaluatedOn = evaluatedOn,
        notes = notes,
        createdAt = createdAt,
    )
