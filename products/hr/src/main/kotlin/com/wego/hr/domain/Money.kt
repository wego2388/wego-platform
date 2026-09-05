package com.wego.hr.domain

import java.math.BigDecimal

/**
 * A monetary amount at this platform's fixed scale of 2 decimal places —
 * same rules as `com.wego.divers.domain.Money`. Duplicated rather than
 * shared: `products/divers` and `products/hr` are separate Modulith
 * product modules, and this platform has no shared kernel `Money` type
 * yet. Promoting one is a real, deliberate refactor for whenever a third
 * real need makes the duplication a proven cost — not a decision to make
 * as a side effect of building this module.
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
        require(amount <= MAX_AMOUNT) { "Money amount must not exceed $MAX_AMOUNT" }
        require(CURRENCY_CODE_FORMAT.matches(currencyCode)) { "Currency code must be a 3-letter uppercase ISO 4217 code" }
    }

    companion object {
        const val REQUIRED_SCALE = 2
        val MAX_AMOUNT: BigDecimal = BigDecimal("99999999.99")
        private val CURRENCY_CODE_FORMAT = Regex("^[A-Z]{3}$")
    }
}
