package com.wego.toursoperator.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal

class BookingPricingTest {
    private val buggy = TourPriceOption(code = "buggy", labelEn = "Two-seat buggy", seatsPerUnit = 2, priceCents = 3000, sortOrder = 0)

    private fun eur(value: String) = Money(BigDecimal(value))

    @Test
    fun `per person total is adults and children at their prices and seats count every guest`() {
        val pricing = BookingPricing.compute(2, 1, eur("35.00"), eur("17.50"))
        assertEquals(eur("87.50"), pricing.totalEur)
        assertEquals(3, pricing.seats)
    }

    @Test
    fun `per unit total is units times the unit price whatever the head count`() {
        val pricing = BookingPricing.computePerUnit(adultsCount = 3, childrenCount = 1, option = buggy, unitCount = 2)
        assertEquals(eur("60.00"), pricing.totalEur)
        assertEquals(eur("0.00"), pricing.priceAdult)
        assertEquals(4, pricing.seats)
        assertEquals("buggy", pricing.unit?.optionCode)
    }

    @Test
    fun `a per unit booking holds every seat of its units`() {
        assertEquals(2, BookingPricing.computePerUnit(1, 0, buggy, 1).seats)
        assertEquals(1, BookingPricing.computePerUnit(1, 0, buggy, 1).guests)
    }

    @Test
    fun `per unit pricing refuses guests who do not fit and units nobody uses`() {
        assertThrows<IllegalArgumentException> { BookingPricing.computePerUnit(3, 0, buggy, 1) }
        assertThrows<IllegalArgumentException> { BookingPricing.computePerUnit(1, 0, buggy, 2) }
    }

    @Test
    fun `a stored snapshot whose total does not follow from its unit is rejected`() {
        assertThrows<IllegalArgumentException> {
            BookingPricing(
                adultsCount = 2,
                childrenCount = 0,
                priceAdult = Money.ZERO,
                priceChild = null,
                totalEur = eur("45.00"),
                unit = UnitPurchase("buggy", "Two-seat buggy", 2, 1, eur("30.00")),
            )
        }
    }

    @Test
    fun `money from cents is exact`() {
        assertEquals(eur("150.00"), Money.fromCents(15000))
        assertEquals(eur("0.05"), Money.fromCents(5))
    }
}
