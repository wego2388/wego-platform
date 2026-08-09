package com.wego.events

import java.time.Instant
import java.util.UUID

data class IntegrationEventEnvelope(
    val id: UUID,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val eventVersion: Int,
    val payloadJson: String,
    val occurredAt: Instant,
    val correlationId: UUID?,
    val causationId: UUID?,
) {
    init {
        require(aggregateType.isNotBlank()) { "Aggregate type must not be blank" }
        require(aggregateId.isNotBlank()) { "Aggregate id must not be blank" }
        require(eventType.isNotBlank()) { "Event type must not be blank" }
        require(eventVersion > 0) { "Event version must be positive" }
        require(payloadJson.isNotBlank()) { "Event payload must not be blank" }
    }
}
