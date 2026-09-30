package com.wego.toursoperator.infrastructure

import com.wego.generated.jooq.tables.ToursOperatorTourContent.TOURS_OPERATOR_TOUR_CONTENT
import com.wego.generated.jooq.tables.ToursOperatorTourFacts.TOURS_OPERATOR_TOUR_FACTS
import com.wego.generated.jooq.tables.ToursOperatorTourMedia.TOURS_OPERATOR_TOUR_MEDIA
import com.wego.toursoperator.application.PublishedTourSummary
import com.wego.toursoperator.application.TourContentRepository
import com.wego.toursoperator.domain.ContentLocale
import com.wego.toursoperator.domain.ContentStage
import com.wego.toursoperator.domain.HotelPickup
import com.wego.toursoperator.domain.MediaRightsStatus
import com.wego.toursoperator.domain.StagedDocument
import com.wego.toursoperator.domain.StopKind
import com.wego.toursoperator.domain.StopText
import com.wego.toursoperator.domain.TourContentDocument
import com.wego.toursoperator.domain.TourFactsDocument
import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.TourMedia
import com.wego.toursoperator.domain.TourStop
import com.wego.toursoperator.domain.fingerprint
import org.jooq.DSLContext
import org.jooq.JSON
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Documents are stored as jsonb and converted explicitly through plain maps,
 * so the domain classes stay free of serialization annotations and a schema
 * change is visible here in one place.
 */
