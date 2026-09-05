package com.wego.accounting.api

import com.wego.accounting.application.BalanceSheet
import com.wego.accounting.application.BalanceSheetLine
import com.wego.accounting.application.IncomeStatement
import com.wego.accounting.application.IncomeStatementLine
import com.wego.accounting.application.TrialBalance
import com.wego.accounting.application.TrialBalanceLine
import com.wego.accounting.domain.AccountType
import java.time.LocalDate
import java.util.UUID

data class TrialBalanceLineResponse(
    val accountId: UUID,
    val code: String,
    val name: String,
    val accountType: AccountType,
    val debitBalance: String,
    val creditBalance: String,
)

fun TrialBalanceLine.toResponse(): TrialBalanceLineResponse =
    TrialBalanceLineResponse(accountId.value, code, name, accountType, debitBalance.toPlainString(), creditBalance.toPlainString())

data class TrialBalanceResponse(
    val asOfDate: LocalDate,
    val lines: List<TrialBalanceLineResponse>,
    val totalDebits: String,
    val totalCredits: String,
)

fun TrialBalance.toResponse(): TrialBalanceResponse =
    TrialBalanceResponse(asOfDate, lines.map { it.toResponse() }, totalDebits.toPlainString(), totalCredits.toPlainString())

data class IncomeStatementLineResponse(
    val accountId: UUID,
    val code: String,
    val name: String,
    val amount: String,
)

fun IncomeStatementLine.toResponse(): IncomeStatementLineResponse =
    IncomeStatementLineResponse(accountId.value, code, name, amount.toPlainString())

data class IncomeStatementResponse(
    val from: LocalDate,
    val to: LocalDate,
    val revenueLines: List<IncomeStatementLineResponse>,
    val expenseLines: List<IncomeStatementLineResponse>,
    val totalRevenue: String,
    val totalExpenses: String,
    val netIncome: String,
)

fun IncomeStatement.toResponse(): IncomeStatementResponse =
    IncomeStatementResponse(
        from = from,
        to = to,
        revenueLines = revenueLines.map { it.toResponse() },
        expenseLines = expenseLines.map { it.toResponse() },
        totalRevenue = totalRevenue.toPlainString(),
        totalExpenses = totalExpenses.toPlainString(),
        netIncome = netIncome.toPlainString(),
    )

data class BalanceSheetLineResponse(
    val accountId: UUID?,
    val code: String?,
    val name: String,
    val amount: String,
)

fun BalanceSheetLine.toResponse(): BalanceSheetLineResponse = BalanceSheetLineResponse(accountId?.value, code, name, amount.toPlainString())

data class BalanceSheetResponse(
    val asOfDate: LocalDate,
    val assetLines: List<BalanceSheetLineResponse>,
    val liabilityLines: List<BalanceSheetLineResponse>,
    val equityLines: List<BalanceSheetLineResponse>,
    val totalAssets: String,
    val totalLiabilities: String,
    val totalEquity: String,
)

fun BalanceSheet.toResponse(): BalanceSheetResponse =
    BalanceSheetResponse(
        asOfDate = asOfDate,
        assetLines = assetLines.map { it.toResponse() },
        liabilityLines = liabilityLines.map { it.toResponse() },
        equityLines = equityLines.map { it.toResponse() },
        totalAssets = totalAssets.toPlainString(),
        totalLiabilities = totalLiabilities.toPlainString(),
        totalEquity = totalEquity.toPlainString(),
    )
