package com.wego.toursoperator.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Immutable price snapshot captured at booking creation time.
 * adultsCount × priceAdult + childrenCount × priceChild = totalEur.
 * The snapshot is immutable — a later change to the tour's price must
 * never retroactively change an existing booking's total.
 */
data class BookingPricing(
    val adultsCount: Int,
    val childrenCount: Int,
    val priceAdult: Money,
    /** Null when the tour has no child price (child travels free or not applicable). */
    val priceChild: Money?,
    val totalEur: Money,
) {
    init {
        require(adultsCount >= 1) { "At least one adult is required per booking" }
        require(childrenCount >= 0) { "Children count must not be negative" }
        if (childrenCount > 0) {
            requireNotNull(priceChild) { "priceChild must be set when childrenCount > 0" }
        }

        val expected =
            priceAdult.amount
                .multiply(BigDecimal(adultsCount))
                .add((priceChild?.amount ?: BigDecimal.ZERO).multiply(BigDecimal(childrenCount)))
                .setScale(Money.REQUIRED_SCALE, RoundingMode.HALF_UP)

        require(totalEur.amount.compareTo(expected) == 0) {
            "totalEur (${totalEur.amount}) does not match adultsCount×priceAdult + childrenCount×priceChild ($expected)"
        }
    }

    companion object {
        fun compute(
            adultsCount: Int,
            childrenCount: Int,
            priceAdult: Money,
            priceChild: Money?,
        ): BookingPricing {
            val childTotal =
                if (childrenCount > 0 && priceChild != null) {
                    priceChild.amount.multiply(BigDecimal(childrenCount))
                } else {
                    BigDecimal.ZERO
                }
            val total =
                priceAdult.amount
                    .multiply(BigDecimal(adultsCount))
                    .add(childTotal)
                    .setScale(Money.REQUIRED_SCALE, RoundingMode.HALF_UP)

            return BookingPricing(
                adultsCount = adultsCount,
                childrenCount = childrenCount,
                priceAdult = priceAdult,
                priceChild = priceChild,
                totalEur = Money(total),
            )
        }
    }
}
