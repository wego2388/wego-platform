package com.wego.divers.application

import com.wego.divers.domain.OfferingId
import java.time.Instant
import java.util.UUID

interface OfferingAuditRecorder {
    fun recordOfferingCreated(
        offeringId: OfferingId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    )

    fun recordOfferingClosed(
        offeringId: OfferingId,
        actorUserId: UUID,
        occurredAt: Instant,
        reason: String?,
        correlationId: UUID?,
    )
}
