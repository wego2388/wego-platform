package com.wego.events

import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class IntegrationEventEnvelopeTest {
    @Test
    fun `event versions must be positive`() {
        assertThatIllegalArgumentException().isThrownBy {
            IntegrationEventEnvelope(
                id = UUID.randomUUID(),
                aggregateType = "booking",
                aggregateId = "B-1",
                eventType = "booking.confirmed",
                eventVersion = 0,
                payloadJson = "{}",
                occurredAt = Instant.parse("2026-08-08T00:00:00Z"),
                correlationId = null,
                causationId = null,
            )
        }
    }
}
