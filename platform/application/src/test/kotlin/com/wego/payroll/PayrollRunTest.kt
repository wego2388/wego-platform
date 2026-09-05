package com.wego.payroll

import com.wego.payroll.domain.PayrollLine
import com.wego.payroll.domain.PayrollLineId
import com.wego.payroll.domain.PayrollRun
import com.wego.payroll.domain.PayrollRunId
import com.wego.payroll.domain.PayrollRunStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class PayrollRunTest {
    private val now = Instant.parse("2026-08-31T00:00:00Z")
    private val periodStart = LocalDate.parse("2026-08-01")
    private val periodEnd = LocalDate.parse("2026-08-31")

    private fun oneLine(amount: String = "15000.00") = listOf(PayrollLine(PayrollLineId.generate(), UUID.randomUUID(), BigDecimal(amount)))

    private fun create(lines: List<PayrollLine> = oneLine()): PayrollRun =
        PayrollRun.create(
            id = PayrollRunId.generate(),
            payPeriodStart = periodStart,
            payPeriodEnd = periodEnd,
            lines = lines,
            currencyCode = "EGP",
            createdByUserId = null,
            now = now,
        )

    @Test
    fun `rejects a pay period end before its start`() {
        assertThatIllegalArgumentException().isThrownBy {
            PayrollRun.create(
                id = PayrollRunId.generate(),
                payPeriodStart = periodEnd,
                payPeriodEnd = periodStart,
                lines = oneLine(),
                currencyCode = "EGP",
                createdByUserId = null,
                now = now,
            )
        }
    }

    @Test
    fun `rejects an empty line list`() {
        assertThatIllegalArgumentException().isThrownBy { create(lines = emptyList()) }
    }

    @Test
    fun `rejects a lowercase currency code`() {
        assertThatIllegalArgumentException().isThrownBy {
            PayrollRun.create(
                id = PayrollRunId.generate(),
                payPeriodStart = periodStart,
                payPeriodEnd = periodEnd,
                lines = oneLine(),
                currencyCode = "egp",
                createdByUserId = null,
                now = now,
            )
        }
    }

    @Test
    fun `starts as a draft with no posting fields`() {
        val run = create()
        assertThat(run.status).isEqualTo(PayrollRunStatus.DRAFT)
        assertThat(run.isDraft).isTrue()
        assertThat(run.postedAt).isNull()
        assertThat(run.journalEntryId).isNull()
    }

    @Test
    fun `totalAmount sums every line`() {
        val run =
            create(
                lines =
                    listOf(
                        PayrollLine(PayrollLineId.generate(), UUID.randomUUID(), BigDecimal("15000.00")),
                        PayrollLine(PayrollLineId.generate(), UUID.randomUUID(), BigDecimal("12000.00")),
                    ),
            )
        assertThat(run.totalAmount).isEqualByComparingTo(BigDecimal("27000.00"))
    }

    @Test
    fun `posting sets status and posting fields together`() {
        val run = create()
        val actor = UUID.randomUUID()
        val journalEntryId = UUID.randomUUID()
        val postedAt = Instant.parse("2026-09-01T00:00:00Z")

        run.post(actor, journalEntryId, postedAt)

        assertThat(run.status).isEqualTo(PayrollRunStatus.POSTED)
        assertThat(run.isDraft).isFalse()
        assertThat(run.postedByUserId).isEqualTo(actor)
        assertThat(run.postedAt).isEqualTo(postedAt)
        assertThat(run.journalEntryId).isEqualTo(journalEntryId)
    }

    @Test
    fun `an already-posted run cannot be posted again`() {
        val run = create()
        run.post(UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-09-01T00:00:00Z"))

        assertThatIllegalArgumentException().isThrownBy {
            run.post(UUID.randomUUID(), UUID.randomUUID(), Instant.parse("2026-09-02T00:00:00Z"))
        }
    }
}
