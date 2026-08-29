package com.wego.divers

import com.wego.divers.application.RefundBookingResult
import com.wego.divers.application.RefundBookingService
import com.wego.divers.domain.Booking
import com.wego.divers.domain.BookingId
import com.wego.divers.domain.BookingPricing
import com.wego.divers.domain.CustomerContact
import com.wego.divers.domain.Money
import com.wego.divers.domain.OfferingId
import com.wego.divers.domain.PricingBasis
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class RefundBookingServiceTest {
    private lateinit var bookingRepository: InMemoryBookingRepository
    private lateinit var auditRecorder: RecordingBookingAuditRecorder
    private lateinit var outboxWriter: RecordingOutboxWriter
    private lateinit var service: RefundBookingService

    private val fixedInstant = Instant.parse("2026-08-15T00:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val actorId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        bookingRepository = InMemoryBookingRepository()
        auditRecorder = RecordingBookingAuditRecorder()
        outboxWriter = RecordingOutboxWriter()
        service = RefundBookingService(bookingRepository, auditRecorder, outboxWriter, NoOpTransactionRunner(), ObjectMapper(), clock)
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
    fun `refunds a paid booking, records an audit event, and writes an outbox event`() {
        val booking = seedBooking()
        booking.markPaid()
        bookingRepository.save(booking)

        val result = service.refund(booking.id, actorId, "Customer requested a refund", null)

        assertThat(result).isInstanceOf(RefundBookingResult.Ok::class.java)
        assertThat((result as RefundBookingResult.Ok).booking.paymentStatus.name).isEqualTo("REFUNDED")
        assertThat(auditRecorder.refunded).containsExactly(booking.id to "Customer requested a refund")
        assertThat(outboxWriter.written.single().eventType).isEqualTo("booking.payment_refunded")
    }

    @Test
    fun `refunds a cancelled-but-paid booking — cancellation and payment are independent`() {
        val booking = seedBooking()
        booking.markPaid()
        booking.cancel(fixedInstant, "Weather")
        bookingRepository.save(booking)

        val result = service.refund(booking.id, actorId, "Refund after weather cancellation", null)

        assertThat(result).isInstanceOf(RefundBookingResult.Ok::class.java)
    }

    @Test
    fun `rejects refunding an unpaid booking`() {
        val booking = seedBooking()

        val result = service.refund(booking.id, actorId, "Attempted refund", null)

        assertThat(result).isInstanceOf(RefundBookingResult.Rejected::class.java)
        assertThat(auditRecorder.refunded).isEmpty()
    }

    @Test
    fun `refunding an already-refunded booking is a safe replay with no duplicate audit or outbox`() {
        val booking = seedBooking()
        booking.markPaid()
        bookingRepository.save(booking)
        service.refund(booking.id, actorId, "First refund", null)

        val result = service.refund(booking.id, actorId, "Second attempt", null)

        assertThat(result).isInstanceOf(RefundBookingResult.Ok::class.java)
        assertThat(auditRecorder.refunded).hasSize(1)
        assertThat(outboxWriter.written).hasSize(1)
    }

    @Test
    fun `rejects a blank reason`() {
        val booking = seedBooking()
        booking.markPaid()
        bookingRepository.save(booking)

        assertThatIllegalArgumentException().isThrownBy { service.refund(booking.id, actorId, "   ", null) }
    }

    @Test
    fun `refunding a booking that does not exist fails`() {
        val result = service.refund(BookingId.generate(), actorId, "reason", null)
        assertThat(result).isEqualTo(RefundBookingResult.NotFound)
    }
}
