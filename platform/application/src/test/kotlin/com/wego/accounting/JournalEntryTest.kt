package com.wego.accounting

import com.wego.accounting.domain.AccountId
import com.wego.accounting.domain.JournalEntry
import com.wego.accounting.domain.JournalEntryId
import com.wego.accounting.domain.JournalLine
import com.wego.accounting.domain.JournalLineDirection
import com.wego.accounting.domain.JournalLineId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

class JournalEntryTest {
    private val now = Instant.parse("2026-08-31T00:00:00Z")
    private val entryDate = LocalDate.parse("2026-08-31")
    private val cashAccountId = AccountId.generate()
    private val revenueAccountId = AccountId.generate()

    private fun line(
        accountId: AccountId,
        direction: JournalLineDirection,
        amount: String,
        order: Int = 0,
    ) = JournalLine(JournalLineId.generate(), accountId, direction, BigDecimal(amount), order)

    private fun balancedLines(amount: String = "100.00") =
        listOf(
            line(cashAccountId, JournalLineDirection.DEBIT, amount, 0),
            line(revenueAccountId, JournalLineDirection.CREDIT, amount, 1),
        )

    @Test
    fun `a balanced two-line entry posts successfully`() {
        val entry =
            JournalEntry.post(
                JournalEntryId.generate(),
                entryDate,
                "Booking revenue",
                null,
                "EGP",
                balancedLines(),
                null,
                null,
                now,
            )
        assertThat(entry.debitTotal).isEqualByComparingTo(BigDecimal("100.00"))
        assertThat(entry.creditTotal).isEqualByComparingTo(BigDecimal("100.00"))
    }

    @Test
    fun `rejects fewer than 2 lines`() {
        assertThatIllegalArgumentException().isThrownBy {
            JournalEntry.post(
                JournalEntryId.generate(),
                entryDate,
                "Bad entry",
                null,
                "EGP",
                listOf(line(cashAccountId, JournalLineDirection.DEBIT, "100.00")),
                null,
                null,
                now,
            )
        }
    }

    @Test
    fun `rejects an entry with only debit lines`() {
        assertThatIllegalArgumentException().isThrownBy {
            JournalEntry.post(
                JournalEntryId.generate(),
                entryDate,
                "All debits",
                null,
                "EGP",
                listOf(
                    line(cashAccountId, JournalLineDirection.DEBIT, "50.00", 0),
                    line(revenueAccountId, JournalLineDirection.DEBIT, "50.00", 1),
                ),
                null,
                null,
                now,
            )
        }
    }

    @Test
    fun `rejects an unbalanced entry`() {
        assertThatIllegalArgumentException().isThrownBy {
            JournalEntry.post(
                JournalEntryId.generate(),
                entryDate,
                "Unbalanced",
                null,
                "EGP",
                listOf(
                    line(cashAccountId, JournalLineDirection.DEBIT, "100.00", 0),
                    line(revenueAccountId, JournalLineDirection.CREDIT, "99.00", 1),
                ),
                null,
                null,
                now,
            )
        }
    }

    @Test
    fun `rejects a lowercase currency code`() {
        assertThatIllegalArgumentException().isThrownBy {
            JournalEntry.post(JournalEntryId.generate(), entryDate, "Bad currency", null, "egp", balancedLines(), null, null, now)
        }
    }

    @Test
    fun `reversing flips every line's direction and links back to the original`() {
        val original =
            JournalEntry.post(
                JournalEntryId.generate(),
                entryDate,
                "Booking revenue",
                "BK-1",
                "EGP",
                balancedLines(),
                null,
                null,
                now,
            )
        val reversedAt = Instant.parse("2026-09-01T00:00:00Z")

        val reversal =
            JournalEntry.reverse(
                JournalEntryId.generate(),
                original,
                LocalDate.parse("2026-09-01"),
                "Booking cancelled",
                null,
                null,
                reversedAt,
            )

        assertThat(reversal.reversalOfEntryId).isEqualTo(original.id)
        assertThat(reversal.reference).isEqualTo(original.reference)
        assertThat(reversal.isReversal).isTrue()
        assertThat(original.isReversal).isFalse()
        assertThat(reversal.description).isEqualTo("Reversal: Booking cancelled")

        val originalCashLine = original.lines.first { it.accountId == cashAccountId }
        val reversalCashLine = reversal.lines.first { it.accountId == cashAccountId }
        assertThat(reversalCashLine.direction).isEqualTo(JournalLineDirection.CREDIT)
        assertThat(originalCashLine.direction).isEqualTo(JournalLineDirection.DEBIT)
        assertThat(reversalCashLine.amount).isEqualByComparingTo(originalCashLine.amount)

        // A reversal is itself a real, balanced entry — proven by not throwing when reconstructed.
        assertThat(reversal.debitTotal).isEqualByComparingTo(reversal.creditTotal)
    }
}
