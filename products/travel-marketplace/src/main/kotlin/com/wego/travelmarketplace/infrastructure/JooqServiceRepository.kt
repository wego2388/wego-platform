package com.wego.travelmarketplace.infrastructure

import com.wego.generated.jooq.tables.TravelService.TRAVEL_SERVICE
import com.wego.generated.jooq.tables.TravelServiceMedia.TRAVEL_SERVICE_MEDIA
import com.wego.generated.jooq.tables.TravelServiceOption.TRAVEL_SERVICE_OPTION
import com.wego.generated.jooq.tables.records.TravelServiceMediaRecord
import com.wego.generated.jooq.tables.records.TravelServiceOptionRecord
import com.wego.generated.jooq.tables.records.TravelServiceRecord
import com.wego.travelmarketplace.application.ServiceRepository
import com.wego.travelmarketplace.domain.CategoryId
import com.wego.travelmarketplace.domain.ConfirmationType
import com.wego.travelmarketplace.domain.FulfilmentModel
import com.wego.travelmarketplace.domain.LocalizedText
import com.wego.travelmarketplace.domain.Money
import com.wego.travelmarketplace.domain.PriceBasis
import com.wego.travelmarketplace.domain.ProviderId
import com.wego.travelmarketplace.domain.Service
import com.wego.travelmarketplace.domain.ServiceId
import com.wego.travelmarketplace.domain.ServiceMedia
import com.wego.travelmarketplace.domain.ServiceOption
import com.wego.travelmarketplace.domain.ServiceStatus
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class JooqServiceRepository(
    private val dsl: DSLContext,
) : ServiceRepository {
    @Transactional(readOnly = true)
    override fun findById(id: ServiceId): Service? =
        dsl
            .selectFrom(TRAVEL_SERVICE)
            .where(TRAVEL_SERVICE.ID.eq(id.value))
            .fetchOne()
            ?.let { toDomain(it) }

    @Transactional
    override fun findByIdForUpdate(id: ServiceId): Service? =
        dsl
            .selectFrom(TRAVEL_SERVICE)
            .where(TRAVEL_SERVICE.ID.eq(id.value))
            .forUpdate()
            .fetchOne()
            ?.let { toDomain(it) }

    @Transactional(readOnly = true)
    override fun findAll(
        status: ServiceStatus?,
        categoryId: CategoryId?,
        limit: Int,
        offset: Int,
    ): List<Service> {
        var condition = DSL.noCondition()
        if (status != null) condition = condition.and(TRAVEL_SERVICE.STATUS.eq(status.name))
        if (categoryId != null) condition = condition.and(TRAVEL_SERVICE.CATEGORY_ID.eq(categoryId.value))
        return fetchMany(condition, limit, offset)
    }

    @Transactional(readOnly = true)
    override fun findAllPublished(
        categoryId: CategoryId?,
        limit: Int,
        offset: Int,
    ): List<Service> {
        var condition = TRAVEL_SERVICE.STATUS.eq(ServiceStatus.PUBLISHED.name)
        if (categoryId != null) condition = condition.and(TRAVEL_SERVICE.CATEGORY_ID.eq(categoryId.value))
        return fetchMany(condition, limit, offset)
    }

    @Transactional(readOnly = true)
    override fun findPublishedById(id: ServiceId): Service? =
        dsl
            .selectFrom(TRAVEL_SERVICE)
            .where(TRAVEL_SERVICE.ID.eq(id.value))
            .and(TRAVEL_SERVICE.STATUS.eq(ServiceStatus.PUBLISHED.name))
            .fetchOne()
            ?.let { toDomain(it) }

    private fun fetchMany(
        condition: org.jooq.Condition,
        limit: Int,
        offset: Int,
    ): List<Service> {
        val records =
            dsl
                .selectFrom(TRAVEL_SERVICE)
                .where(condition)
                .orderBy(TRAVEL_SERVICE.NAME_EN, TRAVEL_SERVICE.ID)
                .limit(limit)
                .offset(offset)
                .fetch()
        val ids = records.map { ServiceId(it.id) }
        val optionsByService = optionsForMany(ids)
        val mediaByService = mediaForMany(ids)
        return records.map {
            toDomain(it, optionsByService[ServiceId(it.id)] ?: emptyList(), mediaByService[ServiceId(it.id)] ?: emptyList())
        }
    }

    @Transactional
    override fun save(service: Service) {
        dsl
            .insertInto(TRAVEL_SERVICE)
            .set(TRAVEL_SERVICE.ID, service.id.value)
            .set(TRAVEL_SERVICE.CATEGORY_ID, service.categoryId.value)
            .set(TRAVEL_SERVICE.NAME_EN, service.name.en)
            .set(TRAVEL_SERVICE.NAME_AR, service.name.ar)
            .set(TRAVEL_SERVICE.DESCRIPTION_EN, service.description.en)
            .set(TRAVEL_SERVICE.DESCRIPTION_AR, service.description.ar)
            .set(TRAVEL_SERVICE.FULFILMENT_MODEL, service.fulfilmentModel.name)
            .set(TRAVEL_SERVICE.PROVIDER_ID, service.providerId?.value)
            .set(TRAVEL_SERVICE.CONFIRMATION_TYPE, service.confirmationType.name)
            .set(TRAVEL_SERVICE.CANCELLATION_POLICY_EN, service.cancellationPolicy.en)
            .set(TRAVEL_SERVICE.CANCELLATION_POLICY_AR, service.cancellationPolicy.ar)
            .set(TRAVEL_SERVICE.PICKUP_INFO_EN, service.pickupInfo?.en)
            .set(TRAVEL_SERVICE.PICKUP_INFO_AR, service.pickupInfo?.ar)
            .set(TRAVEL_SERVICE.INCLUSIONS_EN, service.inclusions?.en)
            .set(TRAVEL_SERVICE.INCLUSIONS_AR, service.inclusions?.ar)
            .set(TRAVEL_SERVICE.EXCLUSIONS_EN, service.exclusions?.en)
            .set(TRAVEL_SERVICE.EXCLUSIONS_AR, service.exclusions?.ar)
            .set(TRAVEL_SERVICE.STATUS, service.status.name)
            .set(TRAVEL_SERVICE.CREATED_AT, toOffset(service.createdAt))
            .set(TRAVEL_SERVICE.PUBLISHED_AT, service.publishedAt?.let(::toOffset))
            .set(TRAVEL_SERVICE.ARCHIVED_AT, service.archivedAt?.let(::toOffset))
            .onConflict(TRAVEL_SERVICE.ID)
            .doUpdate()
            .set(TRAVEL_SERVICE.CATEGORY_ID, service.categoryId.value)
            .set(TRAVEL_SERVICE.NAME_EN, service.name.en)
            .set(TRAVEL_SERVICE.NAME_AR, service.name.ar)
            .set(TRAVEL_SERVICE.DESCRIPTION_EN, service.description.en)
            .set(TRAVEL_SERVICE.DESCRIPTION_AR, service.description.ar)
            .set(TRAVEL_SERVICE.FULFILMENT_MODEL, service.fulfilmentModel.name)
            .set(TRAVEL_SERVICE.PROVIDER_ID, service.providerId?.value)
            .set(TRAVEL_SERVICE.CONFIRMATION_TYPE, service.confirmationType.name)
            .set(TRAVEL_SERVICE.CANCELLATION_POLICY_EN, service.cancellationPolicy.en)
            .set(TRAVEL_SERVICE.CANCELLATION_POLICY_AR, service.cancellationPolicy.ar)
            .set(TRAVEL_SERVICE.PICKUP_INFO_EN, service.pickupInfo?.en)
            .set(TRAVEL_SERVICE.PICKUP_INFO_AR, service.pickupInfo?.ar)
            .set(TRAVEL_SERVICE.INCLUSIONS_EN, service.inclusions?.en)
            .set(TRAVEL_SERVICE.INCLUSIONS_AR, service.inclusions?.ar)
            .set(TRAVEL_SERVICE.EXCLUSIONS_EN, service.exclusions?.en)
            .set(TRAVEL_SERVICE.EXCLUSIONS_AR, service.exclusions?.ar)
            .set(TRAVEL_SERVICE.STATUS, service.status.name)
            .set(TRAVEL_SERVICE.PUBLISHED_AT, service.publishedAt?.let(::toOffset))
            .set(TRAVEL_SERVICE.ARCHIVED_AT, service.archivedAt?.let(::toOffset))
            .execute()

        // Small, fully-owned child collections: replace-in-place, same
        // pattern and same reasoning as JooqDiverRepository's certifications
        // (no concurrent writer to race against beyond the row lock this
        // save() is always called under via findByIdForUpdate).
        dsl.deleteFrom(TRAVEL_SERVICE_OPTION).where(TRAVEL_SERVICE_OPTION.SERVICE_ID.eq(service.id.value)).execute()
        service.options.forEach { option ->
            dsl
                .insertInto(TRAVEL_SERVICE_OPTION)
                .set(TRAVEL_SERVICE_OPTION.ID, option.id)
                .set(TRAVEL_SERVICE_OPTION.SERVICE_ID, service.id.value)
                .set(TRAVEL_SERVICE_OPTION.LABEL_EN, option.label.en)
                .set(TRAVEL_SERVICE_OPTION.LABEL_AR, option.label.ar)
                .set(TRAVEL_SERVICE_OPTION.DURATION_MINUTES, option.durationMinutes)
                .set(TRAVEL_SERVICE_OPTION.MAX_PARTICIPANTS, option.maxParticipants)
                .set(TRAVEL_SERVICE_OPTION.PRICE_AMOUNT, option.price.amount)
                .set(TRAVEL_SERVICE_OPTION.PRICE_CURRENCY, option.price.currencyCode)
                .set(TRAVEL_SERVICE_OPTION.PRICE_BASIS, option.priceBasis.name)
                .execute()
        }

        dsl.deleteFrom(TRAVEL_SERVICE_MEDIA).where(TRAVEL_SERVICE_MEDIA.SERVICE_ID.eq(service.id.value)).execute()
        service.media.forEach { media ->
            dsl
                .insertInto(TRAVEL_SERVICE_MEDIA)
                .set(TRAVEL_SERVICE_MEDIA.ID, media.id)
                .set(TRAVEL_SERVICE_MEDIA.SERVICE_ID, service.id.value)
                .set(TRAVEL_SERVICE_MEDIA.ASSET_REFERENCE, media.assetReference)
                .set(TRAVEL_SERVICE_MEDIA.RIGHTS_EVIDENCE, media.rightsEvidence)
                .set(TRAVEL_SERVICE_MEDIA.LOCALE, media.locale)
                .execute()
        }
    }

    private fun optionsFor(id: ServiceId): List<ServiceOption> = optionsForMany(listOf(id))[id] ?: emptyList()

    private fun mediaFor(id: ServiceId): List<ServiceMedia> = mediaForMany(listOf(id))[id] ?: emptyList()

    private fun optionsForMany(ids: List<ServiceId>): Map<ServiceId, List<ServiceOption>> {
        if (ids.isEmpty()) return emptyMap()
        return dsl
            .selectFrom(TRAVEL_SERVICE_OPTION)
            .where(TRAVEL_SERVICE_OPTION.SERVICE_ID.`in`(ids.map { it.value }))
            .fetch()
            .map(::toOptionPair)
            .groupBy { ServiceId(it.first) }
            .mapValues { (_, pairs) -> pairs.map { it.second } }
    }

    private fun mediaForMany(ids: List<ServiceId>): Map<ServiceId, List<ServiceMedia>> {
        if (ids.isEmpty()) return emptyMap()
        return dsl
            .selectFrom(TRAVEL_SERVICE_MEDIA)
            .where(TRAVEL_SERVICE_MEDIA.SERVICE_ID.`in`(ids.map { it.value }))
            .fetch()
            .map(::toMediaPair)
            .groupBy { ServiceId(it.first) }
            .mapValues { (_, pairs) -> pairs.map { it.second } }
    }

    private fun toOptionPair(record: TravelServiceOptionRecord): Pair<UUID, ServiceOption> =
        record.serviceId to
            ServiceOption(
                id = record.id,
                label = LocalizedText(record.labelEn, record.labelAr),
                durationMinutes = record.durationMinutes,
                maxParticipants = record.maxParticipants,
                price = Money(record.priceAmount, record.priceCurrency),
                priceBasis = PriceBasis.valueOf(record.priceBasis),
            )

    private fun toMediaPair(record: TravelServiceMediaRecord): Pair<UUID, ServiceMedia> =
        record.serviceId to
            ServiceMedia(
                id = record.id,
                assetReference = record.assetReference,
                rightsEvidence = record.rightsEvidence,
                locale = record.locale,
            )

    private fun toDomain(
        record: TravelServiceRecord,
        options: List<ServiceOption> = optionsFor(ServiceId(record.id)),
        media: List<ServiceMedia> = mediaFor(ServiceId(record.id)),
    ): Service =
        Service(
            id = ServiceId(record.id),
            categoryId = CategoryId(record.categoryId),
            name = LocalizedText(record.nameEn, record.nameAr),
            description = LocalizedText(record.descriptionEn, record.descriptionAr),
            fulfilmentModel = FulfilmentModel.valueOf(record.fulfilmentModel),
            providerId = record.providerId?.let(::ProviderId),
            confirmationType = ConfirmationType.valueOf(record.confirmationType),
            cancellationPolicy = LocalizedText(record.cancellationPolicyEn, record.cancellationPolicyAr),
            pickupInfo =
                if (record.pickupInfoEn != null &&
                    record.pickupInfoAr != null
                ) {
                    LocalizedText(record.pickupInfoEn, record.pickupInfoAr)
                } else {
                    null
                },
            inclusions =
                if (record.inclusionsEn != null &&
                    record.inclusionsAr != null
                ) {
                    LocalizedText(record.inclusionsEn, record.inclusionsAr)
                } else {
                    null
                },
            exclusions =
                if (record.exclusionsEn != null &&
                    record.exclusionsAr != null
                ) {
                    LocalizedText(record.exclusionsEn, record.exclusionsAr)
                } else {
                    null
                },
            options = options,
            media = media,
            status = ServiceStatus.valueOf(record.status),
            createdAt = record.createdAt.toInstant(),
            publishedAt = record.publishedAt?.toInstant(),
            archivedAt = record.archivedAt?.toInstant(),
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
