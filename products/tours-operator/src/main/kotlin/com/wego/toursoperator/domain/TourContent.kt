package com.wego.toursoperator.domain

import java.time.Instant
import java.util.UUID

/** The four customer-facing languages. */
enum class ContentLocale(
    val code: String,
) {
    EN("en"),
    AR("ar"),
    RU("ru"),
    IT("it"),
    ;

    companion object {
        fun fromCode(code: String): ContentLocale? = entries.firstOrNull { it.code == code.lowercase() }
    }
}

/** DRAFT is what staff edit; PUBLISHED is the approved copy the public site serves. */
enum class ContentStage {
    DRAFT,
    PUBLISHED,
}

/** Localized text of one itinerary stop, keyed by the stop's stable key in [TourFacts]. */
data class StopText(
    val stopKey: String,
    val name: String,
    val description: String?,
) {
    init {
        require(stopKey.matches(KEY_FORMAT)) { "stopKey must be lowercase letters, digits or hyphens" }
        require(name.isNotBlank() && name.length <= 120) { "stop name must be 1–120 characters" }
        require(description == null || description.length <= 500) { "stop description must be at most 500 characters" }
    }
}

/**
 * Everything a customer reads about a tour in one language. Validation
 * keeps documents bounded so a page can always render them.
 */
data class TourContentDocument(
    val name: String,
    val shortDescription: String,
    val description: String,
    val includes: List<String> = emptyList(),
    val excludes: List<String> = emptyList(),
    val knowBeforeYouGo: List<String> = emptyList(),
    val meetingPoint: String? = null,
    val stops: List<StopText> = emptyList(),
) {
    init {
        require(name.isNotBlank() && name.length <= 120) { "name must be 1–120 characters" }
        require(shortDescription.isNotBlank() && shortDescription.length <= 300) { "shortDescription must be 1–300 characters" }
        require(description.isNotBlank() && description.length <= 6000) { "description must be 1–6000 characters" }
        listOf("includes" to includes, "excludes" to excludes, "knowBeforeYouGo" to knowBeforeYouGo).forEach { (field, items) ->
            require(items.size <= MAX_LIST_ITEMS) { "$field may have at most $MAX_LIST_ITEMS items" }
            require(items.all { it.isNotBlank() && it.length <= 200 }) { "$field items must be 1–200 characters" }
        }
        require(meetingPoint == null || meetingPoint.length <= 300) { "meetingPoint must be at most 300 characters" }
        require(stops.size <= MAX_STOPS) { "at most $MAX_STOPS stops" }
        require(stops.map { it.stopKey }.toSet().size == stops.size) { "stop keys must be unique" }
    }
}

enum class HotelPickup {
    INCLUDED,
    NOT_INCLUDED,
    SOME_AREAS,
}

enum class StopKind {
    STOP,
    MEETING_POINT,
}

data class TourStop(
    val key: String,
    val kind: StopKind,
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(key.matches(KEY_FORMAT)) { "stop key must be lowercase letters, digits or hyphens" }
        require(latitude in -90.0..90.0 && longitude in -180.0..180.0) { "stop coordinates out of range" }
    }
}

/** Locale-independent facts. Null means "not stated" — the site then says nothing. */
data class TourFactsDocument(
    val childrenAllowed: Boolean? = null,
    val minimumAge: Int? = null,
    val guideLanguages: List<String> = emptyList(),
    val hotelPickup: HotelPickup? = null,
    val stops: List<TourStop> = emptyList(),
) {
    init {
        require(minimumAge == null || minimumAge in 0..99) { "minimumAge must be 0–99" }
        require(guideLanguages.size <= 10 && guideLanguages.toSet().size == guideLanguages.size) {
            "guideLanguages must be at most 10 distinct codes"
        }
        require(guideLanguages.all { it.matches(Regex("^[a-z]{2}$")) }) { "guideLanguages must be ISO 639-1 codes" }
        require(stops.size <= MAX_STOPS) { "at most $MAX_STOPS stops" }
        require(stops.map { it.key }.toSet().size == stops.size) { "stop keys must be unique" }
        require(stops.count { it.kind == StopKind.MEETING_POINT } <= 1) { "at most one meeting point" }
    }
}

/**
 * A stored content or facts document with its audit fields. [revision] is a
 * fingerprint of the stored document: a publisher sends back the revision
 * they reviewed, so an edit made meanwhile can never be published unseen.
 */
data class StagedDocument<T>(
    val stage: ContentStage,
    val document: T,
    val updatedAt: Instant,
    val updatedByUserId: UUID?,
    val revision: String,
)

enum class MediaRightsStatus {
    DRAFT,
    APPROVED,
}

/**
 * A tour image. Only APPROVED media is public: rights must be confirmed by a
 * publisher before a picture reaches the site.
 */
class TourMedia(
    val id: UUID,
    val tourId: TourId,
    val position: Int,
    val path: String,
    val width: Int,
    val height: Int,
    val isCover: Boolean,
    val alt: Map<ContentLocale, String>,
    rightsStatus: MediaRightsStatus,
    approvedAt: Instant?,
    approvedByUserId: UUID?,
    val createdAt: Instant,
) {
    var rightsStatus: MediaRightsStatus = rightsStatus
        private set

    var approvedAt: Instant? = approvedAt
        private set

    var approvedByUserId: UUID? = approvedByUserId
        private set

    init {
        require(position >= 0) { "position must not be negative" }
        require(width > 0 && height > 0) { "dimensions must be positive" }
        require(path.length <= 300 && path.matches(MEDIA_PATH)) {
            "path must be /media/tours/<slug>/<file>.(avif|webp|jpg|jpeg|png), at most 300 characters"
        }
        require(alt.values.all { it.isNotBlank() && it.length <= 200 }) { "alt text must be 1–200 characters" }
        require((rightsStatus == MediaRightsStatus.APPROVED) == (approvedAt != null)) {
            "approvedAt must be set if and only if rights are APPROVED"
        }
    }

    /**
     * Fingerprint of everything a publisher reviews before approving rights:
     * the file and its alt texts. Dimensions, order and cover flag are layout
     * and do not affect it.
     */
    val revision: String
        get() =
            // Length-prefixed parts cannot be re-split into different values.
            fingerprint(
                buildString {
                    append(path.length).append(':').append(path)
                    ContentLocale.entries.forEach { locale ->
                        alt[locale]?.let {
                            append('|')
                                .append(locale.code)
                                .append(':')
                                .append(it.length)
                                .append(':')
                                .append(it)
                        }
                    }
                },
            )

    fun approve(
        now: Instant,
        actorUserId: UUID?,
    ) {
        require(alt.containsKey(ContentLocale.EN)) { "media needs at least English alt text before approval" }
        rightsStatus = MediaRightsStatus.APPROVED
        approvedAt = now
        approvedByUserId = actorUserId
    }

    companion object {
        val MEDIA_PATH = Regex("^/media/tours/[a-z0-9-]+/[a-z0-9._-]+\\.(avif|webp|jpg|jpeg|png)$")
    }
}

/** Short, stable SHA-256 fingerprint used as a review revision. */
fun fingerprint(value: String): String =
    java.security.MessageDigest
        .getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .take(12)
        .joinToString("") { "%02x".format(it) }

private val KEY_FORMAT = Regex("^[a-z0-9][a-z0-9-]{0,39}$")
private const val MAX_LIST_ITEMS = 20
private const val MAX_STOPS = 12
