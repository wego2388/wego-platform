package com.wego.accounting.application

import com.wego.accounting.domain.AccountId
import java.math.BigDecimal
import java.time.LocalDate

data class BalanceSheetLine(
    val accountId: AccountId?,
    val code: String?,
    val name: String,
    val amount: BigDecimal,
)

/**
 * `totalAssets == totalLiabilities + totalEquity` is the real invariant a
 * balance sheet must hold — but this system has no formal period-closing
 * step (no month/year-end process that zeroes Revenue/Expense accounts
 * into Equity, the way a full general-ledger product would). Without one,
 * REVENUE/EXPENSE activity would sit in the ledger uncounted on this
 * report and the invariant would silently break the moment any revenue or
 * expense is posted.
 *
 * The standard, legitimate technique for a system without closing entries
 * — not a hack — is computing Retained Earnings on the fly: net income
 * across the *entire* ledger history up to [asOfDate] (there is no fiscal
 * year boundary to reset against, since nothing here ever closes), added
 * to `equityLines` as a synthetic `accountId == null` line. `equityLines`
 * therefore mixes real equity accounts with this one computed line —
 * `BalanceSheetLine.accountId` is null exactly on that one line, so a
 * caller can always tell which is which.
 */
data class BalanceSheet(
    val asOfDate: LocalDate,
    val assetLines: List<BalanceSheetLine>,
    val liabilityLines: List<BalanceSheetLine>,
    val equityLines: List<BalanceSheetLine>,
) {
    val totalAssets: BigDecimal get() = assetLines.sumOf { it.amount }
    val totalLiabilities: BigDecimal get() = liabilityLines.sumOf { it.amount }
    val totalEquity: BigDecimal get() = equityLines.sumOf { it.amount }
}
