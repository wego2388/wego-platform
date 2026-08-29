package com.wego.divers

import com.wego.divers.application.CancelBookingResult
import com.wego.divers.application.CancelBookingService
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

class CancelBookingServiceTest {
    private lateinit var bookingRepository: InMemoryBookingRepository
    private lateinit var auditRecorder: RecordingBookingAuditRecorder
    private lateinit var outboxWriter: RecordingOutboxWriter
    private lateinit var service: CancelBookingService

    private val fixedInstant = Instant.parse("2026-08-15T00:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val actorId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        bookingRepository = InMemoryBookingRepository()
        auditRecorder = RecordingBookingAuditRecorder()
        outboxWriter = RecordingOutboxWriter()
        service =
            CancelBookingService(
                bookingRepository,
                auditRecorder,
                outboxWriter,
                NoOpTransactionRunner(),
                ObjectMapper(),
                clock,
            )
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
    fun `cancels a confirmed booking, records an audit event, and writes an outbox event`() {
        val booking = seedBooking()

        val result = service.cancel(booking.id, actorId, "Customer requested cancellation", null)

        assertThat(result).isInstanceOf(CancelBookingResult.Cancelled::class.java)
        val cancelled = (result as CancelBookingResult.Cancelled).booking
        assertThat(cancelled.status.name).isEqualTo("CANCELLED")
        assertThat(cancelled.cancellationReason).isEqualTo("Customer requested cancellation")
        assertThat(auditRecorder.cancelled).containsExactly(booking.id to "Customer requested cancellation")
        assertThat(outboxWriter.written.single().eventType).isEqualTo("booking.cancelled")
    }

    @Test
    fun `rejects a blank reason`() {
        val booking = seedBooking()
        assertThatIllegalArgumentException().isThrownBy { service.cancel(booking.id, actorId, "   ", null) }
    }

    @Test
    fun `cancelling twice fails the second time`() {
        val booking = seedBooking()
        service.cancel(booking.id, actorId, "First cancellation", null)

        val result = service.cancel(booking.id, actorId, "Second attempt", null)

        assertThat(result).isEqualTo(CancelBookingResult.AlreadyCancelled)
        assertThat(outboxWriter.written).hasSize(1)
    }

    @Test
    fun `cancelling a booking that does not exist fails`() {
        val result = service.cancel(BookingId.generate(), actorId, "reason", null)
        assertThat(result).isEqualTo(CancelBookingResult.NotFound)
    }
}
