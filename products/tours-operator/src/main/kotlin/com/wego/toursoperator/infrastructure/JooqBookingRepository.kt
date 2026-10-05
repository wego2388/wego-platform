package com.wego.toursoperator.infrastructure

import com.wego.generated.jooq.tables.ToursOperatorBooking.TOURS_OPERATOR_BOOKING
import com.wego.generated.jooq.tables.ToursOperatorBookingReferenceSeq.TOURS_OPERATOR_BOOKING_REFERENCE_SEQ
import com.wego.generated.jooq.tables.records.ToursOperatorBookingRecord
import com.wego.toursoperator.application.BookingRepository
import com.wego.toursoperator.domain.Booking
import com.wego.toursoperator.domain.BookingChannel
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.BookingPricing
import com.wego.toursoperator.domain.BookingStatus
import com.wego.toursoperator.domain.CustomerContact
import com.wego.toursoperator.domain.Money
import com.wego.toursoperator.domain.TimeSlot
import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.TourSlotId
import com.wego.toursoperator.domain.UnitPurchase
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository("stoBookingRepositoryImpl")
class JooqBookingRepository(
    private val dsl: DSLContext,
) : BookingRepository {
    @Transactional(readOnly = true)
    override fun findById(id: BookingId): Booking? {
        val record =
            dsl
                .selectFrom(TOURS_OPERATOR_BOOKING)
                .where(TOURS_OPERATOR_BOOKING.ID.eq(id.value))
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional
    override fun findByIdForUpdate(id: BookingId): Booking? {
        val record =
            dsl
                .selectFrom(TOURS_OPERATOR_BOOKING)
                .where(TOURS_OPERATOR_BOOKING.ID.eq(id.value))
                .forUpdate()
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional(readOnly = true)
    override fun findByClientRequest(
        actorUserId: UUID,
        clientRequestId: UUID,
    ): Booking? =
        dsl
            .selectFrom(TOURS_OPERATOR_BOOKING)
            .where(TOURS_OPERATOR_BOOKING.CREATED_BY_USER_ID.eq(actorUserId))
            .and(TOURS_OPERATOR_BOOKING.CLIENT_REQUEST_ID.eq(clientRequestId))
            .fetchOne()
            ?.let(::toDomain)

    @Transactional(readOnly = true)
    override fun findByReference(reference: String): Booking? {
        val record =
            dsl
                .selectFrom(TOURS_OPERATOR_BOOKING)
                .where(TOURS_OPERATOR_BOOKING.REFERENCE.eq(reference))
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional(readOnly = true)
    override fun findByReferenceAndPhone(
        reference: String,
        phone: String,
    ): Booking? {
        val record =
            dsl
                .selectFrom(TOURS_OPERATOR_BOOKING)
                .where(TOURS_OPERATOR_BOOKING.REFERENCE.eq(reference))
                .and(TOURS_OPERATOR_BOOKING.CUSTOMER_PHONE.eq(phone))
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional(readOnly = true)
    override fun findAll(
        tourId: TourId?,
        status: BookingStatus?,
        date: LocalDate?,
        limit: Int,
        offset: Int,
    ): List<Booking> {
        var condition = DSL.noCondition()
        if (tourId != null) {
            condition = condition.and(TOURS_OPERATOR_BOOKING.TOUR_ID.eq(tourId.value))
        }
        if (status != null) {
            condition = condition.and(TOURS_OPERATOR_BOOKING.STATUS.eq(status.name))
        }
        if (date != null) {
            condition = condition.and(TOURS_OPERATOR_BOOKING.TOUR_DATE.eq(date))
        }
        return dsl
            .selectFrom(TOURS_OPERATOR_BOOKING)
            .where(condition)
            // created_at DESC, ID as tie-breaker for stable pagination
            .orderBy(TOURS_OPERATOR_BOOKING.CREATED_AT.desc(), TOURS_OPERATOR_BOOKING.ID.desc())
            .limit(limit)
            .offset(offset)
            .fetch()
            .map(::toDomain)
    }

    @Transactional(readOnly = true)
    override fun findNewCreatedBefore(cutoff: Instant): List<Booking> =
        dsl
            .selectFrom(TOURS_OPERATOR_BOOKING)
            .where(TOURS_OPERATOR_BOOKING.STATUS.eq(BookingStatus.NEW.name))
            .and(TOURS_OPERATOR_BOOKING.CHANNEL.eq(BookingChannel.ONLINE.name))
            .and(TOURS_OPERATOR_BOOKING.CREATED_AT.lt(toOffset(cutoff)))
            .orderBy(TOURS_OPERATOR_BOOKING.CREATED_AT.asc(), TOURS_OPERATOR_BOOKING.ID.asc())
            .fetch()
            .map(::toDomain)

    @Transactional(readOnly = true)
    override fun countBySlotId(slotId: TourSlotId): Int {
        val count =
            dsl
                .selectCount()
                .from(TOURS_OPERATOR_BOOKING)
                .where(TOURS_OPERATOR_BOOKING.SLOT_ID.eq(slotId.value))
                .fetchOne(0, Int::class.java)
        return count ?: 0
    }

    /**
     * Claims the next sequence number for the STR-YYYY-N reference format.
     *
     * Uses UPSERT with an atomic increment so that:
     * - If the year row doesn't exist yet, it's created with next_val = 2
     *   and the claimed value is 1.
     * - If the year row exists, next_val is incremented and the value
     *   before increment (next_val - 1) is claimed.
     *
     * This must be called inside a transaction where the slot row is
     * already locked (via findByIdForUpdate) to prevent gaps or duplicate
     * references under concurrent creation.
     *
     * Note: A plain table is used intentionally (not a DB SEQUENCE) to
     * support per-year numbering with STR-YYYY-N format.
     */
    @Transactional
    override fun nextReferenceSequence(year: Int): Long {
        // Atomically upsert the year counter and return the claimed value.
        // On first insert: next_val starts at 1, claimed = 1, stored = 2.
        // On conflict: next_val incremented by 1, claimed = old next_val.
        val claimed =
            dsl
                .insertInto(TOURS_OPERATOR_BOOKING_REFERENCE_SEQ)
                .set(TOURS_OPERATOR_BOOKING_REFERENCE_SEQ.YEAR, year)
                .set(TOURS_OPERATOR_BOOKING_REFERENCE_SEQ.NEXT_VAL, 2L)
                .onConflict(TOURS_OPERATOR_BOOKING_REFERENCE_SEQ.YEAR)
                .doUpdate()
                .set(
                    TOURS_OPERATOR_BOOKING_REFERENCE_SEQ.NEXT_VAL,
                    TOURS_OPERATOR_BOOKING_REFERENCE_SEQ.NEXT_VAL.add(1L),
                ).returningResult(TOURS_OPERATOR_BOOKING_REFERENCE_SEQ.NEXT_VAL)
                .fetchOne()
                ?.value1() ?: error("nextReferenceSequence upsert returned no value for year=$year")

        // The UPSERT returns the value AFTER increment (or 2 on insert).
        // We want the value BEFORE increment = claimed - 1.
        // On first insert: returned = 2, claimed = 2 - 1 = 1. ✓
        // On conflict: returned = old+1, claimed = old+1 - 1 = old. ✓
        return claimed - 1L
    }

    @Transactional
    override fun save(booking: Booking) {
        val confirmedAt = booking.confirmedAt?.let(::toOffset)
        val cancelledAt = booking.cancelledAt?.let(::toOffset)
        val completedAt = booking.completedAt?.let(::toOffset)
        val expiredAt = booking.expiredAt?.let(::toOffset)

        dsl
            .insertInto(TOURS_OPERATOR_BOOKING)
            .set(TOURS_OPERATOR_BOOKING.ID, booking.id.value)
            .set(TOURS_OPERATOR_BOOKING.REFERENCE, booking.reference)
            .set(TOURS_OPERATOR_BOOKING.TOUR_ID, booking.tourId.value)
            .set(TOURS_OPERATOR_BOOKING.SLOT_ID, booking.slotId.value)
            .set(TOURS_OPERATOR_BOOKING.TOUR_DATE, booking.tourDate)
            .set(TOURS_OPERATOR_BOOKING.TIME_SLOT, booking.timeSlot.name)
            .set(TOURS_OPERATOR_BOOKING.ADULTS_COUNT, booking.pricing.adultsCount)
            .set(TOURS_OPERATOR_BOOKING.CHILDREN_COUNT, booking.pricing.childrenCount)
            .set(TOURS_OPERATOR_BOOKING.PRICE_ADULT_EUR, centsToEur(booking.pricing.priceAdult.amount))
            .set(TOURS_OPERATOR_BOOKING.PRICE_CHILD_EUR, booking.pricing.priceChild?.let { centsToEur(it.amount) })
            .set(TOURS_OPERATOR_BOOKING.TOTAL_EUR, booking.pricing.totalEur.amount)
            .set(TOURS_OPERATOR_BOOKING.PRICE_OPTION_CODE, booking.pricing.unit?.optionCode)
            .set(TOURS_OPERATOR_BOOKING.PRICE_OPTION_LABEL, booking.pricing.unit?.optionLabel)
            .set(TOURS_OPERATOR_BOOKING.SEATS_PER_UNIT, booking.pricing.unit?.seatsPerUnit)
            .set(TOURS_OPERATOR_BOOKING.UNIT_COUNT, booking.pricing.unit?.unitCount)
            .set(
                TOURS_OPERATOR_BOOKING.UNIT_PRICE_EUR,
                booking.pricing.unit
                    ?.unitPrice
                    ?.amount,
            ).set(TOURS_OPERATOR_BOOKING.CUSTOMER_FULL_NAME, booking.customer.fullName)
            .set(TOURS_OPERATOR_BOOKING.CUSTOMER_PHONE, booking.customer.phone)
            .set(TOURS_OPERATOR_BOOKING.CUSTOMER_NATIONALITY, booking.customer.nationality)
            .set(TOURS_OPERATOR_BOOKING.CUSTOMER_EMAIL, booking.customer.email)
            .set(TOURS_OPERATOR_BOOKING.HOTEL_NAME, booking.hotelName)
            .set(TOURS_OPERATOR_BOOKING.HOTEL_ROOM, booking.hotelRoom)
            .set(TOURS_OPERATOR_BOOKING.SPECIAL_REQUESTS, booking.specialRequests)
            .set(TOURS_OPERATOR_BOOKING.LOCALE, booking.locale)
            .set(TOURS_OPERATOR_BOOKING.STATUS, booking.status.name)
            .set(TOURS_OPERATOR_BOOKING.CREATED_AT, toOffset(booking.createdAt))
            .set(TOURS_OPERATOR_BOOKING.CONFIRMED_AT, confirmedAt)
            .set(TOURS_OPERATOR_BOOKING.CANCELLED_AT, cancelledAt)
            .set(TOURS_OPERATOR_BOOKING.CANCELLATION_REASON, booking.cancellationReason)
            .set(TOURS_OPERATOR_BOOKING.COMPLETED_AT, completedAt)
            .set(TOURS_OPERATOR_BOOKING.EXPIRED_AT, expiredAt)
            .set(TOURS_OPERATOR_BOOKING.CHANNEL, booking.channel.name)
            .set(TOURS_OPERATOR_BOOKING.CREATED_BY_USER_ID, booking.createdByUserId)
            .set(TOURS_OPERATOR_BOOKING.CLIENT_REQUEST_ID, booking.clientRequestId)
            .onConflict(TOURS_OPERATOR_BOOKING.ID)
            .doUpdate()
            .set(TOURS_OPERATOR_BOOKING.STATUS, booking.status.name)
            .set(TOURS_OPERATOR_BOOKING.CONFIRMED_AT, confirmedAt)
            .set(TOURS_OPERATOR_BOOKING.CANCELLED_AT, cancelledAt)
            .set(TOURS_OPERATOR_BOOKING.CANCELLATION_REASON, booking.cancellationReason)
            .set(TOURS_OPERATOR_BOOKING.COMPLETED_AT, completedAt)
            .set(TOURS_OPERATOR_BOOKING.EXPIRED_AT, expiredAt)
            .execute()
    }

    private fun toDomain(record: ToursOperatorBookingRecord): Booking =
        Booking(
            id = BookingId(record.id),
            reference = record.reference,
            tourId = TourId(record.tourId),
            slotId = TourSlotId(record.slotId),
            tourDate = record.tourDate,
            timeSlot = TimeSlot.valueOf(record.timeSlot),
            pricing =
                BookingPricing(
                    adultsCount = record.adultsCount,
                    childrenCount = record.childrenCount,
                    priceAdult = Money(record.priceAdultEur.setScale(Money.REQUIRED_SCALE)),
                    priceChild = record.priceChildEur?.let { Money(it.setScale(Money.REQUIRED_SCALE)) },
                    totalEur = Money(record.totalEur.setScale(Money.REQUIRED_SCALE)),
                    unit =
                        record.unitCount?.let { unitCount ->
                            UnitPurchase(
                                optionCode = record.priceOptionCode,
                                optionLabel = record.priceOptionLabel,
                                seatsPerUnit = record.seatsPerUnit,
                                unitCount = unitCount,
                                unitPrice = Money(record.unitPriceEur.setScale(Money.REQUIRED_SCALE)),
                            )
                        },
                ),
            customer =
                CustomerContact(
                    fullName = record.customerFullName,
                    phone = record.customerPhone,
                    nationality = record.customerNationality,
                    email = record.customerEmail,
                ),
            hotelName = record.hotelName,
            hotelRoom = record.hotelRoom,
            specialRequests = record.specialRequests,
            locale = record.locale,
            status = BookingStatus.valueOf(record.status),
            createdAt = record.createdAt.toInstant(),
            confirmedAt = record.confirmedAt?.toInstant(),
            cancelledAt = record.cancelledAt?.toInstant(),
            cancellationReason = record.cancellationReason,
            completedAt = record.completedAt?.toInstant(),
            expiredAt = record.expiredAt?.toInstant(),
            channel = BookingChannel.valueOf(record.channel),
            createdByUserId = record.createdByUserId,
            clientRequestId = record.clientRequestId,
        )

    /**
     * Converts EUR amount (BigDecimal, scale 2) to the same representation.
     * The domain Money stores EUR as BigDecimal already; priceAdultCents on
     * the Tour is stored as Long cents, but BookingPricing.priceAdult is Money
     * (BigDecimal EUR). No conversion needed here — stored as DECIMAL(10,2).
     */
    private fun centsToEur(amount: BigDecimal): BigDecimal = amount

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
