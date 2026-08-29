package com.wego.events

import com.wego.generated.jooq.tables.IntegrationOutbox.INTEGRATION_OUTBOX
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.jooq.JSON
import org.jooq.SQLDialect
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class OutboxMigrationIntegrationTest(
    @Autowired private val flyway: Flyway,
) {
    @Test
    fun `boot auto migrates postgres and generated jooq types honor constraints`() {
        assertThat(flyway.info().applied().map { it.version.toString() }).containsExactly("1", "2", "3", "4", "5", "6")

        postgres.createConnection("").use { connection ->
            val dsl = DSL.using(connection, SQLDialect.POSTGRES)
            val now = OffsetDateTime.of(2026, 8, 8, 0, 0, 0, 0, ZoneOffset.UTC)

            val inserted =
                dsl
                    .insertInto(INTEGRATION_OUTBOX)
                    .set(INTEGRATION_OUTBOX.ID, UUID.randomUUID())
                    .set(INTEGRATION_OUTBOX.AGGREGATE_TYPE, "booking")
                    .set(INTEGRATION_OUTBOX.AGGREGATE_ID, "B-1")
                    .set(INTEGRATION_OUTBOX.EVENT_TYPE, "booking.confirmed")
                    .set(INTEGRATION_OUTBOX.EVENT_VERSION, 1)
                    .set(INTEGRATION_OUTBOX.PAYLOAD, JSON.valueOf("{}"))
                    .set(INTEGRATION_OUTBOX.OCCURRED_AT, now)
                    .set(INTEGRATION_OUTBOX.AVAILABLE_AT, now)
                    .execute()

            assertThat(inserted).isEqualTo(1)

            assertThatThrownBy {
                dsl
                    .insertInto(INTEGRATION_OUTBOX)
                    .set(INTEGRATION_OUTBOX.ID, UUID.randomUUID())
                    .set(INTEGRATION_OUTBOX.AGGREGATE_TYPE, "booking")
                    .set(INTEGRATION_OUTBOX.AGGREGATE_ID, "B-2")
                    .set(INTEGRATION_OUTBOX.EVENT_TYPE, "booking.confirmed")
                    .set(INTEGRATION_OUTBOX.EVENT_VERSION, 0)
                    .set(INTEGRATION_OUTBOX.PAYLOAD, JSON.valueOf("{}"))
                    .set(INTEGRATION_OUTBOX.OCCURRED_AT, now)
                    .set(INTEGRATION_OUTBOX.AVAILABLE_AT, now)
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("integration_outbox_event_version_positive")
        }
    }

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
