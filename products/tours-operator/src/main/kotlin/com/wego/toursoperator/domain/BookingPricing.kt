package com.wego.toursoperator.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The unit a per-unit booking bought: e.g. 2 × "buggy" (2 seats each) at
 * €30, or 1 × "suv" at €20. The label is snapshotted so a later rename of the
 * option never changes what an existing booking says it bought.
 */
data class UnitPurchase(
    val optionCode: String,
    val optionLabel: String,
    val seatsPerUnit: Int,
    val unitCount: Int,
    val unitPrice: Money,
) {
    init {
        require(optionCode.matches(TourPriceOption.CODE_FORMAT)) { "Price option code must be lowercase letters, digits or hyphens" }
        require(optionLabel.isNotBlank()) { "Price option label must not be blank" }
        require(seatsPerUnit >= 1) { "A unit seats at least one guest" }
        require(unitCount >= 1) { "At least one unit is required" }
    }
}

/**
 * Immutable price snapshot captured at booking creation time. A later change
 * to the tour's price must never retroactively change an existing booking.
 *
 * Per person: total = adults × priceAdult + children × priceChild.
 * Per unit ([unit] set): total = unitCount × unitPrice; adults/children are
 * the guests travelling and must fit in the units bought. priceAdult is then
 * zero and priceChild null — the unit is what was priced.
 */
data class BookingPricing(
    val adultsCount: Int,
    val childrenCount: Int,
    val priceAdult: Money,
    /** Null when the tour has no child price (child travels free or not applicable). */
    val priceChild: Money?,
    val totalEur: Money,
    val unit: UnitPurchase? = null,
) {
    /** Guests travelling. */
    val guests: Int get() = adultsCount + childrenCount

    /**
     * Places this booking takes in its slot. Per person: one per guest. Per
     * unit: every seat of every unit bought — a solo rider holds a whole
     * two-seat buggy, a party of two holds the whole private boat.
     */
    val seats: Int get() = unit?.let { it.unitCount * it.seatsPerUnit } ?: guests

    init {
        require(adultsCount >= 1) { "At least one adult is required per booking" }
        require(childrenCount >= 0) { "Children count must not be negative" }

        val expected =
            if (unit != null) {
                require(priceAdult.amount.signum() == 0 && priceChild == null) {
                    "A per-unit booking carries its price on the unit, not per person"
                }
                require(guests <= unit.unitCount * unit.seatsPerUnit) {
                    "$guests guests do not fit in ${unit.unitCount} × ${unit.seatsPerUnit} seats"
                }
                require(unit.unitCount <= guests) { "More units than guests" }
                unit.unitPrice.amount.multiply(BigDecimal(unit.unitCount))
            } else {
                if (childrenCount > 0) {
                    requireNotNull(priceChild) { "priceChild must be set when childrenCount > 0" }
                }
                priceAdult.amount
                    .multiply(BigDecimal(adultsCount))
                    .add((priceChild?.amount ?: BigDecimal.ZERO).multiply(BigDecimal(childrenCount)))
            }.setScale(Money.REQUIRED_SCALE, RoundingMode.HALF_UP)

        require(totalEur.amount.compareTo(expected) == 0) {
            "totalEur (${totalEur.amount}) does not match the booking's pricing ($expected)"
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

        fun computePerUnit(
            adultsCount: Int,
            childrenCount: Int,
            option: TourPriceOption,
            unitCount: Int,
        ): BookingPricing {
            val unitPrice = Money.fromCents(option.priceCents)
            return BookingPricing(
                adultsCount = adultsCount,
                childrenCount = childrenCount,
                priceAdult = Money.ZERO,
                priceChild = null,
                totalEur =
                    Money(
                        unitPrice.amount
                            .multiply(BigDecimal(unitCount))
                            .setScale(Money.REQUIRED_SCALE, RoundingMode.HALF_UP),
                    ),
                unit =
                    UnitPurchase(
                        optionCode = option.code,
                        optionLabel = option.labelEn,
                        seatsPerUnit = option.seatsPerUnit,
                        unitCount = unitCount,
                        unitPrice = unitPrice,
                    ),
            )
        }
    }
}
