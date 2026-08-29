package com.wego.divers.application

import com.wego.divers.domain.Offering
import com.wego.divers.domain.OfferingId
import com.wego.events.IntegrationEventEnvelope
import com.wego.events.OutboxWriter
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed class CloseOfferingResult {
    data class Closed(
        val offering: Offering,
    ) : CloseOfferingResult()

    data object NotFound : CloseOfferingResult()

    data object AlreadyClosed : CloseOfferingResult()
}

/**
 * Uses the same [OfferingRepository.findByIdForUpdate] row lock
 * [CreateBookingService] takes on the offering — this is what makes close
 * vs. concurrent booking creation well-defined: whichever transaction wins
 * the row lock commits first, and the other observes its outcome (a
 * pending create sees the offering already CLOSED; a pending close simply
 * closes after the booking commits, since closing does not depend on the
 * booking's own state). No new lock resource is introduced, so this does
 * not change the fixed lock order [BookingRepository.lockIdempotencyKey]
 * already documents.
 */
class CloseOfferingService(
    private val offeringRepository: OfferingRepository,
    private val offeringAuditRecorder: OfferingAuditRecorder,
    private val outboxWriter: OutboxWriter,
    private val transactionRunner: TransactionRunner,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    fun close(
        id: OfferingId,
        actorUserId: UUID,
        reason: String?,
        correlationId: UUID?,
    ): CloseOfferingResult =
        transactionRunner.runInTransaction {
            val offering =
                offeringRepository.findByIdForUpdate(id)
                    ?: return@runInTransaction CloseOfferingResult.NotFound

            if (!offering.isActive) {
                return@runInTransaction CloseOfferingResult.AlreadyClosed
            }

            val now = Instant.now(clock)
            offering.close(now)
            offeringRepository.save(offering)
            offeringAuditRecorder.recordOfferingClosed(offering.id, actorUserId, now, reason, correlationId)
            outboxWriter.write(closedEnvelope(offering, now, reason, correlationId))

            CloseOfferingResult.Closed(offering)
        }

    private fun closedEnvelope(
        offering: Offering,
        now: Instant,
        reason: String?,
        correlationId: UUID?,
    ): IntegrationEventEnvelope =
        IntegrationEventEnvelope(
            id = UUID.randomUUID(),
            aggregateType = "offering",
            aggregateId = offering.id.value.toString(),
            eventType = "offering.closed",
            eventVersion = 1,
            payloadJson =
                objectMapper.writeValueAsString(
                    mapOf(
                        "offeringId" to offering.id.value.toString(),
                        "reason" to reason,
                    ),
                ),
            occurredAt = now,
            correlationId = correlationId,
            causationId = null,
        )
}
