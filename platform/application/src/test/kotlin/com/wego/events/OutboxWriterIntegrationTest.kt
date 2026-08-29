package com.wego.events

import com.wego.generated.jooq.enums.OutboxStatus
import com.wego.generated.jooq.tables.IntegrationOutbox.INTEGRATION_OUTBOX
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jooq.DSLContext
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

/**
 * Proves [JooqOutboxWriter][com.wego.events.infrastructure.JooqOutboxWriter]
 * both writes a real, complete row and genuinely joins the caller's own
 * transaction rather than committing separately: a write followed by a
 * forced rollback of the *surrounding* transaction must leave zero rows,
 * not one. An implementation that opened its own implicit transaction
 * (e.g. via `@Transactional(propagation = REQUIRES_NEW)`) would pass a
 * naive "does it insert a row" test while failing this one.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class OutboxWriterIntegrationTest {
    @Autowired
    private lateinit var outboxWriter: OutboxWriter

    @Autowired
    private lateinit var transactionManager: PlatformTransactionManager

    @Autowired
    private lateinit var dsl: DSLContext

    @Test
    fun `a committed transaction persists the written envelope with every field intact`() {
        val envelope = sampleEnvelope()
        val transactionTemplate = TransactionTemplate(transactionManager)

        transactionTemplate.execute { outboxWriter.write(envelope) }

        val record = dsl.selectFrom(INTEGRATION_OUTBOX).where(INTEGRATION_OUTBOX.ID.eq(envelope.id)).fetchOne()
        assertThat(record).isNotNull
        assertThat(record!!.aggregateType).isEqualTo(envelope.aggregateType)
        assertThat(record.aggregateId).isEqualTo(envelope.aggregateId)
        assertThat(record.eventType).isEqualTo(envelope.eventType)
        assertThat(record.eventVersion).isEqualTo(envelope.eventVersion)
        // Postgres's jsonb column canonicalizes formatting on round-trip
        // (e.g. adds a space after ':') — compare parsed structure, not
        // the raw text, which isn't guaranteed to be byte-identical.
        val objectMapper = ObjectMapper()
        assertThat(objectMapper.readTree(record.payload.data()) as Any)
            .isEqualTo(objectMapper.readTree(envelope.payloadJson) as Any)
        assertThat(record.correlationId).isEqualTo(envelope.correlationId)
        assertThat(record.causationId).isEqualTo(envelope.causationId)
        assertThat(record.status).isEqualTo(OutboxStatus.PENDING)
    }

    @Test
    fun `a rollback of the surrounding transaction leaves no row behind`() {
        val envelope = sampleEnvelope()
        val transactionTemplate = TransactionTemplate(transactionManager)

        assertThatThrownBy {
            transactionTemplate.execute {
                outboxWriter.write(envelope)
                throw RuntimeException("Simulated failure after the outbox write")
            }
        }.hasMessageContaining("Simulated failure after the outbox write")

        val record = dsl.selectFrom(INTEGRATION_OUTBOX).where(INTEGRATION_OUTBOX.ID.eq(envelope.id)).fetchOne()
        assertThat(record)
            .describedAs("the write must not have committed independently of the transaction that rolled back")
            .isNull()
    }

    private fun sampleEnvelope() =
        IntegrationEventEnvelope(
            id = UUID.randomUUID(),
            aggregateType = "booking",
            aggregateId = UUID.randomUUID().toString(),
            eventType = "booking.created",
            eventVersion = 1,
            payloadJson = """{"offeringId":"o-1"}""",
            occurredAt = Instant.parse("2026-08-10T00:00:00Z"),
            correlationId = UUID.randomUUID(),
            causationId = null,
        )

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("wego_test")
                .withUsername("wego_test")
                .withPassword("wego_test")

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.enabled") { true }
        }
    }
}
