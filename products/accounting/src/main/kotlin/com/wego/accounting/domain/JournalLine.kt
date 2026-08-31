package com.wego.accounting.domain

import java.math.BigDecimal

/** One debit or credit against one account. Whether the referenced account exists and is active is validated by the posting service, not here — this class only enforces its own local invariant. */
data class JournalLine(
    val id: JournalLineId,
    val accountId: AccountId,
    val direction: JournalLineDirection,
    val amount: BigDecimal,
    val lineOrder: Int,
) {
    init {
        require(amount > BigDecimal.ZERO) { "Journal line amount must be positive" }
    }
}
