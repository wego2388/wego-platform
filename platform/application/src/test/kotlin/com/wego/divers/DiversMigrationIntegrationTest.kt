package com.wego.divers

import com.wego.generated.jooq.tables.DiversBooking.DIVERS_BOOKING
import com.wego.generated.jooq.tables.DiversOffering.DIVERS_OFFERING
import com.wego.generated.jooq.tables.IdentityUser.IDENTITY_USER
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
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class DiversMigrationIntegrationTest(
    @Autowired private val flyway: Flyway,
) {
    @Test
    fun `boot auto migrates the divers schema and generated jooq types honor its constraints`() {
        assertThat(flyway.info().applied().map { it.version.toString() }).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9")

        postgres.createConnection("").use { connection ->
            val dsl = DSL.using(connection, SQLDialect.POSTGRES)
            val now = OffsetDateTime.of(2026, 8, 15, 0, 0, 0, 0, ZoneOffset.UTC)
            val userId = UUID.randomUUID()
            dsl
                .insertInto(IDENTITY_USER)
                .set(IDENTITY_USER.ID, userId)
                .set(IDENTITY_USER.EMAIL, "divers-migration-test@example.com")
                .set(IDENTITY_USER.PASSWORD_HASH, "irrelevant-hash")
                .set(IDENTITY_USER.CREATED_AT, now)
                .execute()

            val offeringId = UUID.randomUUID()
            val inserted =
                dsl
                    .insertInto(DIVERS_OFFERING)
                    .set(DIVERS_OFFERING.ID, offeringId)
                    .set(DIVERS_OFFERING.OFFERING_TYPE, "DIVE_TRIP")
                    .set(DIVERS_OFFERING.TITLE, "Reef Trip")
                    .set(DIVERS_OFFERING.STARTS_ON, LocalDate.parse("2026-08-20"))
                    .set(DIVERS_OFFERING.CAPACITY, 5)
                    .set(DIVERS_OFFERING.PRICING_BASIS, "PER_PARTICIPANT")
                    .set(DIVERS_OFFERING.UNIT_PRICE, BigDecimal("45.00"))
                    .set(DIVERS_OFFERING.CURRENCY_CODE, "EUR")
                    .set(DIVERS_OFFERING.STATUS, "ACTIVE")
                    .set(DIVERS_OFFERING.CREATED_AT, now)
                    .execute()
            assertThat(inserted).isEqualTo(1)

            assertThatThrownBy {
                dsl
                    .insertInto(DIVERS_OFFERING)
                    .set(DIVERS_OFFERING.ID, UUID.randomUUID())
                    .set(DIVERS_OFFERING.OFFERING_TYPE, "NOT_A_REAL_TYPE")
                    .set(DIVERS_OFFERING.TITLE, "Invalid")
                    .set(DIVERS_OFFERING.STARTS_ON, LocalDate.parse("2026-08-20"))
                    .set(DIVERS_OFFERING.PRICING_BASIS, "PER_PARTICIPANT")
                    .set(DIVERS_OFFERING.UNIT_PRICE, BigDecimal("45.00"))
                    .set(DIVERS_OFFERING.CURRENCY_CODE, "EUR")
                    .set(DIVERS_OFFERING.STATUS, "ACTIVE")
                    .set(DIVERS_OFFERING.CREATED_AT, now)
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("divers_offering_type_known")

            assertThatThrownBy {
                dsl
                    .insertInto(DIVERS_OFFERING)
                    .set(DIVERS_OFFERING.ID, UUID.randomUUID())
                    .set(DIVERS_OFFERING.OFFERING_TYPE, "COURSE")
                    .set(DIVERS_OFFERING.TITLE, "Backwards dates")
                    .set(DIVERS_OFFERING.STARTS_ON, LocalDate.parse("2026-08-20"))
                    .set(DIVERS_OFFERING.ENDS_ON, LocalDate.parse("2026-08-19"))
                    .set(DIVERS_OFFERING.PRICING_BASIS, "PER_PARTICIPANT")
                    .set(DIVERS_OFFERING.UNIT_PRICE, BigDecimal("350.00"))
                    .set(DIVERS_OFFERING.CURRENCY_CODE, "EUR")
                    .set(DIVERS_OFFERING.STATUS, "ACTIVE")
                    .set(DIVERS_OFFERING.CREATED_AT, now)
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("divers_offering_ends_on_after_starts_on")

            assertThatThrownBy {
                dsl
                    .insertInto(DIVERS_OFFERING)
                    .set(DIVERS_OFFERING.ID, UUID.randomUUID())
                    .set(DIVERS_OFFERING.OFFERING_TYPE, "DIVE_TRIP")
                    .set(DIVERS_OFFERING.TITLE, "Bad basis")
                    .set(DIVERS_OFFERING.STARTS_ON, LocalDate.parse("2026-08-20"))
                    .set(DIVERS_OFFERING.PRICING_BASIS, "PER_HOUR")
                    .set(DIVERS_OFFERING.UNIT_PRICE, BigDecimal("45.00"))
                    .set(DIVERS_OFFERING.CURRENCY_CODE, "EUR")
                    .set(DIVERS_OFFERING.STATUS, "ACTIVE")
                    .set(DIVERS_OFFERING.CREATED_AT, now)
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("divers_offering_pricing_basis_known")

            assertThatThrownBy {
                dsl
                    .insertInto(DIVERS_OFFERING)
                    .set(DIVERS_OFFERING.ID, UUID.randomUUID())
                    .set(DIVERS_OFFERING.OFFERING_TYPE, "DIVE_TRIP")
                    .set(DIVERS_OFFERING.TITLE, "Closed without timestamp")
                    .set(DIVERS_OFFERING.STARTS_ON, LocalDate.parse("2026-08-20"))
                    .set(DIVERS_OFFERING.PRICING_BASIS, "FLAT")
                    .set(DIVERS_OFFERING.UNIT_PRICE, BigDecimal("45.00"))
                    .set(DIVERS_OFFERING.CURRENCY_CODE, "EUR")
                    .set(DIVERS_OFFERING.STATUS, "CLOSED")
                    .set(DIVERS_OFFERING.CREATED_AT, now)
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("divers_offering_closed_at_matches_status")

            val bookingId = UUID.randomUUID()
            val bookingInserted =
                dsl
                    .insertInto(DIVERS_BOOKING)
                    .set(DIVERS_BOOKING.ID, bookingId)
                    .set(DIVERS_BOOKING.OFFERING_ID, offeringId)
                    .set(DIVERS_BOOKING.PARTY_SIZE, 2)
                    .set(DIVERS_BOOKING.CUSTOMER_NAME, "Ada Lovelace")
                    .set(DIVERS_BOOKING.CUSTOMER_EMAIL, "ada@example.com")
                    .set(DIVERS_BOOKING.STATUS, "CONFIRMED")
                    .set(DIVERS_BOOKING.PAYMENT_STATUS, "UNPAID")
                    .set(DIVERS_BOOKING.PRICING_BASIS, "PER_PARTICIPANT")
                    .set(DIVERS_BOOKING.UNIT_PRICE, BigDecimal("45.00"))
                    .set(DIVERS_BOOKING.BILLABLE_QUANTITY, 2)
                    .set(DIVERS_BOOKING.TOTAL_PRICE, BigDecimal("90.00"))
                    .set(DIVERS_BOOKING.CURRENCY_CODE, "EUR")
                    .set(DIVERS_BOOKING.IDEMPOTENCY_KEY, "constraint-test-key")
                    .set(DIVERS_BOOKING.IDEMPOTENCY_FINGERPRINT, "a".repeat(64))
                    .set(DIVERS_BOOKING.CREATED_BY_USER_ID, userId)
                    .set(DIVERS_BOOKING.CREATED_AT, now)
                    .execute()
            assertThat(bookingInserted).isEqualTo(1)

            assertThatThrownBy {
                dsl
                    .insertInto(DIVERS_BOOKING)
                    .set(DIVERS_BOOKING.ID, UUID.randomUUID())
                    .set(DIVERS_BOOKING.OFFERING_ID, offeringId)
                    .set(DIVERS_BOOKING.PARTY_SIZE, 1)
                    .set(DIVERS_BOOKING.CUSTOMER_NAME, "No contact")
                    .set(DIVERS_BOOKING.STATUS, "CONFIRMED")
                    .set(DIVERS_BOOKING.PAYMENT_STATUS, "UNPAID")
                    .set(DIVERS_BOOKING.PRICING_BASIS, "PER_PARTICIPANT")
                    .set(DIVERS_BOOKING.UNIT_PRICE, BigDecimal("45.00"))
                    .set(DIVERS_BOOKING.BILLABLE_QUANTITY, 1)
                    .set(DIVERS_BOOKING.TOTAL_PRICE, BigDecimal("45.00"))
                    .set(DIVERS_BOOKING.CURRENCY_CODE, "EUR")
                    .set(DIVERS_BOOKING.IDEMPOTENCY_KEY, "no-contact-key")
                    .set(DIVERS_BOOKING.IDEMPOTENCY_FINGERPRINT, "a".repeat(64))
                    .set(DIVERS_BOOKING.CREATED_BY_USER_ID, userId)
                    .set(DIVERS_BOOKING.CREATED_AT, now)
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("divers_booking_customer_contact_present")

            assertThatThrownBy {
                dsl
                    .insertInto(DIVERS_BOOKING)
                    .set(DIVERS_BOOKING.ID, UUID.randomUUID())
                    .set(DIVERS_BOOKING.OFFERING_ID, offeringId)
                    .set(DIVERS_BOOKING.PARTY_SIZE, 1)
                    .set(DIVERS_BOOKING.CUSTOMER_NAME, "Blank contact")
                    .set(DIVERS_BOOKING.CUSTOMER_EMAIL, "   ")
                    .set(DIVERS_BOOKING.CUSTOMER_PHONE, "")
                    .set(DIVERS_BOOKING.STATUS, "CONFIRMED")
                    .set(DIVERS_BOOKING.PAYMENT_STATUS, "UNPAID")
                    .set(DIVERS_BOOKING.PRICING_BASIS, "PER_PARTICIPANT")
                    .set(DIVERS_BOOKING.UNIT_PRICE, BigDecimal("45.00"))
                    .set(DIVERS_BOOKING.BILLABLE_QUANTITY, 1)
                    .set(DIVERS_BOOKING.TOTAL_PRICE, BigDecimal("45.00"))
                    .set(DIVERS_BOOKING.CURRENCY_CODE, "EUR")
                    .set(DIVERS_BOOKING.IDEMPOTENCY_KEY, "blank-contact-key")
                    .set(DIVERS_BOOKING.IDEMPOTENCY_FINGERPRINT, "a".repeat(64))
                    .set(DIVERS_BOOKING.CREATED_BY_USER_ID, userId)
                    .set(DIVERS_BOOKING.CREATED_AT, now)
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("divers_booking_customer_contact_present")

            assertThatThrownBy {
                dsl
                    .insertInto(DIVERS_BOOKING)
                    .set(DIVERS_BOOKING.ID, UUID.randomUUID())
                    .set(DIVERS_BOOKING.OFFERING_ID, offeringId)
                    .set(DIVERS_BOOKING.PARTY_SIZE, 1)
                    .set(DIVERS_BOOKING.CUSTOMER_NAME, "Cancelled mismatch")
                    .set(DIVERS_BOOKING.CUSTOMER_EMAIL, "mismatch@example.com")
                    .set(DIVERS_BOOKING.STATUS, "CANCELLED")
                    .set(DIVERS_BOOKING.PAYMENT_STATUS, "UNPAID")
                    .set(DIVERS_BOOKING.PRICING_BASIS, "PER_PARTICIPANT")
                    .set(DIVERS_BOOKING.UNIT_PRICE, BigDecimal("45.00"))
                    .set(DIVERS_BOOKING.BILLABLE_QUANTITY, 1)
                    .set(DIVERS_BOOKING.TOTAL_PRICE, BigDecimal("45.00"))
                    .set(DIVERS_BOOKING.CURRENCY_CODE, "EUR")
                    .set(DIVERS_BOOKING.IDEMPOTENCY_KEY, "cancelled-mismatch-key")
                    .set(DIVERS_BOOKING.IDEMPOTENCY_FINGERPRINT, "a".repeat(64))
                    .set(DIVERS_BOOKING.CREATED_BY_USER_ID, userId)
                    .set(DIVERS_BOOKING.CREATED_AT, now)
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("divers_booking_cancelled_at_matches_status")

            assertThatThrownBy {
                dsl
                    .insertInto(DIVERS_BOOKING)
                    .set(DIVERS_BOOKING.ID, UUID.randomUUID())
                    .set(DIVERS_BOOKING.OFFERING_ID, offeringId)
                    .set(DIVERS_BOOKING.PARTY_SIZE, 1)
                    .set(DIVERS_BOOKING.CUSTOMER_NAME, "Total mismatch")
                    .set(DIVERS_BOOKING.CUSTOMER_EMAIL, "total-mismatch@example.com")
                    .set(DIVERS_BOOKING.STATUS, "CONFIRMED")
                    .set(DIVERS_BOOKING.PAYMENT_STATUS, "UNPAID")
                    .set(DIVERS_BOOKING.PRICING_BASIS, "PER_PARTICIPANT")
                    .set(DIVERS_BOOKING.UNIT_PRICE, BigDecimal("45.00"))
                    // Matches party_size (1), so this row satisfies
                    // divers_booking_billable_quantity_matches_basis and
                    // isolates the violation below to total_price alone.
                    .set(DIVERS_BOOKING.BILLABLE_QUANTITY, 1)
                    // Wrong on purpose: should be 45.00 for 45.00 x 1.
                    .set(DIVERS_BOOKING.TOTAL_PRICE, BigDecimal("100.00"))
                    .set(DIVERS_BOOKING.CURRENCY_CODE, "EUR")
                    .set(DIVERS_BOOKING.IDEMPOTENCY_KEY, "total-mismatch-key")
                    .set(DIVERS_BOOKING.IDEMPOTENCY_FINGERPRINT, "a".repeat(64))
                    .set(DIVERS_BOOKING.CREATED_BY_USER_ID, userId)
                    .set(DIVERS_BOOKING.CREATED_AT, now)
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("divers_booking_total_price_matches_basis")

            assertThatThrownBy {
                dsl
                    .insertInto(DIVERS_BOOKING)
                    .set(DIVERS_BOOKING.ID, UUID.randomUUID())
                    .set(DIVERS_BOOKING.OFFERING_ID, offeringId)
                    .set(DIVERS_BOOKING.PARTY_SIZE, 2)
                    .set(DIVERS_BOOKING.CUSTOMER_NAME, "Quantity mismatch")
                    .set(DIVERS_BOOKING.CUSTOMER_EMAIL, "quantity-mismatch@example.com")
                    .set(DIVERS_BOOKING.STATUS, "CONFIRMED")
                    .set(DIVERS_BOOKING.PAYMENT_STATUS, "UNPAID")
                    .set(DIVERS_BOOKING.PRICING_BASIS, "PER_PARTICIPANT")
                    .set(DIVERS_BOOKING.UNIT_PRICE, BigDecimal("45.00"))
                    // Wrong on purpose: PER_PARTICIPANT must bill exactly
                    // party_size (2), not 1 — total_price is kept internally
                    // consistent (45.00 x 1) so only this constraint fires.
                    .set(DIVERS_BOOKING.BILLABLE_QUANTITY, 1)
                    .set(DIVERS_BOOKING.TOTAL_PRICE, BigDecimal("45.00"))
                    .set(DIVERS_BOOKING.CURRENCY_CODE, "EUR")
                    .set(DIVERS_BOOKING.IDEMPOTENCY_KEY, "quantity-mismatch-key")
                    .set(DIVERS_BOOKING.IDEMPOTENCY_FINGERPRINT, "a".repeat(64))
                    .set(DIVERS_BOOKING.CREATED_BY_USER_ID, userId)
                    .set(DIVERS_BOOKING.CREATED_AT, now)
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("divers_booking_billable_quantity_matches_basis")

            assertThatThrownBy {
                dsl
                    .insertInto(DIVERS_BOOKING)
                    .set(DIVERS_BOOKING.ID, UUID.randomUUID())
                    .set(DIVERS_BOOKING.OFFERING_ID, offeringId)
                    .set(DIVERS_BOOKING.PARTY_SIZE, 1)
                    .set(DIVERS_BOOKING.CUSTOMER_NAME, "Bad fingerprint")
                    .set(DIVERS_BOOKING.CUSTOMER_EMAIL, "bad-fingerprint@example.com")
                    .set(DIVERS_BOOKING.STATUS, "CONFIRMED")
                    .set(DIVERS_BOOKING.PAYMENT_STATUS, "UNPAID")
                    .set(DIVERS_BOOKING.PRICING_BASIS, "PER_PARTICIPANT")
                    .set(DIVERS_BOOKING.UNIT_PRICE, BigDecimal("45.00"))
                    .set(DIVERS_BOOKING.BILLABLE_QUANTITY, 1)
                    .set(DIVERS_BOOKING.TOTAL_PRICE, BigDecimal("45.00"))
                    .set(DIVERS_BOOKING.CURRENCY_CODE, "EUR")
                    .set(DIVERS_BOOKING.IDEMPOTENCY_KEY, "bad-fingerprint-key")
                    .set(DIVERS_BOOKING.IDEMPOTENCY_FINGERPRINT, "not-a-real-fingerprint")
                    .set(DIVERS_BOOKING.CREATED_BY_USER_ID, userId)
                    .set(DIVERS_BOOKING.CREATED_AT, now)
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("divers_booking_idempotency_fingerprint_format")

            assertThatThrownBy {
                dsl
                    .insertInto(DIVERS_BOOKING)
                    .set(DIVERS_BOOKING.ID, UUID.randomUUID())
                    .set(DIVERS_BOOKING.OFFERING_ID, offeringId)
                    .set(DIVERS_BOOKING.PARTY_SIZE, 1)
                    .set(DIVERS_BOOKING.CUSTOMER_NAME, "Blank key")
                    .set(DIVERS_BOOKING.CUSTOMER_EMAIL, "blank-key@example.com")
                    .set(DIVERS_BOOKING.STATUS, "CONFIRMED")
                    .set(DIVERS_BOOKING.PAYMENT_STATUS, "UNPAID")
                    .set(DIVERS_BOOKING.PRICING_BASIS, "PER_PARTICIPANT")
                    .set(DIVERS_BOOKING.UNIT_PRICE, BigDecimal("45.00"))
                    .set(DIVERS_BOOKING.BILLABLE_QUANTITY, 1)
                    .set(DIVERS_BOOKING.TOTAL_PRICE, BigDecimal("45.00"))
                    .set(DIVERS_BOOKING.CURRENCY_CODE, "EUR")
                    .set(DIVERS_BOOKING.IDEMPOTENCY_KEY, "")
                    .set(DIVERS_BOOKING.IDEMPOTENCY_FINGERPRINT, "a".repeat(64))
                    .set(DIVERS_BOOKING.CREATED_BY_USER_ID, userId)
                    .set(DIVERS_BOOKING.CREATED_AT, now)
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("divers_booking_idempotency_key_length")

            assertThatThrownBy {
                dsl
                    .insertInto(DIVERS_BOOKING)
                    .set(DIVERS_BOOKING.ID, UUID.randomUUID())
                    .set(DIVERS_BOOKING.OFFERING_ID, offeringId)
                    .set(DIVERS_BOOKING.PARTY_SIZE, 1)
                    .set(DIVERS_BOOKING.CUSTOMER_NAME, "Duplicate idempotency")
                    .set(DIVERS_BOOKING.CUSTOMER_EMAIL, "dup@example.com")
                    .set(DIVERS_BOOKING.STATUS, "CONFIRMED")
                    .set(DIVERS_BOOKING.PAYMENT_STATUS, "UNPAID")
                    .set(DIVERS_BOOKING.PRICING_BASIS, "PER_PARTICIPANT")
                    .set(DIVERS_BOOKING.UNIT_PRICE, BigDecimal("45.00"))
                    .set(DIVERS_BOOKING.BILLABLE_QUANTITY, 1)
                    .set(DIVERS_BOOKING.TOTAL_PRICE, BigDecimal("45.00"))
                    .set(DIVERS_BOOKING.CURRENCY_CODE, "EUR")
                    // Same (created_by_user_id, idempotency_key) pair as the
                    // booking inserted above — must violate the unique constraint.
                    .set(DIVERS_BOOKING.IDEMPOTENCY_KEY, "constraint-test-key")
                    .set(DIVERS_BOOKING.IDEMPOTENCY_FINGERPRINT, "b".repeat(64))
                    .set(DIVERS_BOOKING.CREATED_BY_USER_ID, userId)
                    .set(DIVERS_BOOKING.CREATED_AT, now)
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("divers_booking_idempotency_key_unique")

            assertThatThrownBy {
                dsl.deleteFrom(DIVERS_OFFERING).where(DIVERS_OFFERING.ID.eq(offeringId)).execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("divers_booking_offering_id_fkey")
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
