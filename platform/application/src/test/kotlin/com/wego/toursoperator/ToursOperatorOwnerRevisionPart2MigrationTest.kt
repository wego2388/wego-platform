package com.wego.toursoperator

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.UUID

/** Real V36 -> V37 upgrade: drift and open SUV references abort atomically, sales snapshots stay byte-identical. */
@Testcontainers(disabledWithoutDocker = true)
class ToursOperatorOwnerRevisionPart2MigrationTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18.4-alpine").withDatabaseName("owner_revision_part2_test")
    }

    private fun flyway(target: String) =
        Flyway
            .configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .locations("classpath:db/migration", "classpath:db/migration/data")
            .target(target)
            .load()

    private fun query(sql: String): List<String?> =
        postgres.createConnection("").use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows ->
                    buildList {
                        while (rows.next()) add(rows.getString(1))
                    }
                }
            }
        }

    private fun execute(vararg statements: String) =
        postgres.createConnection("").use { connection ->
            connection.createStatement().use { statement -> statements.forEach { statement.execute(it) } }
        }

    private fun salesFingerprint() =
        query(
            """SELECT (SELECT md5(COALESCE(string_agg(b::text,'|' ORDER BY b.id),'')) FROM wego.tours_operator_booking b)
            || (SELECT md5(COALESCE(string_agg(r::text,'|' ORDER BY r.id),'')) FROM wego.tours_operator_online_request r)
            || (SELECT md5(COALESCE(string_agg(s::text,'|' ORDER BY s.id),'')) FROM wego.tours_operator_tour_slot s)""",
        ).single()

    @Test
    fun `part 2 refuses drift and open suv references then applies facts without touching sales data`() {
        flyway("36").migrate()
        val saint = "(SELECT id FROM wego.tours_operator_tour WHERE slug='st-catherine-dahab')"
        val transfer = "(SELECT id FROM wego.tours_operator_tour WHERE slug='sharm-airport-transfer')"
        val saintDocument =
            """{"name":"x","shortDescription":"x","description":"x",
            "includes":["Hotel pickup and drop-off","Air-conditioned transport","Visit to St. Catherine's Monastery","Lunch in Dahab","Free time in Dahab","Local guide"],
            "excludes":[],"knowBeforeYouGo":["Passport","Comfortable walking shoes","Runs on Mondays, Thursdays and Saturdays."],
            "meetingPoint":"Preserve this owner field"}"""
        val saintSql = saintDocument.replace("'", "''")
        val transferDocument =
            """{"name":"x","shortDescription":"x",
            "description":"Our driver collects you from your hotel reception and takes you directly to Sharm El Sheikh International Airport, or meets you on arrival. Choose the vehicle that fits your group: a sedan (€15), a higher SUV (€20) or a Hiace minibus (€30) — the price is per vehicle, not per person.",
            "meetingPoint":"Your hotel reception, or the arrivals hall at Sharm El Sheikh airport."}"""
        val slot = UUID.randomUUID()
        val booking = UUID.randomUUID()
        val request = UUID.randomUUID()
        for (stage in listOf("DRAFT", "PUBLISHED")) {
            execute(
                "INSERT INTO wego.tours_operator_tour_content(tour_id,locale,stage,document,updated_at) VALUES ($saint,'en','$stage','$saintSql'::jsonb,NOW())",
                "INSERT INTO wego.tours_operator_tour_content(tour_id,locale,stage,document,updated_at) VALUES ($transfer,'en','$stage','$transferDocument'::jsonb,NOW())",
            )
        }
        execute(
            "INSERT INTO wego.tours_operator_tour_facts(tour_id,stage,document,updated_at) VALUES ($saint,'DRAFT','{\"hotelPickup\":\"INCLUDED\",\"guideLanguages\":[\"en\"]}'::jsonb,NOW())",
            """INSERT INTO wego.tours_operator_tour_slot(id,tour_id,date,time_slot,capacity,booked_count,created_at)
            VALUES ('$slot',$transfer,'2020-01-01','MORNING',10,4,NOW())""",
            """INSERT INTO wego.tours_operator_booking(id,reference,tour_id,slot_id,tour_date,time_slot,
            adults_count,children_count,price_adult_eur,total_eur,customer_full_name,customer_phone,customer_nationality,
            hotel_name,locale,status,created_at,confirmed_at,channel,client_request_id,price_option_code,price_option_label,seats_per_unit,unit_count,unit_price_eur)
            VALUES ('$booking','STR-2020-777',$transfer,'$slot','2020-01-01','MORNING',4,0,0,20,
            'Historic SUV guest','+201000000002','EG','Synthetic hotel','en','CONFIRMED',NOW(),NOW(),'OFFICE','$booking','suv','SUV',4,1,20)""",
            """INSERT INTO wego.tours_operator_online_request(id,reference,payload_hash,tour_id,preferred_date,
            adults_count,children_count,price_option_code,unit_count,estimated_total,full_name,phone,nationality,hotel_name,locale,status,created_at,updated_at)
            VALUES ('$request','STQ-${request.toString().replace("-", "").uppercase()}','${"B".repeat(64)}',$transfer,'2027-01-01',
            4,0,'suv',1,20,'Open SUV guest','+201000000003','EG','Synthetic hotel','en','NEW',NOW(),NOW())""",
        )

        // 1. An open website request still points at the SUV option: refuse, change nothing.
        assertThatThrownBy { flyway("37").migrate() }.hasMessageContaining("SUV option is still referenced")
        assertThat(query("SELECT count(*) FROM wego.tours_operator_tour_price_option WHERE code='suv'").single()).isEqualTo("1")
        assertThat(
            query("SELECT document->'excludes' FROM wego.tours_operator_tour_content WHERE tour_id=$saint AND stage='DRAFT'").single(),
        ).isEqualTo("[]")
        execute("UPDATE wego.tours_operator_online_request SET status='CLOSED' WHERE id='$request'")
        val closed = salesFingerprint()

        // 2. Unreviewed edit of a targeted field: refuse atomically.
        execute(
            "UPDATE wego.tours_operator_tour_content SET document=jsonb_set(document,'{excludes}','[\"Owner typed this\"]') WHERE tour_id=$saint AND stage='DRAFT'",
        )
        assertThatThrownBy { flyway("37").migrate() }.hasMessageContaining("requires localized content re-review")
        assertThat(
            query(
                "SELECT document->'includes'->>6 FROM wego.tours_operator_tour_content WHERE tour_id=$saint AND stage='PUBLISHED'",
            ).single(),
        ).isNull()
        execute(
            "UPDATE wego.tours_operator_tour_content SET document=jsonb_set(document,'{excludes}','[]') WHERE tour_id=$saint AND stage='DRAFT'",
        )

        // 3. Clean upgrade.
        flyway("37").migrate()
        for (stage in listOf("DRAFT", "PUBLISHED")) {
            val content =
                query(
                    "SELECT document::text FROM wego.tours_operator_tour_content WHERE tour_id=$saint AND stage='$stage'",
                ).single()
            assertThat(content)
                .contains(
                    "\"Dinner\"",
                    "\"Lunch in Dahab\"",
                    "\"Photography\"",
                    "\"Headscarf\"",
                    "\"Sunglasses\"",
                    "Preserve this owner field",
                )
            assertThat(
                query(
                    "SELECT document->'guideLanguages' FROM wego.tours_operator_tour_facts WHERE tour_id=$saint AND stage='$stage'",
                ).single(),
            ).isEqualTo("[\"en\", \"it\", \"ru\"]")
            val transferContent =
                query("SELECT document::text FROM wego.tours_operator_tour_content WHERE tour_id=$transfer AND stage='$stage'").single()
            assertThat(
                transferContent,
            ).doesNotContain("SUV").contains("a Hiace minibus (€30)", "confirm your hotel pickup time on WhatsApp")
        }
        assertThat(
            query("SELECT document->>'hotelPickup' FROM wego.tours_operator_tour_facts WHERE tour_id=$saint AND stage='DRAFT'").single(),
        ).isEqualTo("INCLUDED")
        assertThat(query("SELECT code FROM wego.tours_operator_tour_price_option WHERE tour_id=$transfer ORDER BY sort_order"))
            .containsExactly("sedan", "minibus")
        assertThat(query("SELECT pricing_note FROM wego.tours_operator_tour WHERE id=$transfer").single())
            .isEqualTo("Per vehicle: sedan €15 · Hiace minibus €30")
        assertThat(query("SELECT price_option_label FROM wego.tours_operator_booking WHERE id='$booking'").single()).isEqualTo("SUV")
        assertThat(salesFingerprint()).isEqualTo(closed)
    }
}
