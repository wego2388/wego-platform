package com.wego.toursoperator.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class FinanceOpsDomainTest {
    private val day: LocalDate = LocalDate.of(2027, 3, 10)
    private val now: Instant = Instant.parse("2027-03-10T09:00:00Z")
    private val tour = TourId(UUID.randomUUID())
    private val slot = TourSlotId(UUID.randomUUID())

    private fun d(value: String) = BigDecimal(value)

    private fun money(value: String) = Money(d(value))

    private fun perPerson(
        adults: Int,
        children: Int,
    ) = BookingPricing.compute(adults, children, money("35.00"), money("17.50"))

    private fun component(
        category: CostCategory,
        basis: CostBasis,
        currency: PaidCurrency,
        amount: String,
        child: String? = null,
        supplier: UUID? = null,
        driver: UUID? = null,
        from: LocalDate = day.minusDays(30),
        until: LocalDate? = null,
        forTour: TourId = tour,
    ) = CostComponent(
        id = UUID.randomUUID(),
        tourId = if (driver == null) forTour else null,
        driverId = driver,
        category = category,
        label = category.name,
        basis = basis,
        currency = currency,
        amount = d(amount),
        childAmount = child?.let(::d),
        supplierId = supplier,
        validFrom = from,
        validUntil = until,
        replacesComponentId = null,
        note = null,
        createdByUserId = null,
        createdAt = now,
    )

    private fun booking(
        reference: String,
        pricing: BookingPricing,
        office: String = "0.00",
        online: String = "0.00",
        status: BookingStatus = BookingStatus.CONFIRMED,
    ) = ProfitBooking(
        bookingId = BookingId(UUID.randomUUID()),
        reference = reference,
        tourId = tour,
        slotId = slot,
        tourDate = day,
        timeSlot = TimeSlot.MORNING,
        status = status,
        channel = BookingChannel.OFFICE,
        pricing = pricing,
        onlineRevenue = d(online),
        officeRevenue = d(office),
    )

    private val rate50 = RateChoice(d("50.0000"), RateSource.DEPARTURE_DATE)

    // ── cost components ──────────────────────────────────────────────────────

    @Test
    fun `a component applies from its first to its last day inclusive`() {
        val c = component(CostCategory.SUPPLIER, CostBasis.PER_PERSON, PaidCurrency.EGP, "10.00", from = day, until = day.plusDays(2))
        assertFalse(c.effectiveOn(day.minusDays(1)))
        assertTrue(c.effectiveOn(day))
        assertTrue(c.effectiveOn(day.plusDays(2)))
        assertFalse(c.effectiveOn(day.plusDays(3)))
    }

    @Test
    fun `per person costs children at the adult amount unless a child amount is given, per unit needs units`() {
        val noChild = component(CostCategory.SUPPLIER, CostBasis.PER_PERSON, PaidCurrency.EGP, "1100.00")
        assertEquals(d("3300.00"), noChild.bookingCost(perPerson(2, 1)))
        val withChild = component(CostCategory.OWN_EXTRA, CostBasis.PER_PERSON, PaidCurrency.EGP, "50.00", child = "25.00")
        assertEquals(d("125.00"), withChild.bookingCost(perPerson(2, 1)))
        val unit = component(CostCategory.SUPPLIER, CostBasis.PER_UNIT, PaidCurrency.EGP, "600.00")
        assertNull(unit.bookingCost(perPerson(2, 0)))
        val buggy = TourPriceOption("buggy", "Buggy", 2, 3000, 0)
        assertEquals(d("1200.00"), unit.bookingCost(BookingPricing.computePerUnit(3, 0, buggy, 2)))
    }

    @Test
    fun `invalid components are refused`() {
        assertThrows<IllegalArgumentException> { component(CostCategory.FIXED, CostBasis.PER_PERSON, PaidCurrency.EUR, "1.00") }
        assertThrows<IllegalArgumentException> {
            component(CostCategory.FIXED, CostBasis.PER_DEPARTURE, PaidCurrency.EUR, "1.00", child = "1.00")
        }
        assertThrows<IllegalArgumentException> {
            component(CostCategory.OWN_EXTRA, CostBasis.PER_PERSON, PaidCurrency.EUR, "1.00", supplier = UUID.randomUUID())
        }
        assertThrows<IllegalArgumentException> { component(CostCategory.SUPPLIER, CostBasis.PER_PERSON, PaidCurrency.EUR, "-1.00") }
        assertThrows<IllegalArgumentException> { FinanceAmount.positive(d("0.00")) }
        assertThrows<IllegalArgumentException> { FinanceAmount.positive(d("1.001")) }
        assertEquals(d("1.50"), FinanceAmount.positive(d("1.5")))
    }

    // ── profitability ────────────────────────────────────────────────────────

    @Test
    fun `profit converts EGP at the rate and splits per-departure costs by guests with the remainder on the last booking`() {
        val components =
            listOf(
                component(CostCategory.SUPPLIER, CostBasis.PER_PERSON, PaidCurrency.EGP, "1100.00"),
                component(CostCategory.FIXED, CostBasis.PER_DEPARTURE, PaidCurrency.EUR, "10.00"),
            )
        val a = booking("A", perPerson(1, 0), office = "35.00")
        val b = booking("B", perPerson(1, 0), office = "35.00")
        val c = booking("C", perPerson(1, 0), office = "35.00")
        val results = ProfitCalculator.compute(listOf(a, b, c), emptyMap(), components) { rate50 }
        // 1100/50 = 22.00 each; 10.00 split 3.33 / 3.33 / 3.34
        assertEquals(listOf(d("25.33"), d("25.33"), d("25.34")), results.sortedBy { it.booking.reference }.map { it.cost })
        val totals = ProfitTotals.of(results)
        assertEquals(d("76.00"), totals.cost)
        assertEquals(d("29.00"), totals.profit)
        assertEquals(d("27.6"), totals.marginPercent)
        assertEquals(setOf(RateSource.DEPARTURE_DATE), totals.rateSources)
    }

    @Test
    fun `no rate at all hides the profit when EGP costs exist, and EUR-only costs need no rate`() {
        val egp = listOf(component(CostCategory.SUPPLIER, CostBasis.PER_PERSON, PaidCurrency.EGP, "100.00"))
        val none = RateChoice(null, RateSource.NONE)
        val result = ProfitCalculator.compute(listOf(booking("A", perPerson(1, 0), office = "35.00")), emptyMap(), egp) { none }.single()
        assertNull(result.profit)
        assertTrue(CostGap.FX_RATE_MISSING in result.gaps)
        val eur = listOf(component(CostCategory.SUPPLIER, CostBasis.PER_PERSON, PaidCurrency.EUR, "20.00"))
        val ok = ProfitCalculator.compute(listOf(booking("A", perPerson(1, 0), office = "35.00")), emptyMap(), eur) { none }.single()
        assertEquals(d("15.00"), ok.profit)
    }

    @Test
    fun `missing components, per-unit mismatch and a driver without a rate are gaps`() {
        val single = listOf(booking("A", perPerson(2, 0), office = "70.00"))
        assertTrue(CostGap.NO_COST_COMPONENTS in ProfitCalculator.compute(single, emptyMap(), emptyList()) { rate50 }.single().gaps)
        val unit = listOf(component(CostCategory.SUPPLIER, CostBasis.PER_UNIT, PaidCurrency.EGP, "600.00"))
        assertTrue(CostGap.UNIT_COST_ON_PER_PERSON_BOOKING in ProfitCalculator.compute(single, emptyMap(), unit) { rate50 }.single().gaps)

        val driver = UUID.randomUUID()
        val fixed = listOf(component(CostCategory.FIXED, CostBasis.PER_DEPARTURE, PaidCurrency.EUR, "5.00"))
        val departure = mapOf(slot to ProfitDeparture(slot, driver, emptyList()))
        assertTrue(CostGap.DRIVER_COST_MISSING in ProfitCalculator.compute(single, departure, fixed) { rate50 }.single().gaps)
        val withRate = fixed + component(CostCategory.DRIVER, CostBasis.PER_DEPARTURE, PaidCurrency.EGP, "300.00", driver = driver)
        val priced = ProfitCalculator.compute(single, departure, withRate) { rate50 }.single()
        assertEquals(d("11.00"), priced.cost) // 5.00 + 300/50
    }

    @Test
    fun `adjustments of a departure count, reversed ones do not, and a cancelled booking keeps only its retained money`() {
        val driver = UUID.randomUUID()
        val party = PartyRef(PartyType.DRIVER, driver)
        val charge = adjustment(party, AdjustmentKind.CHARGE, "250.00", slot)
        val deduction = adjustment(party, AdjustmentKind.DEDUCTION, "50.00", slot)
        val reversed = adjustment(PartyRef(PartyType.SUPPLIER, UUID.randomUUID()), AdjustmentKind.CHARGE, "999.00", slot)
        val reversal =
            reversed.copy(
                id = UUID.randomUUID(),
                kind = AdjustmentKind.REVERSAL,
                slotId = null,
                reversesAdjustmentId = reversed.id,
            )
        val departure = mapOf(slot to ProfitDeparture(slot, driver, listOf(charge, deduction, reversed, reversal)))
        val fixed = listOf(component(CostCategory.FIXED, CostBasis.PER_DEPARTURE, PaidCurrency.EUR, "0.00"))
        val live = booking("A", perPerson(1, 0), office = "35.00")
        val cancelled = booking("B", perPerson(1, 0), office = "10.00", status = BookingStatus.CANCELLED)
        val results = ProfitCalculator.compute(listOf(live, cancelled), departure, fixed) { rate50 }
        assertEquals(d("4.00"), results.single { it.booking.reference == "A" }.cost) // (250 − 50) / 50
        val kept = results.single { it.booking.reference == "B" }
        assertEquals(d("0.00"), kept.cost)
        assertEquals(d("10.00"), kept.profit)
        assertEquals(1, ProfitTotals.of(results).guests)
    }

    // ── payables ─────────────────────────────────────────────────────────────

    private fun adjustment(
        party: PartyRef,
        kind: AdjustmentKind,
        amount: String,
        slotId: TourSlotId?,
        date: LocalDate = day,
        currency: PaidCurrency = PaidCurrency.EGP,
    ) = PayableAdjustment(UUID.randomUUID(), party, slotId, date, kind, currency, d(amount), "why", null, null, UUID.randomUUID(), now)

    private fun payment(
        party: PartyRef,
        amount: String,
        at: Instant = now,
        kind: SettlementPaymentKind = SettlementPaymentKind.PAYMENT,
        reverses: UUID? = null,
    ) = SettlementPayment(
        UUID.randomUUID(),
        party,
        kind,
        SettlementMethod.CASH,
        PaidCurrency.EGP,
        d(amount),
        d(amount),
        null,
        null,
        null,
        null,
        reverses,
        if (kind == SettlementPaymentKind.REVERSAL) "wrong" else null,
        null,
        UUID.randomUUID(),
        at,
    )

    private fun dep(
        suppliers: Set<UUID>,
        driver: UUID? = null,
        live: List<BookingPricing> = listOf(perPerson(2, 1)),
    ) = PayableDeparture(slot, tour, day, TimeSlot.MORNING, driver, suppliers, live)

    @Test
    fun `an unlinked supplier price is owed to the only supplier, and split by hand when several serve the departure`() {
        val s1 = UUID.randomUUID()
        val s2 = UUID.randomUUID()
        val price = listOf(component(CostCategory.SUPPLIER, CostBasis.PER_PERSON, PaidCurrency.EGP, "1100.00"))
        val (lines, issues) = PayablesCalculator.derive(PartyRef(PartyType.SUPPLIER, s1), listOf(dep(setOf(s1))), price, emptyList())
        assertEquals(d("3300.00"), lines.single().amount)
        assertTrue(issues.isEmpty())
        val (shared, sharedIssues) =
            PayablesCalculator.derive(
                PartyRef(PartyType.SUPPLIER, s1),
                listOf(dep(setOf(s1, s2))),
                price,
                emptyList(),
            )
        assertTrue(shared.isEmpty())
        assertEquals(OwedIssueCode.SHARED_SUPPLIER_COST, sharedIssues.first().code)
        val linked = listOf(component(CostCategory.SUPPLIER, CostBasis.PER_DEPARTURE, PaidCurrency.EUR, "40.00", supplier = s2))
        val (s2Lines, _) = PayablesCalculator.derive(PartyRef(PartyType.SUPPLIER, s2), listOf(dep(setOf(s1, s2))), linked, emptyList())
        assertEquals(PaidCurrency.EUR, s2Lines.single().currency)
        val (none, noneIssues) =
            PayablesCalculator.derive(
                PartyRef(PartyType.SUPPLIER, s1),
                listOf(dep(setOf(s1, s2))),
                linked,
                emptyList(),
            )
        assertTrue(none.isEmpty())
        assertEquals(OwedIssueCode.NO_SUPPLIER_PRICE, noneIssues.single().code)
        // A departure with no live bookings owes nothing.
        assertTrue(
            PayablesCalculator
                .derive(
                    PartyRef(PartyType.SUPPLIER, s1),
                    listOf(dep(setOf(s1), live = emptyList())),
                    price,
                    emptyList(),
                ).first
                .isEmpty(),
        )
    }

    @Test
    fun `a driver is owed the trip rate, otherwise a manual charge is needed`() {
        val driver = UUID.randomUUID()
        val party = PartyRef(PartyType.DRIVER, driver)
        val (_, issues) = PayablesCalculator.derive(party, listOf(dep(emptySet(), driver)), emptyList(), emptyList())
        assertEquals(OwedIssueCode.MANUAL_AMOUNT_NEEDED, issues.single().code)
        val manual = adjustment(party, AdjustmentKind.CHARGE, "250.00", slot)
        assertTrue(PayablesCalculator.derive(party, listOf(dep(emptySet(), driver)), emptyList(), listOf(manual)).second.isEmpty())
        val rate = component(CostCategory.DRIVER, CostBasis.PER_DEPARTURE, PaidCurrency.EGP, "300.00", driver = driver)
        assertEquals(
            d("300.00"),
            PayablesCalculator
                .derive(party, listOf(dep(emptySet(), driver)), listOf(rate), emptyList())
                .first
                .single()
                .amount,
        )
    }

    @Test
    fun `statement balances date each reversal on its own day so closed periods never change`() {
        val party = PartyRef(PartyType.SUPPLIER, UUID.randomUUID())
        val owed = listOf(OwedLine(slot, tour, day, TimeSlot.MORNING, 3, PaidCurrency.EGP, d("3300.00"), listOf("x")))
        val paid = payment(party, "1000.00", at = day.atTime(12, 0).atZone(CAIRO).toInstant())
        val reversal =
            payment(
                party,
                "1000.00",
                at =
                    day
                        .plusDays(5)
                        .atTime(12, 0)
                        .atZone(CAIRO)
                        .toInstant(),
                kind = SettlementPaymentKind.REVERSAL,
                reverses = paid.id,
            )
        val deduction = adjustment(party, AdjustmentKind.DEDUCTION, "100.00", null, day.plusDays(1))
        val moves = PayablesCalculator.movements(owed, listOf(deduction), listOf(paid, reversal)) { it.atZone(CAIRO).toLocalDate() }
        val firstWeek = PayablesCalculator.balances(moves, day, day.plusDays(2)).single()
        assertEquals(d("0.00"), firstWeek.opening)
        assertEquals(d("3200.00"), firstWeek.owed)
        assertEquals(d("1000.00"), firstWeek.paid)
        assertEquals(d("2200.00"), firstWeek.closing)
        val later = PayablesCalculator.balances(moves, day.plusDays(3), day.plusDays(9)).single()
        assertEquals(d("2200.00"), later.opening)
        assertEquals(d("-1000.00"), later.paid)
        assertEquals(d("3200.00"), later.closing)
    }

    @Test
    fun `the manager limit is 5000 EGP inclusive, EUR is valued at today's rate and needs approval without one`() {
        assertFalse(SettlementPolicy.needsApproval(SettlementPolicy.egpEquivalent(PaidCurrency.EGP, d("5000.00"), null)))
        assertTrue(SettlementPolicy.needsApproval(SettlementPolicy.egpEquivalent(PaidCurrency.EGP, d("5000.01"), null)))
        val rate = FxRate(UUID.randomUUID(), day, d("50.0000"), null, now)
        assertEquals(d("5000.00"), SettlementPolicy.egpEquivalent(PaidCurrency.EUR, d("100.00"), rate))
        assertTrue(SettlementPolicy.needsApproval(SettlementPolicy.egpEquivalent(PaidCurrency.EUR, d("100.01"), rate)))
        assertTrue(SettlementPolicy.needsApproval(SettlementPolicy.egpEquivalent(PaidCurrency.EUR, d("1.00"), null)))
        val party = PartyRef(PartyType.DRIVER, UUID.randomUUID())
        assertThrows<IllegalArgumentException> { payment(party, "5000.01") }
    }

    // ── cash box and refunds ─────────────────────────────────────────────────

    @Test
    fun `cash day state follows the latest event and expected nets every reversal`() {
        fun event(
            seq: Int,
            kind: CashBoxEventKind,
        ) = CashBoxEvent(
            UUID.randomUUID(),
            day,
            PaidCurrency.EUR,
            seq,
            kind,
            if (kind == CashBoxEventKind.REOPEN) null else d("10.00"),
            if (kind == CashBoxEventKind.COUNT) d("8.00") else null,
            if (kind == CashBoxEventKind.COUNT) d("-2.00") else null,
            if (kind == CashBoxEventKind.REOPEN) "late cash" else null,
            if (kind == CashBoxEventKind.CONFIRM) UUID.randomUUID() else null,
            null,
            now,
        )
        assertEquals(CashDayState.OPEN, CashBoxEvent.state(emptyList()))
        val count = event(1, CashBoxEventKind.COUNT)
        assertEquals(CashDayState.COUNTED, CashBoxEvent.state(listOf(count)))
        val closed = listOf(count, event(2, CashBoxEventKind.CONFIRM))
        assertEquals(CashDayState.CLOSED, CashBoxEvent.state(closed))
        assertEquals(CashDayState.OPEN, CashBoxEvent.state(closed + event(3, CashBoxEventKind.REOPEN)))
        assertThrows<IllegalArgumentException> {
            CashBoxEvent(UUID.randomUUID(), day, PaidCurrency.EUR, 1, CashBoxEventKind.REOPEN, null, null, null, " ", null, null, now)
        }
        val flow = CashFlow(d("100.00"), d("10.00"), d("30.00"), d("5.00"), d("20.00"), d("20.00"))
        assertEquals(d("65.00"), flow.expected) // 100 − 10 − (30 − 5) − (20 − 20)
    }

    @Test
    fun `refunds net their reversals`() {
        val booking = BookingId(UUID.randomUUID())
        val refund =
            OfficeRefund(
                UUID.randomUUID(),
                booking,
                OfficeRefundKind.REFUND,
                RefundMethod.CASH,
                money("30.00"),
                PaidCurrency.EUR,
                money("30.00"),
                null,
                null,
                "cancelled",
                null,
                null,
                UUID.randomUUID(),
                now,
            )
        val reversal = refund.copy(id = UUID.randomUUID(), kind = OfficeRefundKind.REVERSAL, reversesRefundId = refund.id)
        assertEquals(d("30.00"), OfficeRefund.netRefunded(listOf(refund)))
        assertEquals(d("0.00"), OfficeRefund.netRefunded(listOf(refund, reversal)))
        assertThrows<IllegalArgumentException> { refund.copy(method = RefundMethod.INSTAPAY) }
    }

    companion object {
        private val CAIRO: ZoneId = ZoneId.of("Africa/Cairo")
    }
}
