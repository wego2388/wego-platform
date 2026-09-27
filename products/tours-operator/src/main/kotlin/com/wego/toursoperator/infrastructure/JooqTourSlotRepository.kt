package com.wego.toursoperator.infrastructure

import com.wego.generated.jooq.tables.ToursOperatorTourSlot.TOURS_OPERATOR_TOUR_SLOT
import com.wego.generated.jooq.tables.records.ToursOperatorTourSlotRecord
import com.wego.toursoperator.application.TourSlotRepository
import com.wego.toursoperator.domain.TimeSlot
import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.TourSlot
import com.wego.toursoperator.domain.TourSlotId
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository("stoTourSlotRepositoryImpl")
class JooqTourSlotRepository(
    private val dsl: DSLContext,
) : TourSlotRepository {
    @Transactional(readOnly = true)
    override fun findById(id: TourSlotId): TourSlot? {
        val record =
            dsl
                .selectFrom(TOURS_OPERATOR_TOUR_SLOT)
                .where(TOURS_OPERATOR_TOUR_SLOT.ID.eq(id.value))
                .fetchOne() ?: return null
        return toDomain(record)
    }

    /**
     * Row-level lock — the critical path for capacity enforcement.
     * Called by CreateBookingService before the capacity check to prevent
     * two concurrent bookings from both passing with available = 1.
     */
    @Transactional
    override fun findByIdForUpdate(id: TourSlotId): TourSlot? {
        val record =
            dsl
                .selectFrom(TOURS_OPERATOR_TOUR_SLOT)
                .where(TOURS_OPERATOR_TOUR_SLOT.ID.eq(id.value))
                .forUpdate()
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional(readOnly = true)
    override fun findByTourAndDate(
        tourId: TourId,
        date: LocalDate,
    ): List<TourSlot> =
        dsl
            .selectFrom(TOURS_OPERATOR_TOUR_SLOT)
            .where(TOURS_OPERATOR_TOUR_SLOT.TOUR_ID.eq(tourId.value))
            .and(TOURS_OPERATOR_TOUR_SLOT.DATE.eq(date))
            .orderBy(TOURS_OPERATOR_TOUR_SLOT.TIME_SLOT.asc())
            .fetch()
            .map(::toDomain)

    @Transactional(readOnly = true)
    override fun findAvailable(
        tourId: TourId,
        from: LocalDate,
        to: LocalDate,
    ): List<TourSlot> =
        dsl
            .selectFrom(TOURS_OPERATOR_TOUR_SLOT)
            .where(TOURS_OPERATOR_TOUR_SLOT.TOUR_ID.eq(tourId.value))
            .and(TOURS_OPERATOR_TOUR_SLOT.DATE.greaterOrEqual(from))
            .and(TOURS_OPERATOR_TOUR_SLOT.DATE.lessOrEqual(to))
            .and(TOURS_OPERATOR_TOUR_SLOT.IS_BLOCKED.isFalse)
            .and(TOURS_OPERATOR_TOUR_SLOT.BOOKED_COUNT.lessThan(TOURS_OPERATOR_TOUR_SLOT.CAPACITY))
            .orderBy(TOURS_OPERATOR_TOUR_SLOT.DATE.asc(), TOURS_OPERATOR_TOUR_SLOT.TIME_SLOT.asc())
            .fetch()
            .map(::toDomain)

    @Transactional
    override fun save(slot: TourSlot) {
        dsl
            .insertInto(TOURS_OPERATOR_TOUR_SLOT)
            .set(TOURS_OPERATOR_TOUR_SLOT.ID, slot.id.value)
            .set(TOURS_OPERATOR_TOUR_SLOT.TOUR_ID, slot.tourId.value)
            .set(TOURS_OPERATOR_TOUR_SLOT.DATE, slot.date)
            .set(TOURS_OPERATOR_TOUR_SLOT.TIME_SLOT, slot.timeSlot.name)
            .set(TOURS_OPERATOR_TOUR_SLOT.CAPACITY, slot.capacity)
            .set(TOURS_OPERATOR_TOUR_SLOT.BOOKED_COUNT, slot.bookedCount)
            .set(TOURS_OPERATOR_TOUR_SLOT.IS_BLOCKED, slot.isBlocked)
            .set(TOURS_OPERATOR_TOUR_SLOT.CREATED_AT, toOffset(slot.createdAt))
            .onConflict(TOURS_OPERATOR_TOUR_SLOT.ID)
            .doUpdate()
            .set(TOURS_OPERATOR_TOUR_SLOT.CAPACITY, slot.capacity)
            .set(TOURS_OPERATOR_TOUR_SLOT.BOOKED_COUNT, slot.bookedCount)
            .set(TOURS_OPERATOR_TOUR_SLOT.IS_BLOCKED, slot.isBlocked)
            .execute()
    }

    private fun toDomain(record: ToursOperatorTourSlotRecord): TourSlot =
        TourSlot(
            id = TourSlotId(record.id),
            tourId = TourId(record.tourId),
            date = record.date,
            timeSlot = TimeSlot.valueOf(record.timeSlot),
            capacity = record.capacity,
            bookedCount = record.bookedCount,
            isBlocked = record.isBlocked,
            createdAt = record.createdAt.toInstant(),
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
