package com.wego.divers.application

import com.wego.divers.domain.BoatCharter
import com.wego.divers.domain.BoatCharterId
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface EndCharterResult {
    data class Ended(
        val charter: BoatCharter,
    ) : EndCharterResult

    data object NotFound : EndCharterResult

    data object AlreadyEnded : EndCharterResult
}

class EndCharterService(
    private val boatCharterRepository: BoatCharterRepository,
    private val boatCharterAuditRecorder: BoatCharterAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun end(
        charterId: BoatCharterId,
        actorUserId: UUID,
        correlationId: UUID?,
    ): EndCharterResult =
        transactionRunner.runInTransaction {
            val charter = boatCharterRepository.findByIdForUpdate(charterId) ?: return@runInTransaction EndCharterResult.NotFound
            if (!charter.isActive) return@runInTransaction EndCharterResult.AlreadyEnded

            val now = Instant.now(clock)
            charter.end(now)
            boatCharterRepository.save(charter)
            boatCharterAuditRecorder.recordCharterEnded(charter.id, actorUserId, now, correlationId)
            EndCharterResult.Ended(charter)
        }
}
