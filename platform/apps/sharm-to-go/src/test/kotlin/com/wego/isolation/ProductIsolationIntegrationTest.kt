package com.wego.isolation

import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * The database half of WEGO-010-A Packet 0R's proof (see
 * `com.wego.security.SecurityConfigurationTest` for the route half). This
 * application's Flyway location (`src/main/resources/db/migration`)
 * physically contains V1 (platform foundation), V2 (identity foundation,
 * both copied by hand from `:platform:application` rather than shared — an
 * accepted duplication risk recorded on the WEGO-010-A board entry), and
 * this module's own V3 (Packet 1A travel marketplace catalog) — the Divers
 * product's V3-V8 files (a disjoint numbering sequence in a different
 * application) do not exist under this module at all. This test proves the
 * *database* consequence of that: a real, freshly migrated Sharm To Go
 * database contains the travel marketplace catalog tables and zero Divers
 * tables.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class ProductIsolationIntegrationTest(
    @Autowired private val flyway: Flyway,
) {
    @Test
    fun `boots and migrates only the shared platform, identity, and travel marketplace catalog foundation, never any Divers table`() {
        assertThat(flyway.info().applied().map { it.version.toString() }).containsExactly("1", "2", "3")

        postgres.createConnection("").use { connection ->
            val tableNames =
                connection.metaData.getTables(null, "wego", "%", arrayOf("TABLE")).use { rs ->
                    generateSequence {
                        if (rs.next()) rs.getString("TABLE_NAME") else null
                    }.toList()
                }

            assertThat(tableNames)
                .contains(
                    "identity_user",
                    "identity_role",
                    "identity_session",
                    "integration_outbox",
                    "travel_provider",
                    "travel_category",
                    "travel_service",
                    "travel_service_option",
                    "travel_service_media",
                    "travel_marketplace_audit_event",
                ).noneMatch { it.startsWith("divers_") }
        }
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("wego_sharm_to_go_test")
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
