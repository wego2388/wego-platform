package com.wego.toursoperator

import com.wego.generated.jooq.tables.ToursOperatorTour.TOURS_OPERATOR_TOUR
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeEach
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
    @BeforeEach
    fun removeRowsCreatedByThisTestClass() {
        postgres.createConnection("").use { connection ->
            DSL
                .using(connection, SQLDialect.POSTGRES)
                .deleteFrom(TOURS_OPERATOR_TOUR)
                .where(TOURS_OPERATOR_TOUR.SLUG.like("migration-test-%"))
                .execute()
        }
    }

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
                .isEqualTo(30)
        }
    }

    @Test
    fun `Private Boat is REQUEST_ONLY, inactive, and excluded from public active-only query`() {
        postgres.createConnection("").use { connection ->
            val dsl = DSL.using(connection, SQLDialect.POSTGRES)

            val privateBoat =
                dsl
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
            val activeTours =
                dsl
                    .selectFrom(TOURS_OPERATOR_TOUR)
                    .where(TOURS_OPERATOR_TOUR.IS_ACTIVE.isTrue)
                    .fetch()
                    .map { it.slug }

            assertThat(activeTours)
                .withFailMessage("private-boat must not appear in the active-only list")
                .doesNotContain("private-boat")

            // 30 seeded − private-boat (V17) − intro-diving and crocodile-show
            // (owner revision applied by V25) = 27 active tours.
            assertThat(activeTours.size)
                .withFailMessage("Expected 27 active tours after the V25 owner revision, got ${activeTours.size}")
                .isEqualTo(27)
        }
    }

    @Test
    fun `V16 slug uniqueness constraint fires on duplicate insert`() {
        postgres.createConnection("").use { connection ->
            val dsl = DSL.using(connection, SQLDialect.POSTGRES)
            val now = OffsetDateTime.of(2026, 9, 28, 0, 0, 0, 0, ZoneOffset.UTC)

            // Insert a tour with a unique test slug
            dsl
                .insertInto(TOURS_OPERATOR_TOUR)
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
                dsl
                    .insertInto(TOURS_OPERATOR_TOUR)
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
                    """.trimIndent(),
                )
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("tours_operator_tour_type_known")
        }
    }

    @Test
    fun `V19 upgrades V18 data with duplicate provider references without losing evidence`() {
        val upgradePostgres =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("wego_v18_upgrade")
                .withUsername("wego_upgrade")
                .withPassword("wego_upgrade")
        upgradePostgres.start()
        try {
            val migrationLocations = arrayOf("classpath:db/migration", "classpath:db/migration/data")
            Flyway
                .configure()
                .dataSource(upgradePostgres.jdbcUrl, upgradePostgres.username, upgradePostgres.password)
                .locations(*migrationLocations)
                .target("18")
                .load()
                .migrate()

            upgradePostgres.createConnection("").use { connection ->
                val dsl = DSL.using(connection, SQLDialect.POSTGRES)
                val tourId =
                    dsl.fetchValue(
                        "SELECT id FROM wego.tours_operator_tour ORDER BY sort_order LIMIT 1",
                        UUID::class.java,
                    ) ?: error("V17 seeded tour missing")
                val slotIds = listOf(UUID.randomUUID(), UUID.randomUUID())
                val bookingIds = listOf(UUID.randomUUID(), UUID.randomUUID())

                slotIds.forEachIndexed { index, slotId ->
                    dsl.execute(
                        """
                        INSERT INTO wego.tours_operator_tour_slot
                          (id, tour_id, date, time_slot, capacity, booked_count, is_blocked, created_at)
                        VALUES (?, ?, DATE '2027-01-10' + ?, 'MORNING', 10, 1, false, now())
                        """.trimIndent(),
                        slotId,
                        tourId,
                        index,
                    )
                    dsl.execute(
                        """
                        INSERT INTO wego.tours_operator_booking
                          (id, reference, tour_id, slot_id, tour_date, time_slot,
                           adults_count, children_count, price_adult_eur, price_child_eur, total_eur,
                           customer_full_name, customer_phone, customer_nationality,
                           hotel_name, locale, status, created_at)
                        VALUES (?, ?, ?, ?, DATE '2027-01-10' + ?, 'MORNING',
                                1, 0, 10.00, NULL, 10.00,
                                'Migration Test', '+20100000000', 'EG',
                                'Migration Hotel', 'en', 'NEW', now())
                        """.trimIndent(),
                        bookingIds[index],
                        "STR-2027-${index + 1}",
                        tourId,
                        slotId,
                        index,
                    )
                    dsl.execute(
                        """
                        INSERT INTO wego.tours_operator_payment
                          (id, booking_id, amount_eur, amount_minor_units, currency_code,
                           paymob_order_id, paymob_transaction_id, status, created_at)
                        VALUES (?, ?, 10.00, 1000, 'EUR', 'DUPLICATE-ORDER', 'DUPLICATE-TXN', 'PENDING', now())
                        """.trimIndent(),
                        UUID.randomUUID(),
                        bookingIds[index],
                    )
                }
            }

            val upgraded =
                Flyway
                    .configure()
                    .dataSource(upgradePostgres.jdbcUrl, upgradePostgres.username, upgradePostgres.password)
                    .locations(*migrationLocations)
                    .load()
            upgraded.migrate()
            assertThat(upgraded.info().applied().map { it.version.toString() }).contains("19")

            upgradePostgres.createConnection("").use { connection ->
                val dsl = DSL.using(connection, SQLDialect.POSTGRES)
                val summary =
                    dsl.fetchOne(
                        """
                        SELECT count(*) AS total,
                               count(DISTINCT provider_reference) AS unique_references,
                               count(*) FILTER (WHERE paymob_order_id IS NULL) AS cleared_orders,
                               count(*) FILTER (WHERE paymob_transaction_id IS NULL) AS cleared_transactions
                        FROM wego.tours_operator_payment
                        """.trimIndent(),
                    ) ?: error("Payment migration summary missing")
                assertThat(summary.get(0, Int::class.java)).isEqualTo(2)
                assertThat(summary.get(1, Int::class.java)).isEqualTo(2)
                assertThat(summary.get(2, Int::class.java)).isEqualTo(2)
                assertThat(summary.get(3, Int::class.java)).isEqualTo(2)
                assertThat(
                    dsl
                        .fetchOne("SELECT count(*) FROM wego.tours_operator_payment_reference_quarantine")
                        ?.get(0, Int::class.java),
                ).isEqualTo(4)
                assertThat(
                    dsl
                        .fetchOne(
                            """
                            SELECT count(*) FROM pg_indexes
                            WHERE schemaname = 'wego'
                              AND indexname IN (
                                'tours_operator_payment_paymob_order_unique',
                                'tours_operator_payment_paymob_transaction_unique'
                              )
                            """.trimIndent(),
                        )?.get(0, Int::class.java),
                ).isEqualTo(2)
            }
        } finally {
            upgradePostgres.stop()
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

    @Test
    fun `V20 and V21 upgrade existing V19 payments with recognition and backfilled history`() {
        val upgradePostgres =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("wego_v19_upgrade")
                .withUsername("wego_upgrade")
                .withPassword("wego_upgrade")
        upgradePostgres.start()
        try {
            val migrationLocations = arrayOf("classpath:db/migration", "classpath:db/migration/data")
            Flyway
                .configure()
                .dataSource(upgradePostgres.jdbcUrl, upgradePostgres.username, upgradePostgres.password)
                .locations(*migrationLocations)
                .target("19")
                .load()
                .migrate()

            // status -> (paid_at offset, refunded_at offset) in minutes after creation
            val seeded =
                listOf(
                    "PENDING" to Pair(null, null),
                    "PAID" to Pair(5, null),
                    "REFUNDED" to Pair(5, 60),
                    "RECONCILIATION_REQUIRED" to Pair(null, null),
                )
            upgradePostgres.createConnection("").use { connection ->
                val dsl = DSL.using(connection, SQLDialect.POSTGRES)
                val tourId =
                    dsl.fetchValue(
                        "SELECT id FROM wego.tours_operator_tour ORDER BY sort_order LIMIT 1",
                        UUID::class.java,
                    ) ?: error("V17 seeded tour missing")
                seeded.forEachIndexed { index, (status, offsets) ->
                    val slotId = UUID.randomUUID()
                    val bookingId = UUID.randomUUID()
                    val paymentId = UUID.randomUUID()
                    dsl.execute(
                        """
                        INSERT INTO wego.tours_operator_tour_slot
                          (id, tour_id, date, time_slot, capacity, booked_count, is_blocked, created_at)
                        VALUES (?, ?, DATE '2027-02-10' + ?, 'MORNING', 10, 1, false, now())
                        """.trimIndent(),
                        slotId,
                        tourId,
                        index,
                    )
                    dsl.execute(
                        """
                        INSERT INTO wego.tours_operator_booking
                          (id, reference, tour_id, slot_id, tour_date, time_slot,
                           adults_count, children_count, price_adult_eur, price_child_eur, total_eur,
                           customer_full_name, customer_phone, customer_nationality,
                           hotel_name, locale, status, created_at)
                        VALUES (?, ?, ?, ?, DATE '2027-02-10' + ?, 'MORNING',
                                1, 0, 10.00, NULL, 10.00,
                                'Migration Test', '+20100000000', 'EG',
                                'Migration Hotel', 'en', 'NEW', now())
                        """.trimIndent(),
                        bookingId,
                        "STR-2027-2${index + 1}",
                        tourId,
                        slotId,
                        index,
                    )
                    dsl.execute(
                        """
                        INSERT INTO wego.tours_operator_payment
                          (id, booking_id, amount_eur, amount_minor_units, currency_code, provider_reference,
                           status, provider_status, created_at, paid_at, refunded_at)
                        VALUES (?, ?, 10.00, 1000, 'EUR', ?, ?, ?, TIMESTAMPTZ '2027-01-01 10:00:00+00',
                                TIMESTAMPTZ '2027-01-01 10:00:00+00' + make_interval(mins => ?),
                                TIMESTAMPTZ '2027-01-01 10:00:00+00' + make_interval(mins => ?))
                        """.trimIndent(),
                        paymentId,
                        bookingId,
                        "sts-$paymentId",
                        status,
                        if (status == "PENDING") null else "PRE_V20",
                        offsets.first,
                        offsets.second,
                    )
                }
            }

            val upgraded =
                Flyway
                    .configure()
                    .dataSource(upgradePostgres.jdbcUrl, upgradePostgres.username, upgradePostgres.password)
                    .locations(*migrationLocations)
                    .load()
            upgraded.migrate()
            assertThat(upgraded.info().applied().map { it.version.toString() }).contains("20", "21")

            upgradePostgres.createConnection("").use { connection ->
                val dsl = DSL.using(connection, SQLDialect.POSTGRES)
                val recognised =
                    dsl
                        .fetch("SELECT status FROM wego.tours_operator_payment WHERE revenue_recognised_at IS NOT NULL")
                        .map { it.get(0, String::class.java) }
                // Pre-V20 REFUNDED rows are ambiguous and stay unrecognised by design.
                assertThat(recognised).containsExactly("PAID")

                val history =
                    dsl
                        .fetch(
                            """
                            SELECT p.status, e.from_status, e.to_status, e.source, e.occurred_at, e.seq
                            FROM wego.tours_operator_payment_audit_event e
                            JOIN wego.tours_operator_payment p ON p.id = e.payment_id
                            ORDER BY p.status, e.occurred_at, e.seq
                            """.trimIndent(),
                        ).map { record ->
                            listOf<String?>(
                                record.get(0, String::class.java),
                                record.get(1, String::class.java),
                                record.get(2, String::class.java),
                                record.get(3, String::class.java),
                            )
                        }
                assertThat(history).containsExactly(
                    listOf("PAID", null, "PENDING", "BACKFILL"),
                    listOf("PAID", null, "PAID", "BACKFILL"),
                    listOf("PENDING", null, "PENDING", "BACKFILL"),
                    // Same occurred_at as creation: seq keeps creation first.
                    listOf("RECONCILIATION_REQUIRED", null, "PENDING", "BACKFILL"),
                    listOf("RECONCILIATION_REQUIRED", null, "RECONCILIATION_REQUIRED", "BACKFILL"),
                    listOf("REFUNDED", null, "PENDING", "BACKFILL"),
                    listOf("REFUNDED", null, "REFUNDED", "BACKFILL"),
                )
                val refundedAt =
                    dsl.fetchValue(
                        """
                        SELECT e.occurred_at = p.refunded_at
                        FROM wego.tours_operator_payment_audit_event e
                        JOIN wego.tours_operator_payment p ON p.id = e.payment_id
                        WHERE e.to_status = 'REFUNDED'
                        """.trimIndent(),
                        Boolean::class.java,
                    )
                assertThat(refundedAt).isEqualTo(true)
            }
        } finally {
            upgradePostgres.stop()
        }
    }

    @Test
    fun `V24 and V25 count guests not bookings, apply the owner revision and price unit tours`() {
        val upgradePostgres =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("wego_v23_upgrade")
                .withUsername("wego_upgrade")
                .withPassword("wego_upgrade")
        upgradePostgres.start()
        try {
            val migrationLocations = arrayOf("classpath:db/migration", "classpath:db/migration/data")
            Flyway
                .configure()
                .dataSource(upgradePostgres.jdbcUrl, upgradePostgres.username, upgradePostgres.password)
                .locations(*migrationLocations)
                .target("23")
                .load()
                .migrate()

            val slotFits = UUID.randomUUID()
            val slotOver = UUID.randomUUID()
            upgradePostgres.createConnection("").use { connection ->
                val dsl = DSL.using(connection, SQLDialect.POSTGRES)
                val tourId =
                    dsl.fetchValue(
                        "SELECT id FROM wego.tours_operator_tour WHERE slug = 'super-safari-adventure'",
                        UUID::class.java,
                    ) ?: error("V17 seeded tour missing")
                // Before V24 each booking counted as one place, whatever its size.
                listOf(slotFits to 10, slotOver to 4).forEachIndexed { index, (slotId, capacity) ->
                    dsl.execute(
                        """
                        INSERT INTO wego.tours_operator_tour_slot
                          (id, tour_id, date, time_slot, capacity, booked_count, is_blocked, created_at)
                        VALUES (?, ?, DATE '2027-03-10' + ?, 'MORNING', ?, 2, false, now())
                        """.trimIndent(),
                        slotId,
                        tourId,
                        index,
                        capacity,
                    )
                }
                // slotFits: a NEW party of 3 and a CONFIRMED party of 2 hold places; a CANCELLED party of 4 does not.
                // slotOver: two NEW parties of 3 — 6 guests on a 4-place slot.
                val bookings =
                    listOf(
                        Triple(slotFits, 3, "NEW"),
                        Triple(slotFits, 2, "CONFIRMED"),
                        Triple(slotFits, 4, "CANCELLED"),
                        Triple(slotOver, 3, "NEW"),
                        Triple(slotOver, 3, "NEW"),
                    )
                bookings.forEachIndexed { index, (slotId, guests, status) ->
                    dsl.execute(
                        """
                        INSERT INTO wego.tours_operator_booking
                          (id, reference, tour_id, slot_id, tour_date, time_slot,
                           adults_count, children_count, price_adult_eur, price_child_eur, total_eur,
                           customer_full_name, customer_phone, customer_nationality,
                           hotel_name, locale, status, created_at, confirmed_at, cancelled_at, cancellation_reason)
                        VALUES (?, ?, ?, ?, DATE '2027-03-10', 'MORNING',
                                ?, 0, 10.00, NULL, ? * 10.00,
                                'Migration Test', '+20100000000', 'EG',
                                'Migration Hotel', 'en', ?, now(),
                                CASE WHEN ? = 'CONFIRMED' THEN now() END,
                                CASE WHEN ? = 'CANCELLED' THEN now() END,
                                CASE WHEN ? = 'CANCELLED' THEN 'test' END)
                        """.trimIndent(),
                        UUID.randomUUID(),
                        "STR-2027-3${index + 1}",
                        tourId,
                        slotId,
                        guests,
                        guests,
                        status,
                        status,
                        status,
                        status,
                    )
                }
            }

            val upgraded =
                Flyway
                    .configure()
                    .dataSource(upgradePostgres.jdbcUrl, upgradePostgres.username, upgradePostgres.password)
                    .locations(*migrationLocations)
                    .load()
            upgraded.migrate()
            assertThat(upgraded.info().applied().map { it.version.toString() }).contains("24", "25")

            upgradePostgres.createConnection("").use { connection ->
                val dsl = DSL.using(connection, SQLDialect.POSTGRES)
                fun slot(id: UUID) =
                    dsl.fetchOne("SELECT capacity, booked_count FROM wego.tours_operator_tour_slot WHERE id = ?", id)!!.let {
                        it.get(0, Int::class.java) to it.get(1, Int::class.java)
                    }
                assertThat(slot(slotFits)).isEqualTo(10 to 5)
                // Nobody's booking is dropped: the slot grows to fit and shows as full.
                assertThat(slot(slotOver)).isEqualTo(6 to 6)

                fun tour(slug: String) =
                    dsl.fetchOne(
                        "SELECT price_adult_cents, price_basis, is_active, capacity, duration_text FROM wego.tours_operator_tour WHERE slug = ?",
                        slug,
                    )!!
                assertThat(tour("sunset-quad-bike").get(0, Long::class.java)).isEqualTo(3500L)
                assertThat(tour("crocodile-show").get(2, Boolean::class.java)).isFalse()
                assertThat(tour("evening-cruise").get(4, String::class.java)).isEqualTo("About 4 hours (18:00–22:00)")
                assertThat(tour("double-buggy-camel-ride").get(1, String::class.java)).isEqualTo("PER_UNIT")
                assertThat(tour("speed-boat-adventure").get(3, Int::class.java)).isEqualTo(5)
                assertThat(tour("super-safari-adventure").get(1, String::class.java)).isEqualTo("PER_PERSON")

                val options =
                    dsl
                        .fetch(
                            """
                            SELECT t.slug, o.code, o.seats_per_unit, o.price_cents
                            FROM wego.tours_operator_tour_price_option o
                            JOIN wego.tours_operator_tour t ON t.id = o.tour_id
                            ORDER BY t.slug, o.sort_order
                            """.trimIndent(),
                        ).map { "${it.get(0)}:${it.get(1)}:${it.get(2)}:${it.get(3)}" }
                assertThat(options).containsExactly(
                    "double-buggy-camel-ride:buggy:2:3000",
                    "sharm-airport-transfer:sedan:4:1500",
                    "sharm-airport-transfer:suv:4:2000",
                    "sharm-airport-transfer:minibus:8:3500",
                    "speed-boat-adventure:boat:5:15000",
                )

                // The total must follow the unit snapshot; a per-unit row with a per-person price is refused.
                assertThatThrownBy {
                    dsl.execute(
                        """
                        UPDATE wego.tours_operator_booking
                        SET price_option_code = 'buggy', price_option_label = 'Two-seat buggy', seats_per_unit = 2,
                            unit_count = 2, unit_price_eur = 30.00
                        WHERE reference = 'STR-2027-31'
                        """.trimIndent(),
                    )
                }.isInstanceOf(DataAccessException::class.java)
            }
        } finally {
            upgradePostgres.stop()
        }
    }
}
