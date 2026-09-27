package com.wego.toursoperator.domain

import java.math.BigDecimal

/**
 * Monetary amount in EUR at scale 2 — mirrors the Money type in
 * products/divers. Safari Tours Sharm prices exclusively in EUR.
 * Scale is enforced here rather than silently delegated to Postgres
 * rounding, so every construction path makes the rounding decision
 * explicit in code.
 */
data class Money(
    val amount: BigDecimal,
) {
    init {
        require(amount >= BigDecimal.ZERO) { "Money amount must not be negative" }
        require(amount.scale() == REQUIRED_SCALE) {
            "Money amount must have exactly $REQUIRED_SCALE decimal places (call setScale explicitly before constructing)"
        }
        require(amount <= MAX_AMOUNT) { "Money amount must not exceed $MAX_AMOUNT" }
    }

    companion object {
        const val CURRENCY_CODE: String = "EUR"
        const val REQUIRED_SCALE = 2
        val MAX_AMOUNT: BigDecimal = BigDecimal("99999999.99")
        val ZERO: Money = Money(BigDecimal.ZERO.setScale(REQUIRED_SCALE))
    }
}
