package com.wego.divers

import com.wego.divers.domain.Booking
import com.wego.divers.domain.BookingFingerprint
import com.wego.divers.domain.BookingId
import com.wego.divers.domain.BookingPricing
import com.wego.divers.domain.BookingStatus
import com.wego.divers.domain.CustomerContact
import com.wego.divers.domain.Money
import com.wego.divers.domain.Offering
import com.wego.divers.domain.OfferingId
import com.wego.divers.domain.OfferingType
import com.wego.divers.domain.PaymentTransitionResult
import com.wego.divers.domain.PricingBasis
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class MoneyTest {
    @Test
    fun `rejects a negative amount`() {
        assertThatIllegalArgumentException().isThrownBy { Money(BigDecimal("-1.00"), "EUR") }
    }

    @Test
    fun `rejects a currency code that is not a 3-letter uppercase code`() {
        assertThatIllegalArgumentException().isThrownBy { Money(BigDecimal("10.00"), "eur") }
        assertThatIllegalArgumentException().isThrownBy { Money(BigDecimal("10.00"), "EURO") }
    }

    @Test
    fun `rejects an amount that is not scaled to exactly 2 decimal places`() {
        assertThatIllegalArgumentException().isThrownBy { Money(BigDecimal("10"), "EUR") }
        assertThatIllegalArgumentException().isThrownBy { Money(BigDecimal("10.5"), "EUR") }
        assertThatIllegalArgumentException().isThrownBy { Money(BigDecimal("10.123"), "EUR") }
    }

    @Test
    fun `accepts a zero amount and a valid code`() {
        assertThat(Money(BigDecimal("0.00"), "EUR").amount).isEqualByComparingTo(BigDecimal.ZERO)
    }
}

class CustomerContactTest {
    @Test
    fun `rejects a blank name`() {
        assertThatIllegalArgumentException().isThrownBy { CustomerContact("   ", "a@example.com", null) }
    }

    @Test
    fun `rejects a contact with neither email nor phone`() {
        assertThatIllegalArgumentException().isThrownBy { CustomerContact("Ada", null, null) }
    }

    @Test
    fun `accepts a contact with only a phone number`() {
        assertThat(CustomerContact("Ada", null, "+201066461010").phone).isEqualTo("+201066461010")
    }
}

class BookingFingerprintTest {
    private val offeringId = OfferingId.generate()

