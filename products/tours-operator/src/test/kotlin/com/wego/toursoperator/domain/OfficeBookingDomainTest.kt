package com.wego.toursoperator.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class OfficeBookingDomainTest {
    private val now: Instant = Instant.parse("2026-10-05T10:00:00Z")

    private fun money(value: String) = Money(BigDecimal(value))

    private fun office(): Booking =
        Booking.createOffice(
            id = BookingId(UUID.randomUUID()),
            reference = "STR-2026-7",
            tourId = TourId(UUID.randomUUID()),
            slotId = TourSlotId(UUID.randomUUID()),
            tourDate = LocalDate.of(2027, 6, 15),
            timeSlot = TimeSlot.MORNING,
            pricing = BookingPricing.compute(2, 1, money("35.00"), money("17.50")),
            customer = CustomerContact("Ahmed Hassan", "+201234567890", "EG", null),
            hotelName = "Hilton",
            hotelRoom = null,
            specialRequests = null,
            locale = "en",
            now = now,
            createdByUserId = UUID.randomUUID(),
            clientRequestId = UUID.randomUUID(),
        )

    @Test
    fun `an office booking starts NEW and awaiting collection`() {
        val b = office()
        assertEquals(BookingChannel.OFFICE, b.channel)
        assertEquals(BookingStatus.NEW, b.status)
        assertTrue(b.isAwaitingCollection)
    }

    @Test
    fun `an office booking never expires because the payment window is online-only`() {
        val b = office()
        assertThrows<IllegalArgumentException> { b.expire(now.plusSeconds(7200)) }
        assertEquals(BookingStatus.NEW, b.status)
    }

    @Test
    fun `staff can cancel an office booking and it stops awaiting collection`() {
        val b = office()
        b.cancel(now, "Customer called")
        assertEquals(BookingStatus.CANCELLED, b.status)
        assertFalse(b.isAwaitingCollection)
    }

    @Test
    fun `office fields are consistent`() {
        assertThrows<IllegalArgumentException> {
            Booking(
                id = BookingId(UUID.randomUUID()),
                reference = "STR-2026-8",
                tourId = TourId(UUID.randomUUID()),
                slotId = TourSlotId(UUID.randomUUID()),
                tourDate = LocalDate.of(2027, 6, 15),
                timeSlot = TimeSlot.MORNING,
                pricing = BookingPricing.compute(1, 0, money("35.00"), null),
                customer = CustomerContact("A B", "+2012", "EG", null),
                hotelName = "H",
                hotelRoom = null,
                specialRequests = null,
                locale = "en",
                status = BookingStatus.NEW,
                createdAt = now,
                confirmedAt = null,
                cancelledAt = null,
                cancellationReason = null,
                completedAt = null,
                expiredAt = null,
                channel = BookingChannel.OFFICE,
                createdByUserId = null,
                clientRequestId = null,
            )
        }
    }

    // ── payment state derived from the ledger ────────────────────────────────

    private fun entry(
        kind: OfficeCollectionKind,
        eur: String,
        method: CollectionMethod = CollectionMethod.CASH_AT_OFFICE,
    ) = OfficeCollection(
        id = UUID.randomUUID(),
        bookingId = BookingId(UUID.randomUUID()),
        kind = kind,
        method = method,
        amount = money(eur),
        currencyPaid = PaidCurrency.EUR,
        amountPaid = money(eur),
        fxRate = null,
        reference = if (method.isCash) null else "RCPT-1",
        reversesCollectionId = if (kind == OfficeCollectionKind.REVERSAL) UUID.randomUUID() else null,
        reason = if (kind == OfficeCollectionKind.REVERSAL) "mistake" else null,
        recordedByUserId = UUID.randomUUID(),
        clientRequestId = UUID.randomUUID(),
        recordedAt = now,
    ).let { if (kind == OfficeCollectionKind.REVERSAL) it.copy(reference = null) else it }

    @Test
    fun `payment state follows the collections and reversals`() {
        val total = money("87.50")
        assertEquals(OfficePaymentState.UNPAID, OfficePaymentSummary.of(total, emptyList()).state)

        val deposit = entry(OfficeCollectionKind.COLLECTION, "20.00")
        val part = OfficePaymentSummary.of(total, listOf(deposit))
        assertEquals(OfficePaymentState.PARTIALLY_PAID, part.state)
        assertEquals(money("67.50"), part.outstanding)

        val paid = OfficePaymentSummary.of(total, listOf(deposit, entry(OfficeCollectionKind.COLLECTION, "67.50", CollectionMethod.INSTAPAY)))
        assertEquals(OfficePaymentState.PAID, paid.state)
        assertEquals(money("0.00"), paid.outstanding)

        val reversed = OfficePaymentSummary.of(total, listOf(deposit, entry(OfficeCollectionKind.REVERSAL, "20.00")))
        assertEquals(OfficePaymentState.UNPAID, reversed.state)
    }

    @Test
    fun `collected can never exceed the total`() {
        assertThrows<IllegalArgumentException> {
            OfficePaymentSummary.of(money("10.00"), listOf(entry(OfficeCollectionKind.COLLECTION, "10.01")))
        }
    }

    @Test
    fun `non-cash needs a reference and cash carries none`() {
        fun build(
            method: CollectionMethod,
            reference: String?,
        ) = OfficeCollection(
            UUID.randomUUID(), BookingId(UUID.randomUUID()), OfficeCollectionKind.COLLECTION, method,
            money("5.00"), PaidCurrency.EUR, money("5.00"), null, reference, null, null, null, UUID.randomUUID(), now,
        )
        assertThrows<IllegalArgumentException> { build(CollectionMethod.FAWRY_OFFICE, null) }
        assertThrows<IllegalArgumentException> { build(CollectionMethod.CASH_ON_PICKUP, "X") }
        build(CollectionMethod.FAWRY_OFFICE, "F-1")
        build(CollectionMethod.CASH_ON_PICKUP, null)
    }

    // ── reference rules ──────────────────────────────────────────────────────

    @Test
    fun `reference is trimmed bounded and free of control characters`() {
        assertEquals("AB-123", CollectionReference.of("  AB-123 \t".trim()))
        assertEquals("AB-123", CollectionReference.of("  AB-123  "))
        assertEquals(64, CollectionReference.of("x".repeat(64)).length)
        assertThrows<IllegalArgumentException> { CollectionReference.of("x".repeat(65)) }
        assertThrows<IllegalArgumentException> { CollectionReference.of("   ") }
        assertThrows<IllegalArgumentException> { CollectionReference.of("AB\u0000C") }
        assertThrows<IllegalArgumentException> { CollectionReference.of("AB\nC") }
        // The message never echoes the value.
        val ex = assertThrows<IllegalArgumentException> { CollectionReference.of("SECRET\u0007") }
        assertFalse(ex.message!!.contains("SECRET"))
    }

    // ── EGP settlement and rounding ──────────────────────────────────────────

    private fun rate(value: String) = FxRate(UUID.randomUUID(), LocalDate.of(2026, 10, 5), BigDecimal(value), null, now)

    private fun settle(
        paid: String,
        currency: PaidCurrency,
        rate: FxRate?,
        outstanding: String,
    ) = Settlement.of(money(paid), currency, rate, money(outstanding))

    @Test
    fun `EUR settles one to one and cannot exceed the balance`() {
        assertEquals(Settlement.Settled(money("10.00"), null), settle("10.00", PaidCurrency.EUR, null, "87.50"))
        assertEquals(Settlement.ExceedsOutstanding(money("87.50")), settle("87.51", PaidCurrency.EUR, null, "87.50"))
    }

    @Test
    fun `EGP needs a rate`() {
        assertEquals(Settlement.RateMissing, settle("100.00", PaidCurrency.EGP, null, "87.50"))
    }

    @Test
    fun `EGP converts half-up to cents`() {
        val r = rate("4.0000")
        // 0.02 / 4 = 0.005 → 0.01 (half-up, not banker's)
        assertEquals(money("0.01"), (settle("0.02", PaidCurrency.EGP, r, "87.50") as Settlement.Settled).eur)
        // 0.06 / 4 = 0.015 → 0.02
        assertEquals(money("0.02"), (settle("0.06", PaidCurrency.EGP, r, "87.50") as Settlement.Settled).eur)
        val r2 = rate("48.5000")
        // 100 / 48.5 = 2.0618… → 2.06 ; 105 / 48.5 = 2.1649… → 2.16 ; 120 / 48.5 = 2.4742… → 2.47
        assertEquals(money("2.06"), (settle("100.00", PaidCurrency.EGP, r2, "87.50") as Settlement.Settled).eur)
        assertEquals(money("2.16"), (settle("105.00", PaidCurrency.EGP, r2, "87.50") as Settlement.Settled).eur)
        assertEquals(money("2.47"), (settle("120.00", PaidCurrency.EGP, r2, "87.50") as Settlement.Settled).eur)
    }

    @Test
    fun `an EGP amount worth less than a cent is refused`() {
        assertEquals(Settlement.TooSmall, settle("0.20", PaidCurrency.EGP, rate("50.0000"), "87.50"))
    }

    @Test
    fun `EGP can never settle more than the outstanding balance`() {
        val r = rate("50.0000")
        assertEquals(Settlement.ExceedsOutstanding(money("87.50")), settle("4376.00", PaidCurrency.EGP, r, "87.50"))
        // 4375.01 / 50 = 87.5002 → 87.50: within the balance, accepted.
        assertEquals(money("87.50"), (settle("4375.01", PaidCurrency.EGP, r, "87.50") as Settlement.Settled).eur)
    }

    @Test
    fun `the exact EGP value of the balance settles it exactly`() {
        for ((out, rt) in listOf("87.50" to "50.0000", "10.01" to "48.3333", "0.01" to "49.5000", "123.45" to "52.1234", "5.00" to "47.3333")) {
            val r = rate(rt)
            val due = BigDecimal(out).multiply(r.egpPerEur).setScale(2, java.math.RoundingMode.HALF_UP)
            val s = Settlement.of(Money(due), PaidCurrency.EGP, r, money(out))
            assertEquals(money(out), (s as Settlement.Settled).eur, "paying $due EGP at $rt must settle $out EUR exactly")
        }
    }
}
