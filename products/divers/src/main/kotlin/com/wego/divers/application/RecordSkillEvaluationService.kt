package com.wego.divers.application
import com.wego.divers.domain.CourseEnrollmentId
import com.wego.divers.domain.CourseSkillEvaluation
import com.wego.transaction.TransactionRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class RecordSkillEvaluationCommand(
    val enrollmentId: CourseEnrollmentId,
    val skillName: String,
    val passed: Boolean,
    val evaluatedByUserId: UUID?,
    val evaluatedOn: LocalDate,
    val notes: String?,
)

sealed interface RecordSkillEvaluationResult {
    data class Recorded(
        val evaluation: CourseSkillEvaluation,
    ) : RecordSkillEvaluationResult

    data object EnrollmentNotFound : RecordSkillEvaluationResult
}

class RecordSkillEvaluationService(
    private val enrollmentRepository: CourseEnrollmentRepository,
    private val skillEvaluationRepository: CourseSkillEvaluationRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun record(command: RecordSkillEvaluationCommand): RecordSkillEvaluationResult =
        transactionRunner.runInTransaction {
            if (enrollmentRepository.findById(command.enrollmentId) == null) {
                return@runInTransaction RecordSkillEvaluationResult.EnrollmentNotFound
            }

            val evaluation =
                CourseSkillEvaluation(
                    id = UUID.randomUUID(),
                    enrollmentId = command.enrollmentId,
                    skillName = command.skillName,
                    passed = command.passed,
                    evaluatedByUserId = command.evaluatedByUserId,
                    evaluatedOn = command.evaluatedOn,
                    notes = command.notes,
                    createdAt = Instant.now(clock),
                )
            skillEvaluationRepository.save(evaluation)
            RecordSkillEvaluationResult.Recorded(evaluation)
        }
}
