package com.wego.divers.application

import com.wego.divers.domain.BoatCharterId
import java.time.Instant
import java.util.UUID

interface BoatCharterAuditRecorder {
    fun recordCharterCreated(
        charterId: BoatCharterId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordCharterEnded(
        charterId: BoatCharterId,
        actorUserId: UUID,
        occurredAt: Instant,
        correlationId: UUID?,
    )
}
