package com.wego.events

/**
 * Writes an [IntegrationEventEnvelope] to the durable outbox
 * (`wego.integration_outbox`). Implementations must participate in the
 * caller's own transaction — an event is only durable if it commits
 * atomically with the business state change it describes, never as a
 * separate commit that could succeed or fail independently.
 *
 * No dispatcher/relay exists yet to actually deliver a written row anywhere
 * (deferred since WEGO-000-B) — this only guarantees the event is recorded,
 * not that it has been published to any consumer.
 */
interface OutboxWriter {
    fun write(envelope: IntegrationEventEnvelope)
}