@Repository("stoTourContentRepositoryImpl")
class JooqTourContentRepository(
    private val dsl: DSLContext,
    @Qualifier("stoObjectMapper") private val objectMapper: ObjectMapper,
) : TourContentRepository {
    private val log = LoggerFactory.getLogger(JooqTourContentRepository::class.java)

    @Transactional(readOnly = true)
    override fun findContent(
        tourId: TourId,
        locale: ContentLocale,
        stage: ContentStage,
    ): StagedDocument<TourContentDocument>? {
        val t = TOURS_OPERATOR_TOUR_CONTENT
        return dsl
            .selectFrom(t)
            .where(t.TOUR_ID.eq(tourId.value), t.LOCALE.eq(locale.code), t.STAGE.eq(stage.name))
            .fetchOne()
            ?.let {
                StagedDocument(
                    stage,
                    contentFromJson(it.document),
                    it.updatedAt.toInstant(),
                    it.updatedByUserId,
                    fingerprint(it.document.data()),
                )
            }
    }

    @Transactional(readOnly = true)
    override fun findAllContent(tourId: TourId): Map<ContentLocale, List<StagedDocument<TourContentDocument>>> {
        val t = TOURS_OPERATOR_TOUR_CONTENT
        return dsl
            .selectFrom(t)
            .where(t.TOUR_ID.eq(tourId.value))
            .orderBy(t.LOCALE, t.STAGE)
            .fetch()
            .groupBy({ checkNotNull(ContentLocale.fromCode(it.locale)) }) {
                StagedDocument(
                    ContentStage.valueOf(it.stage),
                    contentFromJson(it.document),
                    it.updatedAt.toInstant(),
                    it.updatedByUserId,
                    fingerprint(it.document.data()),
                )
            }
    }

    @Transactional
    override fun saveContent(
        tourId: TourId,
        locale: ContentLocale,
        stage: ContentStage,
        document: TourContentDocument,
        now: Instant,
        actorUserId: UUID?,
    ) {
        val t = TOURS_OPERATOR_TOUR_CONTENT
        val json = JSON.valueOf(contentToJson(document))
        dsl
            .insertInto(t)
            .set(t.TOUR_ID, tourId.value)
            .set(t.LOCALE, locale.code)
            .set(t.STAGE, stage.name)
            .set(t.DOCUMENT, json)
            .set(t.UPDATED_AT, toOffset(now))
            .set(t.UPDATED_BY_USER_ID, actorUserId)
            .onConflict(t.TOUR_ID, t.LOCALE, t.STAGE)
            .doUpdate()
            .set(t.DOCUMENT, json)
            .set(t.UPDATED_AT, toOffset(now))
            .set(t.UPDATED_BY_USER_ID, actorUserId)
            .execute()
    }

    @Transactional
    override fun deleteContent(
        tourId: TourId,
        locale: ContentLocale,
        stage: ContentStage,
    ): Boolean {
        val t = TOURS_OPERATOR_TOUR_CONTENT
        return dsl
            .deleteFrom(t)
            .where(t.TOUR_ID.eq(tourId.value), t.LOCALE.eq(locale.code), t.STAGE.eq(stage.name))
            .execute() == 1
    }

    @Transactional(readOnly = true)
    override fun findFacts(
        tourId: TourId,
        stage: ContentStage,
    ): StagedDocument<TourFactsDocument>? {
        val t = TOURS_OPERATOR_TOUR_FACTS
        return dsl
            .selectFrom(t)
            .where(t.TOUR_ID.eq(tourId.value), t.STAGE.eq(stage.name))
            .fetchOne()
            ?.let {
                StagedDocument(
                    stage,
                    factsFromJson(it.document),
                    it.updatedAt.toInstant(),
                    it.updatedByUserId,
                    fingerprint(it.document.data()),
                )
            }
    }

    @Transactional
    override fun saveFacts(
        tourId: TourId,
        stage: ContentStage,
        document: TourFactsDocument,
        now: Instant,
        actorUserId: UUID?,
    ) {
        val t = TOURS_OPERATOR_TOUR_FACTS
        val json = JSON.valueOf(factsToJson(document))
        dsl
            .insertInto(t)
            .set(t.TOUR_ID, tourId.value)
            .set(t.STAGE, stage.name)
            .set(t.DOCUMENT, json)
            .set(t.UPDATED_AT, toOffset(now))
            .set(t.UPDATED_BY_USER_ID, actorUserId)
            .onConflict(t.TOUR_ID, t.STAGE)
            .doUpdate()
            .set(t.DOCUMENT, json)
            .set(t.UPDATED_AT, toOffset(now))
            .set(t.UPDATED_BY_USER_ID, actorUserId)
            .execute()
    }

    @Transactional
    override fun deleteFacts(
        tourId: TourId,
        stage: ContentStage,
    ): Boolean {
        val t = TOURS_OPERATOR_TOUR_FACTS
        return dsl.deleteFrom(t).where(t.TOUR_ID.eq(tourId.value), t.STAGE.eq(stage.name)).execute() == 1
    }

    @Transactional(readOnly = true)
    override fun findMedia(tourId: TourId): List<TourMedia> {
        val m = TOURS_OPERATOR_TOUR_MEDIA
        return dsl
            .selectFrom(m)
            .where(m.TOUR_ID.eq(tourId.value))
            .orderBy(m.POSITION)
            .fetch { r ->
                TourMedia(
                    id = r.id,
                    tourId = TourId(r.tourId),
                    position = r.position,
                    path = r.path,
                    width = r.width,
                    height = r.height,
                    isCover = r.isCover,
                    alt = altFromJson(r.alt),
                    rightsStatus = MediaRightsStatus.valueOf(r.rightsStatus),
                    approvedAt = r.approvedAt?.toInstant(),
                    approvedByUserId = r.approvedByUserId,
                    createdAt = r.createdAt.toInstant(),
                )
            }
    }

    @Transactional
    override fun replaceMedia(
        tourId: TourId,
        media: List<TourMedia>,
    ) {
        val m = TOURS_OPERATOR_TOUR_MEDIA
        dsl.deleteFrom(m).where(m.TOUR_ID.eq(tourId.value)).execute()
        media.forEach { item ->
            dsl
                .insertInto(m)
                .set(m.ID, item.id)
                .set(m.TOUR_ID, tourId.value)
                .set(m.POSITION, item.position)
                .set(m.PATH, item.path)
                .set(m.WIDTH, item.width)
                .set(m.HEIGHT, item.height)
                .set(m.IS_COVER, item.isCover)
                .set(m.ALT, JSON.valueOf(objectMapper.writeValueAsString(item.alt.mapKeys { it.key.code })))
                .set(m.RIGHTS_STATUS, item.rightsStatus.name)
                .set(m.APPROVED_AT, item.approvedAt?.let(::toOffset))
                .set(m.APPROVED_BY_USER_ID, item.approvedByUserId)
                .set(m.CREATED_AT, toOffset(item.createdAt))
                .execute()
        }
    }

    @Transactional(readOnly = true)
    override fun findPublishedSummaries(
        tourIds: Collection<TourId>,
        locale: ContentLocale,
    ): Map<TourId, PublishedTourSummary> {
        if (tourIds.isEmpty()) return emptyMap()
        val t = TOURS_OPERATOR_TOUR_CONTENT
        val locales = listOf(locale, ContentLocale.EN).distinct()
        return dsl
            .selectFrom(t)
            .where(
                t.TOUR_ID.`in`(tourIds.map { it.value }),
                t.STAGE.eq(ContentStage.PUBLISHED.name),
                t.LOCALE.`in`(locales.map { it.code }),
            ).fetch()
            .groupBy { TourId(it.tourId) }
            .mapNotNull { (tourId, rows) ->
                // Requested locale first, English as the fallback. One unreadable
                // document must not break every tour card, so it is skipped.
                val summary =
                    locales.firstNotNullOfOrNull { l ->
                        rows.firstOrNull { it.locale == l.code }?.let { row ->
                            runCatching { contentFromJson(row.document) }
                                .onFailure {
                                    log.warn(
                                        "Unreadable published content for tour {} ({}): {}",
                                        tourId.value,
                                        row.locale,
                                        it.javaClass.simpleName,
                                    )
                                }.getOrNull()
                                ?.let { PublishedTourSummary(l, it.name, it.shortDescription) }
                        }
                    }
                summary?.let { tourId to it }
            }.toMap()
    }

    // ── JSON mapping ─────────────────────────────────────────────────────────

    private fun contentToJson(d: TourContentDocument): String =
        objectMapper.writeValueAsString(
            mapOf(
                "name" to d.name,
                "shortDescription" to d.shortDescription,
                "description" to d.description,
                "includes" to d.includes,
                "excludes" to d.excludes,
                "knowBeforeYouGo" to d.knowBeforeYouGo,
                "meetingPoint" to d.meetingPoint,
                "stops" to d.stops.map { mapOf("stopKey" to it.stopKey, "name" to it.name, "description" to it.description) },
            ),
        )

    private fun contentFromJson(json: JSON): TourContentDocument {
        val map = readMap(json)
        return TourContentDocument(
            name = map["name"] as String,
            shortDescription = map["shortDescription"] as String,
            description = map["description"] as String,
            includes = strings(map["includes"]),
            excludes = strings(map["excludes"]),
            knowBeforeYouGo = strings(map["knowBeforeYouGo"]),
            meetingPoint = map["meetingPoint"] as String?,
            stops =
                (map["stops"] as List<*>? ?: emptyList<Any>()).map {
                    val stop = it as Map<*, *>
                    StopText(stop["stopKey"] as String, stop["name"] as String, stop["description"] as String?)
                },
        )
    }

    private fun factsToJson(d: TourFactsDocument): String =
        objectMapper.writeValueAsString(
            mapOf(
                "childrenAllowed" to d.childrenAllowed,
                "minimumAge" to d.minimumAge,
                "guideLanguages" to d.guideLanguages,
                "hotelPickup" to d.hotelPickup?.name,
                "stops" to
                    d.stops.map {
                        mapOf("key" to it.key, "kind" to it.kind.name, "latitude" to it.latitude, "longitude" to it.longitude)
                    },
            ),
        )

    private fun factsFromJson(json: JSON): TourFactsDocument {
        val map = readMap(json)
        return TourFactsDocument(
            childrenAllowed = map["childrenAllowed"] as Boolean?,
            minimumAge = (map["minimumAge"] as Number?)?.toInt(),
            guideLanguages = strings(map["guideLanguages"]),
            hotelPickup = (map["hotelPickup"] as String?)?.let(HotelPickup::valueOf),
            stops =
                (map["stops"] as List<*>? ?: emptyList<Any>()).map {
                    val stop = it as Map<*, *>
                    TourStop(
                        key = stop["key"] as String,
                        kind = StopKind.valueOf(stop["kind"] as String),
                        latitude = (stop["latitude"] as Number).toDouble(),
                        longitude = (stop["longitude"] as Number).toDouble(),
                    )
                },
        )
    }

    private fun altFromJson(json: JSON): Map<ContentLocale, String> =
        readMap(json)
            .mapNotNull { (code, text) -> ContentLocale.fromCode(code.toString())?.let { it to text as String } }
            .toMap()

    private fun readMap(json: JSON): Map<*, *> = objectMapper.readValue(json.data(), Map::class.java)

    private fun strings(value: Any?): List<String> = (value as List<*>? ?: emptyList<Any>()).map { it as String }

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
