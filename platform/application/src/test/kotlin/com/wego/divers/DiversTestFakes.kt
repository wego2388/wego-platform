package com.wego.divers

import com.wego.divers.application.BookingAuditRecorder
import com.wego.divers.application.BookingRepository
import com.wego.divers.application.OfferingAuditRecorder
import com.wego.divers.application.OfferingRepository
import com.wego.divers.application.TransactionRunner
import com.wego.divers.domain.Booking
import com.wego.divers.domain.BookingId
import com.wego.divers.domain.BookingStatus
import com.wego.divers.domain.Money
import com.wego.divers.domain.Offering
import com.wego.divers.domain.OfferingId
import com.wego.divers.domain.OfferingStatus
import com.wego.divers.domain.OfferingType
import com.wego.divers.domain.PaymentStatus
import com.wego.events.IntegrationEventEnvelope
import com.wego.events.OutboxWriter
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

internal class NoOpTransactionRunner : TransactionRunner {
    override fun <T> runInTransaction(block: () -> T): T = block()
}

internal class InMemoryOfferingRepository : OfferingRepository {
    val byId = mutableMapOf<OfferingId, Offering>()

    // No real locking semantics: fakes are single-threaded test doubles.
    // Row-locking behavior itself is proven against real PostgreSQL in
    // BookingCapacityConcurrencyIntegrationTest, not here.
    override fun findByIdForUpdate(id: OfferingId): Offering? = findById(id)

    override fun findById(id: OfferingId): Offering? = byId[id]

    override fun findAll(
        offeringType: OfferingType?,
        status: OfferingStatus?,
        limit: Int,
        offset: Int,
    ): List<Offering> =
        byId.values
            .filter {
                (offeringType == null || it.offeringType == offeringType) &&
                    (status == null || it.status == status)
            }.drop(offset)
            .take(limit)

    override fun findUpcoming(
        from: LocalDate,
        to: LocalDate,
        limit: Int,
    ): List<Offering> =
        byId.values
            .filter { it.status == OfferingStatus.ACTIVE && it.startsOn >= from && it.startsOn <= to }
            .sortedBy { it.startsOn }
            .take(limit)

    override fun save(offering: Offering) {
        byId[offering.id] = offering
    }
}

internal class InMemoryBookingRepository : BookingRepository {
    val byId = mutableMapOf<BookingId, Booking>()

    // No real locking semantics: fakes are single-threaded test doubles.
    // The real advisory lock is proven against real PostgreSQL in
    // IdempotencyConcurrencyIntegrationTest, not here.
    override fun lockIdempotencyKey(
        actorUserId: UUID,
        idempotencyKey: String,
    ) = Unit

    override fun findById(id: BookingId): Booking? = byId[id]

    override fun findByIdForUpdate(id: BookingId): Booking? = findById(id)

    override fun findByIdempotencyKey(
        createdByUserId: UUID,
        idempotencyKey: String,
    ): Booking? = byId.values.find { it.createdByUserId == createdByUserId && it.idempotencyKey == idempotencyKey }

    override fun sumConfirmedPartySize(offeringId: OfferingId): Int =
        byId.values
            .filter { it.offeringId == offeringId && it.status == BookingStatus.CONFIRMED }
            .sumOf { it.partySize }

    override fun findAll(
        offeringId: OfferingId?,
        status: BookingStatus?,
        limit: Int,
        offset: Int,
    ): List<Booking> =
        byId.values
            .filter {
                (offeringId == null || it.offeringId == offeringId) &&
                    (status == null || it.status == status)
            }.drop(offset)
            .take(limit)

    override fun countCreatedBetween(
        from: Instant,
        to: Instant,
    ): Int = byId.values.count { it.createdAt >= from && it.createdAt < to }

    override fun sumPaidTotalsCreatedBetween(
        from: Instant,
        to: Instant,
    ): List<Money> =
        byId.values
            .filter {
                it.status == BookingStatus.CONFIRMED &&
                    it.paymentStatus == PaymentStatus.PAID &&
                    it.createdAt >= from &&
                    it.createdAt < to
            }.groupBy { it.pricing.totalPrice.currencyCode }
            .map { (currencyCode, bookings) ->
                Money(
                    bookings.sumOf { it.pricing.totalPrice.amount }.setScale(Money.REQUIRED_SCALE, RoundingMode.HALF_UP),
                    currencyCode,
                )
            }

    override fun save(booking: Booking) {
        byId[booking.id] = booking
    }
}

internal class RecordingBookingAuditRecorder : BookingAuditRecorder {
    val created = mutableListOf<BookingId>()
    val cancelled = mutableListOf<Pair<BookingId, String>>()
    val markedPaid = mutableListOf<BookingId>()
    val refunded = mutableListOf<Pair<BookingId, String>>()

    override fun recordBookingCreated(
        bookingId: BookingId,
        actorUserId: UUID,
        occurredAt: Instant,
        correlationId: UUID?,
    ) {
        created += bookingId
    }

    override fun recordBookingCancelled(
        bookingId: BookingId,
        actorUserId: UUID,
        occurredAt: Instant,
        reason: String,
        correlationId: UUID?,
    ) {
        cancelled += bookingId to reason
    }

    override fun recordPaymentMarkedPaid(
        bookingId: BookingId,
        actorUserId: UUID,
        occurredAt: Instant,
        correlationId: UUID?,
    ) {
        markedPaid += bookingId
    }

    override fun recordPaymentRefunded(
        bookingId: BookingId,
        actorUserId: UUID,
        occurredAt: Instant,
        reason: String,
        correlationId: UUID?,
    ) {
        refunded += bookingId to reason
    }
}

internal class RecordingOfferingAuditRecorder : OfferingAuditRecorder {
    val created = mutableListOf<OfferingId>()
    val closed = mutableListOf<Pair<OfferingId, String?>>()

    override fun recordOfferingCreated(
        offeringId: OfferingId,
        actorUserId: UUID?,
        occurredAt: Instant,
        correlationId: UUID?,
    ) {
        created += offeringId
    }

    override fun recordOfferingClosed(
        offeringId: OfferingId,
        actorUserId: UUID,
        occurredAt: Instant,
        reason: String?,
        correlationId: UUID?,
    ) {
        closed += offeringId to reason
    }
}

internal class RecordingOutboxWriter : OutboxWriter {
    val written = mutableListOf<IntegrationEventEnvelope>()

    override fun write(envelope: IntegrationEventEnvelope) {
        written += envelope
    }
}
