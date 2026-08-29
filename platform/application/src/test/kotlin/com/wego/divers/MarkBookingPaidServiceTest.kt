package com.wego.divers

import com.wego.divers.application.MarkBookingPaidResult
import com.wego.divers.application.MarkBookingPaidService
import com.wego.divers.domain.Booking
import com.wego.divers.domain.BookingId
import com.wego.divers.domain.BookingPricing
import com.wego.divers.domain.CustomerContact
import com.wego.divers.domain.Money
import com.wego.divers.domain.OfferingId
import com.wego.divers.domain.PricingBasis
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class MarkBookingPaidServiceTest {
    private lateinit var bookingRepository: InMemoryBookingRepository
    private lateinit var auditRecorder: RecordingBookingAuditRecorder
    private lateinit var outboxWriter: RecordingOutboxWriter
    private lateinit var service: MarkBookingPaidService

    private val fixedInstant = Instant.parse("2026-08-15T00:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val actorId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        bookingRepository = InMemoryBookingRepository()
        auditRecorder = RecordingBookingAuditRecorder()
        outboxWriter = RecordingOutboxWriter()
        service = MarkBookingPaidService(bookingRepository, auditRecorder, outboxWriter, NoOpTransactionRunner(), ObjectMapper(), clock)
    }

    private fun seedBooking(): Booking {
        val booking =
            Booking.confirm(
                id = BookingId.generate(),
                offeringId = OfferingId.generate(),
                partySize = 2,
                customer = CustomerContact("Ada Lovelace", "ada@example.com", null),
                pricing =
                    BookingPricing(
                        pricingBasis = PricingBasis.PER_PARTICIPANT,
                        unitPrice = Money(BigDecimal("45.00"), "EUR"),
                        billableQuantity = 2,
                        totalPrice = Money(BigDecimal("90.00"), "EUR"),
                    ),
                idempotencyKey = "key-1",
                idempotencyFingerprint = "a".repeat(64),
                createdByUserId = actorId,
                now = fixedInstant,
            )
        bookingRepository.save(booking)
        return booking
    }

    @Test
    fun `moves an unpaid booking to PAID, records an audit event, and writes an outbox event`() {
        val booking = seedBooking()

        val result = service.markPaid(booking.id, actorId, null)

        assertThat(result).isInstanceOf(MarkBookingPaidResult.Ok::class.java)
        assertThat((result as MarkBookingPaidResult.Ok).booking.paymentStatus.name).isEqualTo("PAID")
        assertThat(auditRecorder.markedPaid).containsExactly(booking.id)
        assertThat(outboxWriter.written.single().eventType).isEqualTo("booking.payment_marked_paid")
    }

    @Test
    fun `marking an already-paid booking paid is a safe replay with no duplicate audit or outbox`() {
        val booking = seedBooking()
        service.markPaid(booking.id, actorId, null)

        val result = service.markPaid(booking.id, actorId, null)

        assertThat(result).isInstanceOf(MarkBookingPaidResult.Ok::class.java)
        assertThat(auditRecorder.markedPaid).hasSize(1)
        assertThat(outboxWriter.written).hasSize(1)
    }

    @Test
    fun `rejects marking a cancelled booking paid`() {
        val booking = seedBooking()
        booking.cancel(fixedInstant, "Customer requested cancellation")
        bookingRepository.save(booking)

        val result = service.markPaid(booking.id, actorId, null)

        assertThat(result).isInstanceOf(MarkBookingPaidResult.Rejected::class.java)
        assertThat(auditRecorder.markedPaid).isEmpty()
    }

    @Test
    fun `rejects marking a refunded booking paid again`() {
        val booking = seedBooking()
        booking.markPaid()
        booking.refund("Duplicate charge")
        bookingRepository.save(booking)

        val result = service.markPaid(booking.id, actorId, null)

        assertThat(result).isInstanceOf(MarkBookingPaidResult.Rejected::class.java)
    }

    @Test
    fun `marking a booking that does not exist fails`() {
        val result = service.markPaid(BookingId.generate(), actorId, null)
        assertThat(result).isEqualTo(MarkBookingPaidResult.NotFound)
    }
}
