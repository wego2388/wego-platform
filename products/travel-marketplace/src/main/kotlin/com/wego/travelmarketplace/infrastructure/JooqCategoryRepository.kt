package com.wego.travelmarketplace.infrastructure

import com.wego.generated.jooq.tables.TravelCategory.TRAVEL_CATEGORY
import com.wego.generated.jooq.tables.records.TravelCategoryRecord
import com.wego.travelmarketplace.application.CategoryRepository
import com.wego.travelmarketplace.domain.Category
import com.wego.travelmarketplace.domain.CategoryId
import com.wego.travelmarketplace.domain.CategoryStatus
import com.wego.travelmarketplace.domain.LocalizedText
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqCategoryRepository(
    private val dsl: DSLContext,
) : CategoryRepository {
    @Transactional(readOnly = true)
    override fun findById(id: CategoryId): Category? =
        dsl
            .selectFrom(TRAVEL_CATEGORY)
            .where(TRAVEL_CATEGORY.ID.eq(id.value))
            .fetchOne()
            ?.let(::toDomain)

    @Transactional
    override fun findByIdForUpdate(id: CategoryId): Category? =
        dsl
            .selectFrom(TRAVEL_CATEGORY)
            .where(TRAVEL_CATEGORY.ID.eq(id.value))
            .forUpdate()
            .fetchOne()
            ?.let(::toDomain)

    @Transactional(readOnly = true)
    override fun findByCode(code: String): Category? =
        dsl
            .selectFrom(TRAVEL_CATEGORY)
            .where(TRAVEL_CATEGORY.CODE.eq(code))
            .fetchOne()
            ?.let(::toDomain)

    @Transactional(readOnly = true)
    override fun findAll(status: CategoryStatus?): List<Category> {
        val condition = if (status != null) TRAVEL_CATEGORY.STATUS.eq(status.name) else DSL.noCondition()
        return dsl
            .selectFrom(TRAVEL_CATEGORY)
            .where(condition)
            .orderBy(TRAVEL_CATEGORY.DISPLAY_ORDER, TRAVEL_CATEGORY.ID)
            .fetch()
            .map(::toDomain)
    }

    @Transactional
    override fun save(category: Category) {
        dsl
            .insertInto(TRAVEL_CATEGORY)
            .set(TRAVEL_CATEGORY.ID, category.id.value)
            .set(TRAVEL_CATEGORY.CODE, category.code)
            .set(TRAVEL_CATEGORY.NAME_EN, category.name.en)
            .set(TRAVEL_CATEGORY.NAME_AR, category.name.ar)
            .set(TRAVEL_CATEGORY.DESCRIPTION_EN, category.description?.en)
            .set(TRAVEL_CATEGORY.DESCRIPTION_AR, category.description?.ar)
            .set(TRAVEL_CATEGORY.DISPLAY_ORDER, category.displayOrder)
            .set(TRAVEL_CATEGORY.STATUS, category.status.name)
            .set(TRAVEL_CATEGORY.CREATED_AT, toOffset(category.createdAt))
            .set(TRAVEL_CATEGORY.ARCHIVED_AT, category.archivedAt?.let(::toOffset))
            .onConflict(TRAVEL_CATEGORY.ID)
            .doUpdate()
            .set(TRAVEL_CATEGORY.NAME_EN, category.name.en)
            .set(TRAVEL_CATEGORY.NAME_AR, category.name.ar)
            .set(TRAVEL_CATEGORY.DESCRIPTION_EN, category.description?.en)
            .set(TRAVEL_CATEGORY.DESCRIPTION_AR, category.description?.ar)
            .set(TRAVEL_CATEGORY.DISPLAY_ORDER, category.displayOrder)
            .set(TRAVEL_CATEGORY.STATUS, category.status.name)
            .set(TRAVEL_CATEGORY.ARCHIVED_AT, category.archivedAt?.let(::toOffset))
            .execute()
    }

    private fun toDomain(record: TravelCategoryRecord): Category =
        Category(
            id = CategoryId(record.id),
            code = record.code,
            name = LocalizedText(record.nameEn, record.nameAr),
            description =
                if (record.descriptionEn != null && record.descriptionAr != null) {
                    LocalizedText(record.descriptionEn, record.descriptionAr)
                } else {
                    null
                },
            displayOrder = record.displayOrder,
            status = CategoryStatus.valueOf(record.status),
            createdAt = record.createdAt.toInstant(),
            archivedAt = record.archivedAt?.toInstant(),
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
