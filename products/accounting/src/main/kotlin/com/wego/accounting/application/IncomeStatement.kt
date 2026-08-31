package com.wego.accounting.application

import com.wego.accounting.domain.AccountId
import java.math.BigDecimal
import java.time.LocalDate

data class IncomeStatementLine(
    val accountId: AccountId,
    val code: String,
    val name: String,
    val amount: BigDecimal,
)

/** A period report (bounded by `from`/`to`), not cumulative — matching how a real income statement always covers a specific range, unlike a balance sheet's single point in time. */
data class IncomeStatement(
    val from: LocalDate,
    val to: LocalDate,
    val revenueLines: List<IncomeStatementLine>,
    val expenseLines: List<IncomeStatementLine>,
) {
    val totalRevenue: BigDecimal get() = revenueLines.sumOf { it.amount }
    val totalExpenses: BigDecimal get() = expenseLines.sumOf { it.amount }
    val netIncome: BigDecimal get() = totalRevenue - totalExpenses
}
