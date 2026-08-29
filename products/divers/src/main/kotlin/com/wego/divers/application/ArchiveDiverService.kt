package com.wego.divers.application

import com.wego.divers.domain.Diver
import com.wego.divers.domain.DiverId
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface ArchiveDiverResult {
    data class Archived(
        val diver: Diver,
    ) : ArchiveDiverResult

    data object NotFound : ArchiveDiverResult

    data object AlreadyArchived : ArchiveDiverResult
}

class ArchiveDiverService(
    private val diverRepository: DiverRepository,
    private val diverAuditRecorder: DiverAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun archive(
        diverId: DiverId,
        actorUserId: UUID,
        correlationId: UUID?,
    ): ArchiveDiverResult =
        transactionRunner.runInTransaction {
            val diver = diverRepository.findByIdForUpdate(diverId) ?: return@runInTransaction ArchiveDiverResult.NotFound
            if (!diver.isActive) return@runInTransaction ArchiveDiverResult.AlreadyArchived

            val now = Instant.now(clock)
            diver.archive(now)
            diverRepository.save(diver)
            diverAuditRecorder.recordDiverArchived(diver.id, actorUserId, now, correlationId)
            ArchiveDiverResult.Archived(diver)
        }
}
