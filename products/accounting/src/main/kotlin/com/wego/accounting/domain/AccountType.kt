package com.wego.accounting.domain

/** The fundamental accounting-equation split: assets and expenses grow with a debit; liabilities, equity, and revenue grow with a credit. */
enum class AccountType {
    ASSET,
    LIABILITY,
    EQUITY,
    REVENUE,
    EXPENSE,
    ;

    val normalBalance: JournalLineDirection
        get() =
            when (this) {
                ASSET, EXPENSE -> JournalLineDirection.DEBIT
                LIABILITY, EQUITY, REVENUE -> JournalLineDirection.CREDIT
            }
}
