package com.wego.divers.domain

import java.math.BigDecimal

/**
 * An immutable pricing snapshot captured at booking-creation time — never
 * recomputed from the offering later, so a subsequent change to the
 * offering's `unitPrice` cannot retroactively change what a customer was
 * actually charged.
 */
data class BookingPricing(
    val pricingBasis: PricingBasis,
    val unitPrice: Money,
    val billableQuantity: Int,
    val totalPrice: Money,
) {
    init {
        require(billableQuantity > 0) { "Billable quantity must be positive" }
        require(unitPrice.currencyCode == totalPrice.currencyCode) { "Unit price and total price must share a currency" }
        // Exact BigDecimal arithmetic (no binary float involved) — this is
        // a real integrity check, not an approximation, and mirrors the
        // database's own divers_booking_total_price_matches_basis CHECK.
        require(totalPrice.amount.compareTo(unitPrice.amount.multiply(BigDecimal(billableQuantity))) == 0) {
            "Total price must equal unit price times billable quantity"
        }
    }

    companion object {
        fun forOffering(
            offering: Offering,
            partySize: Int,
        ): BookingPricing {
            val billableQuantity =
                when (offering.pricingBasis) {
                    PricingBasis.PER_PARTICIPANT -> partySize
                    PricingBasis.FLAT -> 1
                }
            val totalAmount = offering.unitPrice.amount.multiply(BigDecimal(billableQuantity))
            return BookingPricing(
                pricingBasis = offering.pricingBasis,
                unitPrice = offering.unitPrice,
                billableQuantity = billableQuantity,
                totalPrice = Money(totalAmount, offering.unitPrice.currencyCode),
            )
        }
    }
}
