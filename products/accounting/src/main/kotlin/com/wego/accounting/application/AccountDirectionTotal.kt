package com.wego.accounting.application

import com.wego.accounting.domain.AccountId
import com.wego.accounting.domain.JournalLineDirection
import java.math.BigDecimal

/** The sum of one account's lines in one direction, within whatever date window the query asked for — the raw material every report is built from. */
data class AccountDirectionTotal(
    val accountId: AccountId,
    val direction: JournalLineDirection,
    val total: BigDecimal,
)
