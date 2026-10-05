package com.wego.toursoperator.application

import com.wego.toursoperator.domain.ContentLocale
import com.wego.toursoperator.domain.ContentStage
import com.wego.toursoperator.domain.MediaRightsStatus
import com.wego.toursoperator.domain.StopKind
import com.wego.toursoperator.domain.Tour
import com.wego.toursoperator.domain.TourContentDocument
import com.wego.toursoperator.domain.TourFactsDocument

data class PublicStop(
    val key: String,
    val kind: StopKind,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val description: String?,
)

data class PublicMedia(
    val path: String,
    val width: Int,
    val height: Int,
    val isCover: Boolean,
    /** Alt in the served locale, else English. */
    val alt: String,
)

/** What the public site may show for one tour: published and approved material only. */
data class PublicTourContent(
    val tour: Tour,
    val requestedLocale: ContentLocale,
    /** The locale the text is actually in (requested, or English fallback); null when nothing is published. */
    val servedLocale: ContentLocale?,
    val content: TourContentDocument?,
    val facts: TourFactsDocument?,
    val stops: List<PublicStop>,
    val media: List<PublicMedia>,
)

class PublicTourContentQuery(
    private val tourRepository: TourRepository,
    private val contentRepository: TourContentRepository,
    private val transactionRunner: TransactionRunner,
) {
    /** Published names for tour cards; tours without published content are absent. */
    fun publishedSummaries(
        tourIds: Collection<com.wego.toursoperator.domain.TourId>,
        locale: ContentLocale,
    ): Map<com.wego.toursoperator.domain.TourId, PublishedTourSummary> = contentRepository.findPublishedSummaries(tourIds, locale)

    fun approvedCovers(
        tourIds: Collection<com.wego.toursoperator.domain.TourId>,
        locale: ContentLocale,
    ): Map<com.wego.toursoperator.domain.TourId, PublicMedia> = contentRepository.findApprovedCovers(tourIds, locale)

    /** Null when the tour does not exist or is not publicly active. */
    fun forSlug(
        slug: String,
        requested: ContentLocale,
    ): PublicTourContent? =
        // The transaction runner cannot return null, so absence travels in a list.
        transactionRunner
            .runInTransaction {
                val tour = tourRepository.findBySlug(slug)?.takeIf { it.isActive } ?: return@runInTransaction emptyList()
                val (servedLocale, content) =
                    listOf(requested, ContentLocale.EN)
                        .distinct()
                        .firstNotNullOfOrNull { locale ->
                            contentRepository.findContent(tour.id, locale, ContentStage.PUBLISHED)?.let { locale to it.document }
                        } ?: (null to null)
                val facts = contentRepository.findFacts(tour.id, ContentStage.PUBLISHED)?.document
                // A stop is shown only with both published coordinates and published text.
                val texts = content?.stops?.associateBy { it.stopKey }.orEmpty()
                val stops =
                    facts?.stops.orEmpty().mapNotNull { stop ->
                        texts[stop.key]?.let { text ->
                            PublicStop(stop.key, stop.kind, stop.latitude, stop.longitude, text.name, text.description)
                        }
                    }
                val media =
                    contentRepository
                        .findMedia(tour.id)
                        .filter { it.rightsStatus == MediaRightsStatus.APPROVED }
                        .mapNotNull { item ->
                            val alt = item.alt[servedLocale ?: requested] ?: item.alt[ContentLocale.EN] ?: return@mapNotNull null
                            PublicMedia(item.path, item.width, item.height, item.isCover, alt)
                        }
                listOf(PublicTourContent(tour, requested, servedLocale, content, facts, stops, media))
            }.firstOrNull()
}
