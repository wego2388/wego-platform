package com.wego.travelmarketplace.infrastructure

import com.wego.generated.jooq.tables.TravelProvider.TRAVEL_PROVIDER
import com.wego.generated.jooq.tables.records.TravelProviderRecord
import com.wego.travelmarketplace.application.ProviderRepository
import com.wego.travelmarketplace.domain.Provider
import com.wego.travelmarketplace.domain.ProviderId
import com.wego.travelmarketplace.domain.ProviderStatus
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqProviderRepository(
    private val dsl: DSLContext,
) : ProviderRepository {
    @Transactional(readOnly = true)
    override fun findById(id: ProviderId): Provider? =
        dsl
            .selectFrom(TRAVEL_PROVIDER)
            .where(TRAVEL_PROVIDER.ID.eq(id.value))
            .fetchOne()
            ?.let(::toDomain)

    @Transactional
    override fun findByIdForUpdate(id: ProviderId): Provider? =
        dsl
            .selectFrom(TRAVEL_PROVIDER)
            .where(TRAVEL_PROVIDER.ID.eq(id.value))
            .forUpdate()
            .fetchOne()
            ?.let(::toDomain)

    @Transactional(readOnly = true)
    override fun findAll(
        status: ProviderStatus?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<Provider> {
        var condition = DSL.noCondition()
        if (status != null) {
            condition = condition.and(TRAVEL_PROVIDER.STATUS.eq(status.name))
        }
        if (!search.isNullOrBlank()) {
            condition = condition.and(TRAVEL_PROVIDER.NAME.containsIgnoreCase(search.trim()))
        }
        return dsl
            .selectFrom(TRAVEL_PROVIDER)
            .where(condition)
            .orderBy(TRAVEL_PROVIDER.NAME, TRAVEL_PROVIDER.ID)
            .limit(limit)
            .offset(offset)
            .fetch()
            .map(::toDomain)
    }

    @Transactional
    override fun save(provider: Provider) {
        dsl
            .insertInto(TRAVEL_PROVIDER)
            .set(TRAVEL_PROVIDER.ID, provider.id.value)
            .set(TRAVEL_PROVIDER.NAME, provider.name)
            .set(TRAVEL_PROVIDER.CONTACT_EMAIL, provider.contactEmail)
            .set(TRAVEL_PROVIDER.CONTACT_PHONE, provider.contactPhone)
            .set(TRAVEL_PROVIDER.STATUS, provider.status.name)
            .set(TRAVEL_PROVIDER.CREATED_AT, toOffset(provider.createdAt))
            .set(TRAVEL_PROVIDER.ARCHIVED_AT, provider.archivedAt?.let(::toOffset))
            .onConflict(TRAVEL_PROVIDER.ID)
            .doUpdate()
            .set(TRAVEL_PROVIDER.NAME, provider.name)
            .set(TRAVEL_PROVIDER.CONTACT_EMAIL, provider.contactEmail)
            .set(TRAVEL_PROVIDER.CONTACT_PHONE, provider.contactPhone)
            .set(TRAVEL_PROVIDER.STATUS, provider.status.name)
            .set(TRAVEL_PROVIDER.ARCHIVED_AT, provider.archivedAt?.let(::toOffset))
            .execute()
    }

    private fun toDomain(record: TravelProviderRecord): Provider =
        Provider(
            id = ProviderId(record.id),
            name = record.name,
            contactEmail = record.contactEmail,
            contactPhone = record.contactPhone,
            status = ProviderStatus.valueOf(record.status),
            createdAt = record.createdAt.toInstant(),
            archivedAt = record.archivedAt?.toInstant(),
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
