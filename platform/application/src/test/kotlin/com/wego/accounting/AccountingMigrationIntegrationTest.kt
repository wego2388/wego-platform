package com.wego.accounting

import com.wego.generated.jooq.tables.AccountingAccount.ACCOUNTING_ACCOUNT
import com.wego.generated.jooq.tables.AccountingJournalEntry.ACCOUNTING_JOURNAL_ENTRY
import com.wego.generated.jooq.tables.AccountingJournalLine.ACCOUNTING_JOURNAL_LINE
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
class AccountingMigrationIntegrationTest(
    @Autowired private val flyway: Flyway,
) {
    @Test
    fun `boot auto migrates the accounting schema, seeds a real starter chart of accounts, and jooq types honor its constraints`() {
        assertThat(
            flyway.info().applied().map { it.version.toString() },
        ).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12")

        postgres.createConnection("").use { connection ->
            val dsl = DSL.using(connection, SQLDialect.POSTGRES)
            val now = OffsetDateTime.of(2026, 8, 31, 0, 0, 0, 0, ZoneOffset.UTC)

            // The real starter chart of accounts, seeded by V12 itself.
            val cash = dsl.selectFrom(ACCOUNTING_ACCOUNT).where(ACCOUNTING_ACCOUNT.CODE.eq("1000")).fetchOne()
            assertThat(cash).isNotNull
            assertThat(cash!!.name).isEqualTo("Cash on Hand")
            assertThat(cash.accountType).isEqualTo("ASSET")

            // Real constraint: an account code must be unique.
            assertThatThrownBy {
                dsl
                    .insertInto(ACCOUNTING_ACCOUNT)
                    .set(ACCOUNTING_ACCOUNT.ID, UUID.randomUUID())
                    .set(ACCOUNTING_ACCOUNT.CODE, "1000")
                    .set(ACCOUNTING_ACCOUNT.NAME, "Duplicate Cash")
                    .set(ACCOUNTING_ACCOUNT.ACCOUNT_TYPE, "ASSET")
                    .set(ACCOUNTING_ACCOUNT.CREATED_AT, now)
                    .set(ACCOUNTING_ACCOUNT.UPDATED_AT, now)
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("accounting_account_code_unique")

            val revenueAccount = dsl.selectFrom(ACCOUNTING_ACCOUNT).where(ACCOUNTING_ACCOUNT.CODE.eq("4000")).fetchOne()!!
            val cashAccount = cash

            val entryId = UUID.randomUUID()
            dsl
                .insertInto(ACCOUNTING_JOURNAL_ENTRY)
                .set(ACCOUNTING_JOURNAL_ENTRY.ID, entryId)
                .set(ACCOUNTING_JOURNAL_ENTRY.ENTRY_DATE, LocalDate.of(2026, 8, 31))
                .set(ACCOUNTING_JOURNAL_ENTRY.DESCRIPTION, "Constraint test entry")
                .set(ACCOUNTING_JOURNAL_ENTRY.CURRENCY_CODE, "EGP")
                .set(ACCOUNTING_JOURNAL_ENTRY.POSTED_AT, now)
                .execute()

            // Real constraint: a journal line's amount must be positive.
            assertThatThrownBy {
                dsl
                    .insertInto(ACCOUNTING_JOURNAL_LINE)
                    .set(ACCOUNTING_JOURNAL_LINE.ID, UUID.randomUUID())
                    .set(ACCOUNTING_JOURNAL_LINE.JOURNAL_ENTRY_ID, entryId)
                    .set(ACCOUNTING_JOURNAL_LINE.ACCOUNT_ID, cashAccount.id)
                    .set(ACCOUNTING_JOURNAL_LINE.DIRECTION, "DEBIT")
                    .set(ACCOUNTING_JOURNAL_LINE.AMOUNT, BigDecimal.ZERO)
                    .set(ACCOUNTING_JOURNAL_LINE.LINE_ORDER, 0)
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("accounting_journal_line_amount_positive")

            // Real constraint: currency_code must be a 3-letter uppercase ISO 4217 code.
            assertThatThrownBy {
                dsl
                    .insertInto(ACCOUNTING_JOURNAL_ENTRY)
                    .set(ACCOUNTING_JOURNAL_ENTRY.ID, UUID.randomUUID())
                    .set(ACCOUNTING_JOURNAL_ENTRY.ENTRY_DATE, LocalDate.of(2026, 8, 31))
                    .set(ACCOUNTING_JOURNAL_ENTRY.DESCRIPTION, "Bad currency entry")
                    .set(ACCOUNTING_JOURNAL_ENTRY.CURRENCY_CODE, "egp")
                    .set(ACCOUNTING_JOURNAL_ENTRY.POSTED_AT, now)
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("accounting_journal_entry_currency_code_format")

            // Real constraint: an entry can be reversed at most once (a
            // second reversal pointing at the same original is rejected by
            // the unique partial index).
            val firstReversalId = UUID.randomUUID()
            dsl
                .insertInto(ACCOUNTING_JOURNAL_ENTRY)
                .set(ACCOUNTING_JOURNAL_ENTRY.ID, firstReversalId)
                .set(ACCOUNTING_JOURNAL_ENTRY.ENTRY_DATE, LocalDate.of(2026, 8, 31))
                .set(ACCOUNTING_JOURNAL_ENTRY.DESCRIPTION, "Reversal: first")
                .set(ACCOUNTING_JOURNAL_ENTRY.CURRENCY_CODE, "EGP")
                .set(ACCOUNTING_JOURNAL_ENTRY.REVERSAL_OF_ENTRY_ID, entryId)
                .set(ACCOUNTING_JOURNAL_ENTRY.POSTED_AT, now)
                .execute()

            assertThatThrownBy {
                dsl
                    .insertInto(ACCOUNTING_JOURNAL_ENTRY)
                    .set(ACCOUNTING_JOURNAL_ENTRY.ID, UUID.randomUUID())
                    .set(ACCOUNTING_JOURNAL_ENTRY.ENTRY_DATE, LocalDate.of(2026, 8, 31))
                    .set(ACCOUNTING_JOURNAL_ENTRY.DESCRIPTION, "Reversal: second")
                    .set(ACCOUNTING_JOURNAL_ENTRY.CURRENCY_CODE, "EGP")
                    .set(ACCOUNTING_JOURNAL_ENTRY.REVERSAL_OF_ENTRY_ID, entryId)
                    .set(ACCOUNTING_JOURNAL_ENTRY.POSTED_AT, now)
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("accounting_journal_entry_reversal_unique")

            assertThat(revenueAccount.accountType).isEqualTo("REVENUE")
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
