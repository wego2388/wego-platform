package com.wego.travelmarketplace.domain

import java.math.BigDecimal

/**
 * Deliberately a separate type from `com.wego.divers.domain.Money`, not a
 * shared one — `products/divers` and `products/travel-marketplace` are never
 * on the same application's compile classpath (see WEGO-010-A Packet 0R), so
 * neither product may depend on the other's domain types. Same rules as that
 * type: fixed 2-decimal scale matching this client's `numeric(10,2)`
 * columns, non-negative, a real 3-letter ISO 4217 currency code. This client
 * currently only prices in EGP (`LOCALES_AND_CONTENT.md`'s organizational
 * currency), but the currency code stays a real field rather than a hardcoded
 * constant so multi-currency display (explicitly deferred, same document)
 * does not require a breaking schema change later.
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
