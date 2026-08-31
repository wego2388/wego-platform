package com.wego.divers.application
import com.wego.divers.domain.Diver
import com.wego.divers.domain.DiverCertification
import com.wego.divers.domain.DiverId
import com.wego.transaction.TransactionRunner
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class UpdateDiverCommand(
    val diverId: DiverId,
    val fullName: String,
    val nationality: String?,
    val primaryLanguage: String?,
    val email: String?,
    val phone: String?,
    val emergencyContactName: String?,
    val emergencyContactPhone: String?,
    val medicalNotes: String?,
    val totalLoggedDives: Int,
    val maxDepthMeters: BigDecimal?,
    val lastDiveOn: LocalDate?,
    val bcdSize: String?,
    val finSize: String?,
    val wetsuitSize: String?,
    val certifications: List<DiverCertification>,
    val actorUserId: UUID?,
    val correlationId: UUID?,
)

sealed interface UpdateDiverResult {
    data class Updated(
        val diver: Diver,
    ) : UpdateDiverResult

    data object NotFound : UpdateDiverResult

    data object Archived : UpdateDiverResult
}

class UpdateDiverService(
    private val diverRepository: DiverRepository,
    private val diverAuditRecorder: DiverAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun update(command: UpdateDiverCommand): UpdateDiverResult =
        transactionRunner.runInTransaction {
            val existing = diverRepository.findByIdForUpdate(command.diverId) ?: return@runInTransaction UpdateDiverResult.NotFound
            if (!existing.isActive) return@runInTransaction UpdateDiverResult.Archived

            val updated =
                existing.withUpdatedProfile(
                    fullName = command.fullName,
                    nationality = command.nationality,
                    primaryLanguage = command.primaryLanguage,
                    email = command.email,
                    phone = command.phone,
                    emergencyContactName = command.emergencyContactName,
                    emergencyContactPhone = command.emergencyContactPhone,
                    medicalNotes = command.medicalNotes,
                    totalLoggedDives = command.totalLoggedDives,
                    maxDepthMeters = command.maxDepthMeters,
                    lastDiveOn = command.lastDiveOn,
                    bcdSize = command.bcdSize,
                    finSize = command.finSize,
                    wetsuitSize = command.wetsuitSize,
                    certifications = command.certifications,
                )
            diverRepository.save(updated)
            val now = Instant.now(clock)
            diverAuditRecorder.recordDiverUpdated(updated.id, command.actorUserId, now, command.correlationId)
            UpdateDiverResult.Updated(updated)
        }
}
