package com.wego.divers

import com.wego.divers.application.CreateBookingCommand
import com.wego.divers.application.CreateBookingResult
import com.wego.divers.application.CreateBookingService
import com.wego.divers.domain.CustomerContact
import com.wego.divers.domain.Money
import com.wego.divers.domain.Offering
import com.wego.divers.domain.OfferingId
import com.wego.divers.domain.OfferingType
import com.wego.divers.domain.PricingBasis
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class CreateBookingServiceTest {
    private lateinit var offeringRepository: InMemoryOfferingRepository
    private lateinit var bookingRepository: InMemoryBookingRepository
    private lateinit var auditRecorder: RecordingBookingAuditRecorder
    private lateinit var outboxWriter: RecordingOutboxWriter
    private lateinit var service: CreateBookingService

    private val fixedInstant = Instant.parse("2026-08-15T00:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val actorId = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        offeringRepository = InMemoryOfferingRepository()
        bookingRepository = InMemoryBookingRepository()
        auditRecorder = RecordingBookingAuditRecorder()
        outboxWriter = RecordingOutboxWriter()
        service =
            CreateBookingService(
                offeringRepository,
                bookingRepository,
                auditRecorder,
                outboxWriter,
                NoOpTransactionRunner(),
                ObjectMapper(),
                clock,
            )
    }

    private fun seedOffering(capacity: Int? = 5): Offering {
        val offering =
            Offering.create(
                id = OfferingId.generate(),
                offeringType = OfferingType.DIVE_TRIP,
                title = "Reef Trip",
                description = null,
                startsOn = LocalDate.parse("2026-08-20"),
                endsOn = null,
                capacity = capacity,
                pricingBasis = PricingBasis.PER_PARTICIPANT,
                unitPrice = Money(BigDecimal("45.00"), "EUR"),
                createdByUserId = null,
                now = fixedInstant,
            )
        offeringRepository.save(offering)
        return offering
    }

    private fun command(
        offeringId: OfferingId,
        partySize: Int = 1,
        idempotencyKey: String = "key-1",
        customerName: String = "Ada Lovelace",
        customerEmail: String = "ada@example.com",
    ) = CreateBookingCommand(
        offeringId = offeringId,
        partySize = partySize,
        customer = CustomerContact(customerName, customerEmail, null),
        idempotencyKey = idempotencyKey,
        createdByUserId = actorId,
        correlationId = null,
    )

    @Test
    fun `creates and confirms a booking, records an audit event, and writes an outbox event`() {
        val offering = seedOffering()

        val result = service.create(command(offering.id))

        assertThat(result).isInstanceOf(CreateBookingResult.Created::class.java)
        val booking = (result as CreateBookingResult.Created).booking
        assertThat(booking.status.name).isEqualTo("CONFIRMED")
        assertThat(booking.pricing.totalPrice.amount).isEqualByComparingTo(BigDecimal("45.00"))
        assertThat(auditRecorder.created).containsExactly(booking.id)
        assertThat(outboxWriter.written).hasSize(1)
        assertThat(outboxWriter.written.single().eventType).isEqualTo("booking.created")
    }

    @Test
    fun `a retry with the same idempotency key and same parameters returns the original booking unchanged`() {
        val offering = seedOffering()

        val first = service.create(command(offering.id)) as CreateBookingResult.Created
        val second = service.create(command(offering.id)) as CreateBookingResult.Replayed

        assertThat(second.booking.id).isEqualTo(first.booking.id)
        assertThat(bookingRepository.byId).hasSize(1)
        assertThat(outboxWriter.written).hasSize(1)
    }

    @Test
    fun `a different idempotency key against the same offering creates a second booking`() {
        val offering = seedOffering()

        service.create(command(offering.id, idempotencyKey = "key-1"))
        service.create(command(offering.id, idempotencyKey = "key-2"))

        assertThat(bookingRepository.byId).hasSize(2)
    }

    @Test
    fun `the same idempotency key with a different offering is a conflict, not a silent replay`() {
        val firstOffering = seedOffering()
        val secondOffering = seedOffering()
        service.create(command(firstOffering.id, idempotencyKey = "shared-key"))

        val result = service.create(command(secondOffering.id, idempotencyKey = "shared-key"))

        assertThat(result).isEqualTo(CreateBookingResult.IdempotencyKeyConflict)
        assertThat(bookingRepository.byId).hasSize(1)
        val storedBooking = bookingRepository.byId.values.single()
        assertThat(storedBooking.offeringId).isEqualTo(firstOffering.id)
    }

    @Test
    fun `the same idempotency key with a different party size is a conflict`() {
        val offering = seedOffering()
        service.create(command(offering.id, partySize = 2, idempotencyKey = "shared-key"))

        val result = service.create(command(offering.id, partySize = 3, idempotencyKey = "shared-key"))

        assertThat(result).isEqualTo(CreateBookingResult.IdempotencyKeyConflict)
        assertThat(bookingRepository.byId).hasSize(1)
    }

    @Test
    fun `the same idempotency key with a different customer is a conflict`() {
        val offering = seedOffering()
        service.create(command(offering.id, idempotencyKey = "shared-key", customerName = "Ada Lovelace"))

        val result = service.create(command(offering.id, idempotencyKey = "shared-key", customerName = "Grace Hopper"))

        assertThat(result).isEqualTo(CreateBookingResult.IdempotencyKeyConflict)
        assertThat(bookingRepository.byId).hasSize(1)
    }

    @Test
    fun `rejects an idempotency key over 128 characters before touching the repository`() {
        val offering = seedOffering()

        assertThatIllegalArgumentException().isThrownBy { service.create(command(offering.id, idempotencyKey = "k".repeat(129))) }
        assertThat(bookingRepository.byId).isEmpty()
    }

    @Test
    fun `rejects a blank idempotency key`() {
        val offering = seedOffering()
        assertThatIllegalArgumentException().isThrownBy { service.create(command(offering.id, idempotencyKey = "")) }
    }

    @Test
    fun `rejects a booking against an offering that does not exist`() {
        val result = service.create(command(OfferingId.generate()))
        assertThat(result).isEqualTo(CreateBookingResult.OfferingNotFound)
    }

    @Test
    fun `rejects a booking against a closed offering`() {
        val offering = seedOffering()
        offering.close(fixedInstant)
        offeringRepository.save(offering)

        val result = service.create(command(offering.id))

        assertThat(result).isEqualTo(CreateBookingResult.OfferingClosed)
    }

    @Test
    fun `rejects a booking that would exceed the offering's capacity`() {
        val offering = seedOffering(capacity = 2)
        service.create(command(offering.id, partySize = 2, idempotencyKey = "key-1"))

        val result = service.create(command(offering.id, partySize = 1, idempotencyKey = "key-2"))

        assertThat(result).isEqualTo(CreateBookingResult.CapacityExceeded)
    }

    @Test
    fun `an offering with unlimited capacity accepts any party size`() {
        val offering = seedOffering(capacity = null)

        val result = service.create(command(offering.id, partySize = 1000))

        assertThat(result).isInstanceOf(CreateBookingResult.Created::class.java)
    }

    @Test
    fun `a replay is honored even after the offering has since closed`() {
        val offering = seedOffering()
        val first = service.create(command(offering.id)) as CreateBookingResult.Created

        offering.close(fixedInstant)
        offeringRepository.save(offering)

        val replay = service.create(command(offering.id)) as CreateBookingResult.Replayed
        assertThat(replay.booking.id).isEqualTo(first.booking.id)
    }
}
