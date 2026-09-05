package com.wego.accounting.application

import com.wego.accounting.domain.AccountId
import com.wego.accounting.domain.AccountType
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Each account shows a balance in exactly one column — its real net
 * direction, not necessarily its "normal" side (an over-drawn account
 * genuinely can sit on the opposite side from where it usually lives).
 * `debitBalance`/`creditBalance` are mutually exclusive: whichever one is
 * non-zero is the account's real balance; the other is always zero.
 */
data class TrialBalanceLine(
    val accountId: AccountId,
    val code: String,
    val name: String,
    val accountType: AccountType,
    val debitBalance: BigDecimal,
    val creditBalance: BigDecimal,
)

/** `totalDebits == totalCredits` is the entire point of this report — the real, mechanical proof that every posted entry actually balanced. */
data class TrialBalance(
    val asOfDate: LocalDate,
    val lines: List<TrialBalanceLine>,
) {
    val totalDebits: BigDecimal get() = lines.sumOf { it.debitBalance }
    val totalCredits: BigDecimal get() = lines.sumOf { it.creditBalance }
}
