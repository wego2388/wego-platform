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

data class CreateDiverCommand(
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
    val createdByUserId: UUID?,
    val correlationId: UUID?,
)

class CreateDiverService(
    private val diverRepository: DiverRepository,
    private val diverAuditRecorder: DiverAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun create(command: CreateDiverCommand): Diver =
        transactionRunner.runInTransaction {
            val now = Instant.now(clock)
            val diver =
                Diver.create(
                    id = DiverId.generate(),
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
                    createdByUserId = command.createdByUserId,
                    now = now,
                )
            diverRepository.save(diver)
            diverAuditRecorder.recordDiverCreated(diver.id, command.createdByUserId, now, command.correlationId)
            diver
        }
}
