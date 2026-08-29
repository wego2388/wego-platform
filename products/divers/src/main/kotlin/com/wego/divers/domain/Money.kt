package com.wego.divers.domain

import java.math.BigDecimal

/**
 * A monetary amount at this client's fixed scale of 2 decimal places —
 * matching every EUR/USD/EGP-class currency this client uses today and the
 * `NUMERIC(10,2)` columns backing it (see `DATA_MODELING_RULES.md`'s money
 * rules). `scale == 2` is enforced here rather than left to Postgres's own
 * silent rounding on insert: every construction path must call
 * `BigDecimal.setScale(2, RoundingMode.HALF_UP)` explicitly first, so a
 * rounding decision is visible in code, not implicit in the database.
 * A genuinely zero-decimal currency (e.g. JPY) is out of scope for this
 * client and would require revisiting this assumption.
 */
data class Money(
    val amount: BigDecimal,
    val currencyCode: String,
) {
    init {
        require(amount >= BigDecimal.ZERO) { "Money amount must not be negative" }
        require(amount.scale() == REQUIRED_SCALE) {
            "Money amount must have exactly $REQUIRED_SCALE decimal places (call setScale explicitly before constructing)"
        }
        // Matches the `numeric(10,2)` column this always lands in — enforced
        // here, not just at the request DTO's `MoneyDto.amount` pattern,
        // because a computed total (unitPrice x billableQuantity) can exceed
        // that pattern's per-field bound even when every input passed it
        // individually; without this, such a total reaches Postgres as a raw
        // numeric-overflow error instead of a clean domain rejection.
        require(amount <= MAX_AMOUNT) { "Money amount must not exceed $MAX_AMOUNT" }
        require(CURRENCY_CODE_FORMAT.matches(currencyCode)) { "Currency code must be a 3-letter uppercase ISO 4217 code" }
    }

    companion object {
        const val REQUIRED_SCALE = 2
        val MAX_AMOUNT: BigDecimal = BigDecimal("99999999.99")
        private val CURRENCY_CODE_FORMAT = Regex("^[A-Z]{3}$")
    }
}
