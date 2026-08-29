package com.wego.divers.application

import com.wego.divers.domain.DiverId
import java.time.Instant
import java.util.UUID

interface DiverAuditRecorder {
    fun recordDiverCreated(
        diverId: DiverId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordDiverUpdated(
        diverId: DiverId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordDiverArchived(
        diverId: DiverId,
        actorUserId: UUID,
        occurredAt: Instant,
        correlationId: UUID?,
    )
}
