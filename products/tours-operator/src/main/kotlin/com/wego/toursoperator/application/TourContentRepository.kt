package com.wego.toursoperator.application

import com.wego.toursoperator.domain.ContentLocale
import com.wego.toursoperator.domain.ContentStage
import com.wego.toursoperator.domain.StagedDocument
import com.wego.toursoperator.domain.TourContentDocument
import com.wego.toursoperator.domain.TourFactsDocument
import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.TourMedia
import java.time.Instant
import java.util.UUID

/** Published localized name and summary used on tour cards. */
data class PublishedTourSummary(
    val locale: ContentLocale,
    val name: String,
    val shortDescription: String,
)

interface TourContentRepository {
    fun findContent(
        tourId: TourId,
        locale: ContentLocale,
        stage: ContentStage,
    ): StagedDocument<TourContentDocument>?

    fun findAllContent(tourId: TourId): Map<ContentLocale, List<StagedDocument<TourContentDocument>>>

    fun saveContent(
        tourId: TourId,
        locale: ContentLocale,
        stage: ContentStage,
        document: TourContentDocument,
        now: Instant,
        actorUserId: UUID?,
    )

    fun deleteContent(
        tourId: TourId,
        locale: ContentLocale,
        stage: ContentStage,
    ): Boolean

    fun findFacts(
        tourId: TourId,
        stage: ContentStage,
    ): StagedDocument<TourFactsDocument>?

    fun saveFacts(
        tourId: TourId,
        stage: ContentStage,
        document: TourFactsDocument,
        now: Instant,
        actorUserId: UUID?,
    )

    fun deleteFacts(
        tourId: TourId,
        stage: ContentStage,
    ): Boolean

    /** Ordered by position. */
    fun findMedia(tourId: TourId): List<TourMedia>

    /** Replaces the tour's whole media list in one statement set. */
    fun replaceMedia(
        tourId: TourId,
        media: List<TourMedia>,
    )

    /**
     * Published names for many tours at once (card lists): the requested
     * locale when published, otherwise published English, otherwise absent.
     */
    fun findPublishedSummaries(
        tourIds: Collection<TourId>,
        locale: ContentLocale,
    ): Map<TourId, PublishedTourSummary>
}
