package com.wego.divers.application

import com.wego.divers.domain.BoatCharter
import com.wego.divers.domain.BoatCharterId
import com.wego.divers.domain.CharterType
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateBoatCharterCommand(
    val boatName: String,
    val charterType: CharterType,
    val licensedCapacity: Int,
    val startsOn: LocalDate,
    val endsOn: LocalDate?,
    val notes: String?,
    val createdByUserId: UUID?,
    val correlationId: UUID?,
)

class CreateBoatCharterService(
    private val boatCharterRepository: BoatCharterRepository,
    private val boatCharterAuditRecorder: BoatCharterAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun create(command: CreateBoatCharterCommand): BoatCharter =
        transactionRunner.runInTransaction {
            val now = Instant.now(clock)
            val charter =
                BoatCharter.create(
                    id = BoatCharterId.generate(),
                    boatName = command.boatName,
                    charterType = command.charterType,
                    licensedCapacity = command.licensedCapacity,
                    startsOn = command.startsOn,
                    endsOn = command.endsOn,
                    notes = command.notes,
                    createdByUserId = command.createdByUserId,
                    now = now,
                )
            boatCharterRepository.save(charter)
            boatCharterAuditRecorder.recordCharterCreated(charter.id, command.createdByUserId, now, command.correlationId)
            charter
        }
}
