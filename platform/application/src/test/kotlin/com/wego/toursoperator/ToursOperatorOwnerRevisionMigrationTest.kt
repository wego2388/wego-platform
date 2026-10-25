package com.wego.toursoperator

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.UUID

/** Real V35 -> V36 upgrade: drift aborts atomically, accepted prices never rewrite sales snapshots. */
@Testcontainers(disabledWithoutDocker = true)
class ToursOperatorOwnerRevisionMigrationTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18.4-alpine").withDatabaseName("owner_revision_test")
    }

    @Test
    fun `owner revision refuses content drift then upgrades catalog while preserving requests and bookings`() {
        fun flyway(target: String) =
            Flyway
                .configure()
                .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
                .locations("classpath:db/migration", "classpath:db/migration/data")
                .target(target)
                .load()
        flyway("35").migrate()
        val tour = "a0000001-0000-0000-0000-000000000003"
        val slot = UUID.randomUUID()
        val booking = UUID.randomUUID()
        val request = UUID.randomUUID()
        val content = """{
            "name":"Double Buggy & Camel Ride",
            "shortDescription":"Drive a two-seat sand buggy through the dunes, stop at the Valley of Echo and a Bedouin tent, then ride a camel.",
            "description":"After a short drive to the buggy center and a safety briefing, you and your partner drive a sand buggy across the dunes towards the Echo Mountains. You stop at the Valley of Echo and a Bedouin tent for tea, take a camel ride across the sand, and drive back to the start before your transfer to the hotel.",
            "includes":["Hotel pickup and drop-off","Air-conditioned transport","Two-seat sand buggy","Camel ride","Stops at the Valley of Echo and a Bedouin tent","Bedouin tea","Safety equipment","Tour guide","Entrance fees"],
            "meetingPoint":"Preserve this owner field"
        }"""
        postgres.createConnection("").use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO wego.tours_operator_tour_content(tour_id,locale,stage,document,updated_at) VALUES (?::uuid,'en',?,?::jsonb,NOW())",
                ).use { sql ->
                    for (stage in listOf("DRAFT", "PUBLISHED")) {
                        sql.setString(1, tour)
                        sql.setString(2, stage)
                        sql.setString(3, content)
                        sql.executeUpdate()
                    }
                }
            connection.createStatement().use { sql ->
                sql.execute(
                    """INSERT INTO wego.tours_operator_tour_slot(id,tour_id,date,time_slot,capacity,booked_count,created_at)
                    VALUES ('$slot','$tour','2027-01-01','MORNING',10,2,NOW())""",
                )
                sql.execute(
                    """INSERT INTO wego.tours_operator_booking(id,reference,tour_id,slot_id,tour_date,time_slot,
                    adults_count,children_count,price_adult_eur,total_eur,customer_full_name,customer_phone,customer_nationality,
                    hotel_name,locale,status,created_at,confirmed_at,channel,client_request_id,price_option_code,price_option_label,seats_per_unit,unit_count,unit_price_eur)
                    VALUES ('$booking','STR-2026-123','$tour','$slot','2027-01-01','MORNING',2,0,0,30,
                    'Synthetic revision guest','+201000000001','EG','Synthetic hotel','en','CONFIRMED',NOW(),NOW(),'OFFICE','$booking','buggy','Two-seat buggy',2,1,30)""",
                )
                sql.execute(
                    """INSERT INTO wego.tours_operator_online_request(id,reference,payload_hash,tour_id,preferred_date,
                    adults_count,children_count,estimated_total,full_name,phone,nationality,hotel_name,locale,status,created_at,updated_at)
                    VALUES ('$request','STQ-${request.toString().replace(
                        "-",
                        "",
                    ).uppercase()}','${"A".repeat(64)}','$tour','2027-01-01',2,0,30,
                    'Synthetic revision guest','+201000000001','EG','Synthetic hotel','en','NEW',NOW(),NOW())""",
                )
                sql.execute(
                    "UPDATE wego.tours_operator_tour_content SET document=jsonb_set(document,'{name}','\"New owner edit\"') WHERE tour_id='$tour' AND stage='DRAFT'",
                )
            }
        }
        assertThatThrownBy { flyway("36").migrate() }.hasMessageContaining("requires localized content re-review")
        postgres.createConnection("").use { connection ->
            connection.createStatement().use { sql ->
                sql.executeQuery("SELECT price_adult_cents FROM wego.tours_operator_tour WHERE id='$tour'").use { rows ->
                    rows.next()
                    assertThat(rows.getLong(1)).isEqualTo(3000)
                }
                sql.execute(
                    "UPDATE wego.tours_operator_tour_content SET document=jsonb_set(document,'{name}','\"Double Buggy & Camel Ride\"') WHERE tour_id='$tour' AND stage='DRAFT'",
                )
            }
        }
        flyway("36").migrate()
        postgres.createConnection("").use { connection ->
            connection.createStatement().use { sql ->
                val expected =
                    mapOf(
                        "double-buggy-camel-ride" to 3500L,
                        "sunset-quad-bike" to 1500L,
                        "bedouin-dinner-camel-ride" to 2500L,
                        "evening-cruise" to 3000L,
                        "dahab-colored-canyon" to 3000L,
                    )
                for ((slug, cents) in expected) {
                    sql.executeQuery("SELECT price_adult_cents FROM wego.tours_operator_tour WHERE slug='$slug'").use { rows ->
                        rows.next()
                        assertThat(rows.getLong(1)).isEqualTo(cents)
                    }
                }
                sql
                    .executeQuery(
                        "SELECT price_cents FROM wego.tours_operator_tour_price_option WHERE tour_id='$tour' AND code='buggy'",
                    ).use { rows ->
                        rows.next()
                        assertThat(rows.getLong(1)).isEqualTo(3500)
                    }
                sql
                    .executeQuery(
                        "SELECT price_cents FROM wego.tours_operator_tour_price_option WHERE tour_id='a0000001-0000-0000-0000-000000000030' AND code='minibus'",
                    ).use { rows ->
                        rows.next()
                        assertThat(rows.getLong(1)).isEqualTo(3000)
                    }
                sql.executeQuery("SELECT document::text FROM wego.tours_operator_tour_content WHERE tour_id='$tour'").use { rows ->
                    var count = 0
                    while (rows.next()) {
                        assertThat(rows.getString(1)).doesNotContain("Camel", "camel").contains("Preserve this owner field")
                        count++
                    }
                    assertThat(count).isEqualTo(2)
                }
                sql.executeQuery("SELECT total_eur,unit_price_eur FROM wego.tours_operator_booking WHERE id='$booking'").use { rows ->
                    rows.next()
                    assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("30.00")
                    assertThat(rows.getBigDecimal(2)).isEqualByComparingTo("30.00")
                }
                sql.executeQuery("SELECT estimated_total,status FROM wego.tours_operator_online_request WHERE id='$request'").use { rows ->
                    rows.next()
                    assertThat(rows.getBigDecimal(1)).isEqualByComparingTo("30.00")
                    assertThat(rows.getString(2)).isEqualTo("NEW")
                }
                sql.executeQuery("SELECT booked_count FROM wego.tours_operator_tour_slot WHERE id='$slot'").use { rows ->
                    rows.next()
                    assertThat(rows.getInt(1)).isEqualTo(2)
                }
            }
        }
    }
}
