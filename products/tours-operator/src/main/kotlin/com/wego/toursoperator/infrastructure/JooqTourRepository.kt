package com.wego.toursoperator.infrastructure

import com.wego.generated.jooq.tables.ToursOperatorTour.TOURS_OPERATOR_TOUR
import com.wego.generated.jooq.tables.records.ToursOperatorTourRecord
import com.wego.toursoperator.application.TourRepository
import com.wego.toursoperator.domain.CancellationPolicy
import com.wego.toursoperator.domain.TimeSlot
import com.wego.toursoperator.domain.Tour
import com.wego.toursoperator.domain.TourCategory
import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.TourType
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository("stoTourRepositoryImpl")
class JooqTourRepository(
    private val dsl: DSLContext,
) : TourRepository {
    @Transactional(readOnly = true)
    override fun findById(id: TourId): Tour? {
        val record =
            dsl
                .selectFrom(TOURS_OPERATOR_TOUR)
                .where(TOURS_OPERATOR_TOUR.ID.eq(id.value))
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional
    override fun findByIdForUpdate(id: TourId): Tour? {
        val record =
            dsl
                .selectFrom(TOURS_OPERATOR_TOUR)
                .where(TOURS_OPERATOR_TOUR.ID.eq(id.value))
                .forUpdate()
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional(readOnly = true)
    override fun findBySlug(slug: String): Tour? {
        val record =
            dsl
                .selectFrom(TOURS_OPERATOR_TOUR)
                .where(TOURS_OPERATOR_TOUR.SLUG.eq(slug))
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional(readOnly = true)
    override fun existsBySlug(slug: String): Boolean =
        dsl.fetchExists(
            dsl
                .selectFrom(TOURS_OPERATOR_TOUR)
                .where(TOURS_OPERATOR_TOUR.SLUG.eq(slug)),
        )

    @Transactional(readOnly = true)
    override fun findAll(
        category: TourCategory?,
        activeOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<Tour> {
        var condition = DSL.noCondition()
        if (category != null) {
            condition = condition.and(TOURS_OPERATOR_TOUR.CATEGORY.eq(category.name))
        }
        if (activeOnly) {
            condition = condition.and(TOURS_OPERATOR_TOUR.IS_ACTIVE.isTrue)
        }
        return dsl
            .selectFrom(TOURS_OPERATOR_TOUR)
            .where(condition)
            // sort_order for display order, ID as tie-breaker for stable pagination
            .orderBy(TOURS_OPERATOR_TOUR.SORT_ORDER.asc(), TOURS_OPERATOR_TOUR.ID.asc())
            .limit(limit)
            .offset(offset)
            .fetch()
            .map(::toDomain)
    }

    @Transactional
    override fun save(tour: Tour) {
        dsl
            .insertInto(TOURS_OPERATOR_TOUR)
            .set(TOURS_OPERATOR_TOUR.ID, tour.id.value)
            .set(TOURS_OPERATOR_TOUR.SLUG, tour.slug)
            .set(TOURS_OPERATOR_TOUR.CATEGORY, tour.category.name)
            .set(TOURS_OPERATOR_TOUR.DURATION_TEXT, tour.durationText)
            .set(TOURS_OPERATOR_TOUR.PRICE_ADULT_CENTS, tour.priceAdultCents)
            .set(TOURS_OPERATOR_TOUR.PRICE_CHILD_CENTS, tour.priceChildCents)
            .set(TOURS_OPERATOR_TOUR.CAPACITY, tour.capacity)
            .set(TOURS_OPERATOR_TOUR.AVAILABLE_TIME_SLOTS, toTimeSlotsString(tour.availableTimeSlots))
            .set(TOURS_OPERATOR_TOUR.SORT_ORDER, tour.sortOrder)
            .set(TOURS_OPERATOR_TOUR.IS_ACTIVE, tour.isActive)
            .set(TOURS_OPERATOR_TOUR.CREATED_BY_USER_ID, tour.createdByUserId)
            .set(TOURS_OPERATOR_TOUR.CREATED_AT, toOffset(tour.createdAt))
            .set(TOURS_OPERATOR_TOUR.NAME_EN, tour.nameEn)
            .set(TOURS_OPERATOR_TOUR.TOUR_TYPE, tour.tourType.name)
            .set(TOURS_OPERATOR_TOUR.IMAGE_URL, tour.imageUrl)
            .set(TOURS_OPERATOR_TOUR.CANCELLATION_POLICY, tour.cancellationPolicy.name)
            .set(TOURS_OPERATOR_TOUR.PRICING_NOTE, tour.pricingNote)
            .onConflict(TOURS_OPERATOR_TOUR.ID)
            .doUpdate()
            .set(TOURS_OPERATOR_TOUR.DURATION_TEXT, tour.durationText)
            .set(TOURS_OPERATOR_TOUR.PRICE_ADULT_CENTS, tour.priceAdultCents)
            .set(TOURS_OPERATOR_TOUR.PRICE_CHILD_CENTS, tour.priceChildCents)
            .set(TOURS_OPERATOR_TOUR.CAPACITY, tour.capacity)
            .set(TOURS_OPERATOR_TOUR.AVAILABLE_TIME_SLOTS, toTimeSlotsString(tour.availableTimeSlots))
            .set(TOURS_OPERATOR_TOUR.SORT_ORDER, tour.sortOrder)
            .set(TOURS_OPERATOR_TOUR.IS_ACTIVE, tour.isActive)
            .set(TOURS_OPERATOR_TOUR.NAME_EN, tour.nameEn)
            .set(TOURS_OPERATOR_TOUR.TOUR_TYPE, tour.tourType.name)
            .set(TOURS_OPERATOR_TOUR.IMAGE_URL, tour.imageUrl)
            .set(TOURS_OPERATOR_TOUR.CANCELLATION_POLICY, tour.cancellationPolicy.name)
            .set(TOURS_OPERATOR_TOUR.PRICING_NOTE, tour.pricingNote)
            .execute()
    }

    private fun toDomain(record: ToursOperatorTourRecord): Tour =
        Tour(
            id = TourId(record.id),
            slug = record.slug,
            category = TourCategory.valueOf(record.category),
            durationText = record.durationText,
            priceAdultCents = record.priceAdultCents,
            priceChildCents = record.priceChildCents,
            capacity = record.capacity,
            availableTimeSlots = parseTimeSlots(record.availableTimeSlots),
            sortOrder = record.sortOrder,
            isActive = record.isActive,
            createdAt = record.createdAt.toInstant(),
            createdByUserId = record.createdByUserId,
            nameEn = record.nameEn,
            tourType = record.tourType?.let { runCatching { TourType.valueOf(it) }.getOrDefault(TourType.TOUR) } ?: TourType.TOUR,
            imageUrl = record.imageUrl,
            cancellationPolicy =
                record.cancellationPolicy?.let { runCatching { CancellationPolicy.valueOf(it) }.getOrDefault(CancellationPolicy.STANDARD) }
                    ?: CancellationPolicy.STANDARD,
            pricingNote = record.pricingNote,
        )

    /**
     * Converts a Set<TimeSlot> to a comma-separated string for DB storage.
     * e.g. {MORNING, SUNSET} → "MORNING,SUNSET"
     */
    private fun toTimeSlotsString(slots: Set<TimeSlot>): String = slots.joinToString(",") { it.name }

    /**
     * Parses the comma-separated time slots string from DB back to Set<TimeSlot>.
     * e.g. "MORNING,SUNSET" → {MORNING, SUNSET}
     */
    private fun parseTimeSlots(raw: String): Set<TimeSlot> =
        raw
            .split(",")
            .filter { it.isNotBlank() }
            .map { TimeSlot.valueOf(it.trim()) }
            .toSet()

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