    @Test
    fun `is identical for the exact same request`() {
        val customer = CustomerContact("Ada Lovelace", "ada@example.com", null)
        val first = BookingFingerprint.of(offeringId, 2, customer)
        val second = BookingFingerprint.of(offeringId, 2, customer)
        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `is identical regardless of customer name case and incidental whitespace`() {
        val a = BookingFingerprint.of(offeringId, 2, CustomerContact("Ada Lovelace", "ada@example.com", null))
        val b = BookingFingerprint.of(offeringId, 2, CustomerContact("  ADA   LOVELACE ", "ada@example.com", null))
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `is identical regardless of phone formatting punctuation`() {
        val a = BookingFingerprint.of(offeringId, 2, CustomerContact("Ada", null, "+201066461010"))
        val b = BookingFingerprint.of(offeringId, 2, CustomerContact("Ada", null, "+20 10 664 6101 0"))
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `differs when party size differs`() {
        val customer = CustomerContact("Ada Lovelace", "ada@example.com", null)
        assertThat(BookingFingerprint.of(offeringId, 1, customer))
            .isNotEqualTo(BookingFingerprint.of(offeringId, 2, customer))
    }

    @Test
    fun `differs when the offering differs`() {
        val customer = CustomerContact("Ada Lovelace", "ada@example.com", null)
        assertThat(BookingFingerprint.of(offeringId, 2, customer))
            .isNotEqualTo(BookingFingerprint.of(OfferingId.generate(), 2, customer))
    }

    @Test
    fun `differs when the customer email differs`() {
        assertThat(BookingFingerprint.of(offeringId, 2, CustomerContact("Ada", "ada@example.com", null)))
            .isNotEqualTo(BookingFingerprint.of(offeringId, 2, CustomerContact("Ada", "grace@example.com", null)))
    }

    @Test
    fun `is a 64-character lowercase hex digest`() {
        val fingerprint = BookingFingerprint.of(offeringId, 1, CustomerContact("Ada", "ada@example.com", null))
        assertThat(fingerprint).matches("^[a-f0-9]{64}$")
    }
}

class BookingPricingTest {
    private fun offering(
        pricingBasis: PricingBasis,
        unitPrice: String,
    ) = Offering.create(
        id = OfferingId.generate(),
        offeringType = OfferingType.DIVE_TRIP,
        title = "Reef Trip",
        description = null,
        startsOn = LocalDate.parse("2026-08-20"),
        endsOn = null,
        capacity = null,
        pricingBasis = pricingBasis,
        unitPrice = Money(BigDecimal(unitPrice), "EUR"),
        createdByUserId = null,
        now = Instant.parse("2026-08-15T00:00:00Z"),
    )

    @Test
    fun `PER_PARTICIPANT multiplies unit price by party size`() {
        val pricing = BookingPricing.forOffering(offering(PricingBasis.PER_PARTICIPANT, "45.00"), 3)
        assertThat(pricing.billableQuantity).isEqualTo(3)
        assertThat(pricing.totalPrice.amount).isEqualByComparingTo(BigDecimal("135.00"))
    }

    @Test
    fun `FLAT ignores party size — billable quantity is always 1`() {
        val pricing = BookingPricing.forOffering(offering(PricingBasis.FLAT, "500.00"), 6)
        assertThat(pricing.billableQuantity).isEqualTo(1)
        assertThat(pricing.totalPrice.amount).isEqualByComparingTo(BigDecimal("500.00"))
    }

    @Test
    fun `preserves the 2-decimal scale, including trailing zeros`() {
        val pricing = BookingPricing.forOffering(offering(PricingBasis.PER_PARTICIPANT, "10.00"), 2)
        assertThat(pricing.totalPrice.amount.toPlainString()).isEqualTo("20.00")
    }

    @Test
    fun `rejects a total that does not match unit price times billable quantity`() {
        assertThatIllegalArgumentException().isThrownBy {
            BookingPricing(
                pricingBasis = PricingBasis.PER_PARTICIPANT,
                unitPrice = Money(BigDecimal("45.00"), "EUR"),
                billableQuantity = 2,
                totalPrice = Money(BigDecimal("100.00"), "EUR"),
            )
        }
    }

    @Test
    fun `rejects mismatched currencies between unit and total price`() {
        assertThatIllegalArgumentException().isThrownBy {
            BookingPricing(
                pricingBasis = PricingBasis.FLAT,
                unitPrice = Money(BigDecimal("45.00"), "EUR"),
                billableQuantity = 1,
                totalPrice = Money(BigDecimal("45.00"), "USD"),
            )
        }
    }
}

class OfferingTest {
    private val now = Instant.parse("2026-08-15T00:00:00Z")

    private fun validOffering(
        title: String = "Reef Trip",
        endsOn: LocalDate? = null,
        capacity: Int? = 10,
    ) = Offering.create(
        id = OfferingId.generate(),
        offeringType = OfferingType.DIVE_TRIP,
        title = title,
        description = null,
        startsOn = LocalDate.parse("2026-08-20"),
        endsOn = endsOn,
        capacity = capacity,
        pricingBasis = PricingBasis.PER_PARTICIPANT,
        unitPrice = Money(BigDecimal("45.00"), "EUR"),
        createdByUserId = UUID.randomUUID(),
        now = now,
    )

    @Test
    fun `rejects a blank title`() {
        assertThatIllegalArgumentException().isThrownBy { validOffering(title = "  ") }
    }

    @Test
    fun `rejects an end date before the start date`() {
        assertThatIllegalArgumentException().isThrownBy { validOffering(endsOn = LocalDate.parse("2026-08-19")) }
    }

    @Test
    fun `rejects a non-positive capacity`() {
        assertThatIllegalArgumentException().isThrownBy { validOffering(capacity = 0) }
    }

    @Test
    fun `a freshly created offering is active and not closed`() {
        val offering = validOffering()
        assertThat(offering.isActive).isTrue()
        assertThat(offering.closedAt).isNull()
    }

    @Test
    fun `closing moves it to CLOSED and stamps closedAt`() {
        val offering = validOffering()
        offering.close(now.plusSeconds(60))
        assertThat(offering.isActive).isFalse()
        assertThat(offering.closedAt).isEqualTo(now.plusSeconds(60))
    }

    @Test
    fun `closing an already-closed offering fails`() {
        val offering = validOffering()
        offering.close(now)
        assertThatIllegalArgumentException().isThrownBy { offering.close(now.plusSeconds(1)) }
    }
}

class BookingTest {
    private val now = Instant.parse("2026-08-15T00:00:00Z")

    private fun pricing() =
        BookingPricing(
            pricingBasis = PricingBasis.PER_PARTICIPANT,
            unitPrice = Money(BigDecimal("45.00"), "EUR"),
            billableQuantity = 2,
            totalPrice = Money(BigDecimal("90.00"), "EUR"),
        )

    private fun confirmedBooking(): Booking =
        Booking.confirm(
            id = BookingId.generate(),
            offeringId = OfferingId.generate(),
            partySize = 2,
            customer = CustomerContact("Ada Lovelace", "ada@example.com", null),
            pricing = pricing(),
            idempotencyKey = "key-1",
            idempotencyFingerprint = "a".repeat(64),
            createdByUserId = UUID.randomUUID(),
            now = now,
        )

    @Test
    fun `a confirmed booking is unpaid and not cancelled`() {
        val booking = confirmedBooking()
        assertThat(booking.status).isEqualTo(BookingStatus.CONFIRMED)
        assertThat(booking.paymentStatus.name).isEqualTo("UNPAID")
        assertThat(booking.cancelledAt).isNull()
        assertThat(booking.cancellationReason).isNull()
    }

    @Test
    fun `rejects a non-positive party size`() {
        assertThatIllegalArgumentException().isThrownBy {
            Booking.confirm(
                id = BookingId.generate(),
                offeringId = OfferingId.generate(),
                partySize = 0,
                customer = CustomerContact("Ada", "ada@example.com", null),
                pricing = pricing(),
                idempotencyKey = "key-1",
                idempotencyFingerprint = "a".repeat(64),
                createdByUserId = UUID.randomUUID(),
                now = now,
            )
        }
    }

    @Test
    fun `rejects an idempotency key over the maximum length`() {
        assertThatIllegalArgumentException().isThrownBy {
            Booking.confirm(
                id = BookingId.generate(),
                offeringId = OfferingId.generate(),
                partySize = 1,
                customer = CustomerContact("Ada", "ada@example.com", null),
                pricing = pricing(),
                idempotencyKey = "k".repeat(129),
                idempotencyFingerprint = "a".repeat(64),
                createdByUserId = UUID.randomUUID(),
                now = now,
            )
        }
    }

    @Test
    fun `rejects a fingerprint that is not a 64-character hex digest`() {
        assertThatIllegalArgumentException().isThrownBy {
            Booking.confirm(
                id = BookingId.generate(),
                offeringId = OfferingId.generate(),
                partySize = 1,
                customer = CustomerContact("Ada", "ada@example.com", null),
                pricing = pricing(),
                idempotencyKey = "key-1",
                idempotencyFingerprint = "not-a-real-fingerprint",
                createdByUserId = UUID.randomUUID(),
                now = now,
            )
        }
    }

    @Test
    fun `cancelling moves it to CANCELLED, stamps cancelledAt, and stores the reason`() {
        val booking = confirmedBooking()
        booking.cancel(now.plusSeconds(60), "Customer requested cancellation")
        assertThat(booking.status).isEqualTo(BookingStatus.CANCELLED)
        assertThat(booking.cancelledAt).isEqualTo(now.plusSeconds(60))
        assertThat(booking.cancellationReason).isEqualTo("Customer requested cancellation")
    }

    @Test
    fun `cancelling requires a non-blank reason`() {
        val booking = confirmedBooking()
        assertThatIllegalArgumentException().isThrownBy { booking.cancel(now, "   ") }
    }

    @Test
    fun `cancelling an already-cancelled booking fails`() {
        val booking = confirmedBooking()
        booking.cancel(now, "First cancellation")
        assertThatIllegalArgumentException().isThrownBy { booking.cancel(now.plusSeconds(1), "Second attempt") }
    }

    @Test
    fun `markPaid moves UNPAID to PAID`() {
        val booking = confirmedBooking()
        val result = booking.markPaid()
        assertThat(result).isEqualTo(PaymentTransitionResult.Applied)
        assertThat(booking.paymentStatus.name).isEqualTo("PAID")
    }

    @Test
    fun `markPaid on an already-paid booking is a safe replay`() {
        val booking = confirmedBooking()
        booking.markPaid()
        assertThat(booking.markPaid()).isEqualTo(PaymentTransitionResult.AlreadyInTargetState)
    }

    @Test
    fun `markPaid on a refunded booking is rejected`() {
        val booking = confirmedBooking()
        booking.markPaid()
        booking.refund("Refunded")
        assertThat(booking.markPaid()).isInstanceOf(PaymentTransitionResult.Rejected::class.java)
    }

    @Test
    fun `markPaid on a cancelled booking is rejected even if it was never paid`() {
        val booking = confirmedBooking()
        booking.cancel(now, "Cancelled before payment")
        assertThat(booking.markPaid()).isInstanceOf(PaymentTransitionResult.Rejected::class.java)
    }

    @Test
    fun `refund moves PAID to REFUNDED`() {
        val booking = confirmedBooking()
        booking.markPaid()
        val result = booking.refund("Customer requested a refund")
        assertThat(result).isEqualTo(PaymentTransitionResult.Applied)
        assertThat(booking.paymentStatus.name).isEqualTo("REFUNDED")
    }

    @Test
    fun `refund on an unpaid booking is rejected`() {
        val booking = confirmedBooking()
        assertThat(booking.refund("Attempted refund")).isInstanceOf(PaymentTransitionResult.Rejected::class.java)
    }

    @Test
    fun `refund on an already-refunded booking is a safe replay`() {
        val booking = confirmedBooking()
        booking.markPaid()
        booking.refund("First refund")
        assertThat(booking.refund("Second attempt")).isEqualTo(PaymentTransitionResult.AlreadyInTargetState)
    }

    @Test
    fun `refund on a cancelled-but-paid booking is allowed`() {
        val booking = confirmedBooking()
        booking.markPaid()
        booking.cancel(now, "Weather")
        assertThat(booking.refund("Refund after weather cancellation")).isEqualTo(PaymentTransitionResult.Applied)
    }

    @Test
    fun `refund requires a non-blank reason`() {
        val booking = confirmedBooking()
        booking.markPaid()
        assertThatIllegalArgumentException().isThrownBy { booking.refund("   ") }
    }
}
