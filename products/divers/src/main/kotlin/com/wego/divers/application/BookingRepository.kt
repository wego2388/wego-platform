package com.wego.divers.application

import com.wego.divers.domain.Booking
import com.wego.divers.domain.BookingId
import com.wego.divers.domain.BookingStatus
import com.wego.divers.domain.Money
import com.wego.divers.domain.OfferingId
import java.time.Instant
import java.util.UUID

interface BookingRepository {
    /**
     * Takes a transaction-scoped PostgreSQL advisory lock keyed on
     * (actorUserId, idempotencyKey), released automatically at commit or
     * rollback. This is the real serialization point for
     * [CreateBookingService] — two concurrent requests sharing the same
     * actor+key but targeting *different* offerings never share an
     * offering row lock, so without this, both could observe "no existing
     * booking for this key" before either commits and collide on the
     * `divers_booking_idempotency_key_unique` constraint as a raw,
     * unhandled 500 instead of a clean, detected conflict. Always acquired
     * before [OfferingRepository.findByIdForUpdate] in every call site —
     * a fixed lock order is what keeps this deadlock-free.
     */
    fun lockIdempotencyKey(
        actorUserId: UUID,
        idempotencyKey: String,
    )

    fun findById(id: BookingId): Booking?

    /**
     * Same lookup as [findById], but takes a row lock for the duration of
     * the caller's transaction — cancel must use this so two concurrent
     * cancel requests for the same booking cannot both succeed.
     */
    fun findByIdForUpdate(id: BookingId): Booking?

    fun findByIdempotencyKey(
        createdByUserId: UUID,
        idempotencyKey: String,
    ): Booking?

    /**
     * Sum of `party_size` across every `CONFIRMED` booking for
     * [offeringId] — the capacity-count hot path. Must be read after
     * [OfferingRepository.findByIdForUpdate] has locked the offering row in
     * the same transaction, or two concurrent bookings can both read the
     * same count and both pass the capacity check.
     */
    fun sumConfirmedPartySize(offeringId: OfferingId): Int

    /**
     * [limit]/[offset] are mandatory, not defaulted here, so every call
     * site has to make an explicit choice — this table is append-only
     * (cancel is a status flip, never a delete), so an unbounded scan here
     * would grow without limit over the life of the client.
     */
    fun findAll(
        offeringId: OfferingId?,
        status: BookingStatus?,
        limit: Int,
        offset: Int,
    ): List<Booking>

    /** A real `COUNT(*)` over `[from, to)` — the dashboard's "bookings today" tally. */
    fun countCreatedBetween(
        from: Instant,
        to: Instant,
    ): Int

    /**
     * Sum of `total_price` for every `CONFIRMED` + `PAID` booking created in
     * `[from, to)`, grouped by currency — a client could in principle price
     * offerings in more than one currency, so this never assumes a single
     * one. An approximation of "revenue" by `created_at` (there is no
     * separate payment-date column yet), not a real recognized-revenue date.
     */
    fun sumPaidTotalsCreatedBetween(
        from: Instant,
        to: Instant,
    ): List<Money>

    fun save(booking: Booking)
}
