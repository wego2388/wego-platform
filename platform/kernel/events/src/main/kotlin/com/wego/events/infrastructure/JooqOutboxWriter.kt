package com.wego.events.infrastructure

import com.wego.events.IntegrationEventEnvelope
import com.wego.events.OutboxWriter
import com.wego.generated.jooq.tables.IntegrationOutbox.INTEGRATION_OUTBOX
import org.jooq.DSLContext
import org.jooq.JSON
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqOutboxWriter(
    private val dsl: DSLContext,
) : OutboxWriter {
    // No explicit TransactionRunner wrapping here, unlike identity's
    // services — this is a single insert, and @Transactional (default
    // REQUIRED propagation) is enough for it to join whichever transaction
    // the caller already started, exactly like JooqUserRepository's own
    // write methods do.
    @Transactional
    override fun write(envelope: IntegrationEventEnvelope) {
        dsl
            .insertInto(INTEGRATION_OUTBOX)
            .set(INTEGRATION_OUTBOX.ID, envelope.id)
            .set(INTEGRATION_OUTBOX.AGGREGATE_TYPE, envelope.aggregateType)
            .set(INTEGRATION_OUTBOX.AGGREGATE_ID, envelope.aggregateId)
            .set(INTEGRATION_OUTBOX.EVENT_TYPE, envelope.eventType)
            .set(INTEGRATION_OUTBOX.EVENT_VERSION, envelope.eventVersion)
            .set(INTEGRATION_OUTBOX.PAYLOAD, JSON.valueOf(envelope.payloadJson))
            .set(INTEGRATION_OUTBOX.OCCURRED_AT, toOffset(envelope.occurredAt))
            // Immediately eligible for delivery — no scheduling/delay
            // semantics exist yet, so "available" is just "occurred".
            .set(INTEGRATION_OUTBOX.AVAILABLE_AT, toOffset(envelope.occurredAt))
            .set(INTEGRATION_OUTBOX.CORRELATION_ID, envelope.correlationId)
            .set(INTEGRATION_OUTBOX.CAUSATION_ID, envelope.causationId)
            .execute()
    }

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
