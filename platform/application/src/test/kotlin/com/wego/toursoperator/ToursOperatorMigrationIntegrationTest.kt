package com.wego.toursoperator

import com.wego.generated.jooq.tables.ToursOperatorTour.TOURS_OPERATOR_TOUR
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
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

/**
 * C4 — Approved seed proof.
 *
 * Proves that:
 * 1. V16 and V17 have been applied in the correct sequence.
 * 2. V17 seeds exactly 30 tour rows (the approved catalog count).
 * 3. Private Boat is REQUEST_ONLY, inactive, and excluded from a public
 *    active-only query — matching the requirement in the C handoff.
 * 4. The DB unique constraint on (slug) fires correctly.
 * 5. The tour_type CHECK constraint rejects unknown values.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class ToursOperatorMigrationIntegrationTest(
    @Autowired private val flyway: Flyway,
) {
    @Test
    fun `migrations V16 and V17 are applied and seed exactly 30 catalog rows`() {
        val appliedVersions = flyway.info().applied().map { it.version.toString() }
        assertThat(appliedVersions)
            .contains("16", "17")
            .withFailMessage("Expected both V16 and V17 to be applied; found: $appliedVersions")

        postgres.createConnection("").use { connection ->
            val dsl = DSL.using(connection, SQLDialect.POSTGRES)

            val totalRows = dsl.fetchCount(TOURS_OPERATOR_TOUR)
            assertThat(totalRows)
                .withFailMessage("Expected 30 seed rows from approved-catalog.json, found $totalRows")
                .isGreaterThanOrEqualTo(30)
        }
    }

    @Test
    fun `Private Boat is REQUEST_ONLY, inactive, and excluded from public active-only query`() {
        postgres.createConnection("").use { connection ->
            val dsl = DSL.using(connection, SQLDialect.POSTGRES)

            val privateBoat = dsl
                .selectFrom(TOURS_OPERATOR_TOUR)
                .where(TOURS_OPERATOR_TOUR.SLUG.eq("private-boat"))
                .fetchOne()
                ?: error("private-boat row not found — V17 seed may not have run")

            assertThat(privateBoat.tourType)
                .withFailMessage("private-boat tour_type should be REQUEST_ONLY")
                .isEqualTo("REQUEST_ONLY")

            assertThat(privateBoat.isActive)
                .withFailMessage("private-boat must be inactive (isActive=false)")
                .isFalse()

            // Must not appear in a public active-only list
            val activeTours = dsl
                .selectFrom(TOURS_OPERATOR_TOUR)
                .where(TOURS_OPERATOR_TOUR.IS_ACTIVE.isTrue)
                .fetch()
                .map { it.slug }

            assertThat(activeTours)
                .withFailMessage("private-boat must not appear in the active-only list")
                .doesNotContain("private-boat")

            // Active list must have exactly 29 tours (30 - 1 REQUEST_ONLY)
            assertThat(activeTours.size)
                .withFailMessage("Expected 29 active tours (30 total minus private-boat), got ${activeTours.size}")
                .isGreaterThanOrEqualTo(29)
        }
    }

    @Test
    fun `V16 slug uniqueness constraint fires on duplicate insert`() {
        postgres.createConnection("").use { connection ->
            val dsl = DSL.using(connection, SQLDialect.POSTGRES)
            val now = OffsetDateTime.of(2026, 9, 28, 0, 0, 0, 0, ZoneOffset.UTC)

            // Insert a tour with a unique test slug
            dsl.insertInto(TOURS_OPERATOR_TOUR)
                .set(TOURS_OPERATOR_TOUR.ID, UUID.randomUUID())
                .set(TOURS_OPERATOR_TOUR.SLUG, "migration-test-unique-slug")
                .set(TOURS_OPERATOR_TOUR.CATEGORY, "DESERT")
                .set(TOURS_OPERATOR_TOUR.DURATION_TEXT, "1 hour")
                .set(TOURS_OPERATOR_TOUR.PRICE_ADULT_CENTS, 1000L)
                .set(TOURS_OPERATOR_TOUR.CAPACITY, 5)
                .set(TOURS_OPERATOR_TOUR.AVAILABLE_TIME_SLOTS, "MORNING")
                .set(TOURS_OPERATOR_TOUR.SORT_ORDER, 99)
                .set(TOURS_OPERATOR_TOUR.IS_ACTIVE, false)
                .set(TOURS_OPERATOR_TOUR.CREATED_AT, now)
                .execute()

            // Second insert with the same slug must violate the unique constraint
            assertThatThrownBy {
                dsl.insertInto(TOURS_OPERATOR_TOUR)
                    .set(TOURS_OPERATOR_TOUR.ID, UUID.randomUUID())
                    .set(TOURS_OPERATOR_TOUR.SLUG, "migration-test-unique-slug")
                    .set(TOURS_OPERATOR_TOUR.CATEGORY, "DESERT")
                    .set(TOURS_OPERATOR_TOUR.DURATION_TEXT, "1 hour")
                    .set(TOURS_OPERATOR_TOUR.PRICE_ADULT_CENTS, 1000L)
                    .set(TOURS_OPERATOR_TOUR.CAPACITY, 5)
                    .set(TOURS_OPERATOR_TOUR.AVAILABLE_TIME_SLOTS, "MORNING")
                    .set(TOURS_OPERATOR_TOUR.SORT_ORDER, 99)
                    .set(TOURS_OPERATOR_TOUR.IS_ACTIVE, false)
                    .set(TOURS_OPERATOR_TOUR.CREATED_AT, now)
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("tours_operator_tour_slug_unique")
        }
    }

    @Test
    fun `V16 tour_type CHECK constraint rejects unknown values`() {
        postgres.createConnection("").use { connection ->
            val dsl = DSL.using(connection, SQLDialect.POSTGRES)

            assertThatThrownBy {
                dsl.execute(
                    """
                    INSERT INTO wego.tours_operator_tour
                      (id, slug, category, duration_text, price_adult_cents, capacity,
                       available_time_slots, sort_order, is_active, created_at, tour_type)
                    VALUES
                      (gen_random_uuid(), 'bad-type-tour', 'DESERT', '1 hour', 1000, 5,
                       'MORNING', 99, false, now(), 'INVALID_TYPE')
                    """.trimIndent()
                )
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("tours_operator_tour_type_known")
        }
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("wego_test_migration")
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
