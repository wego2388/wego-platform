package com.wego.toursoperator.application

import com.wego.toursoperator.domain.ContentLocale
import com.wego.toursoperator.domain.ContentStage
import com.wego.toursoperator.domain.MediaRightsStatus
import com.wego.toursoperator.domain.StagedDocument
import com.wego.toursoperator.domain.TourContentDocument
import com.wego.toursoperator.domain.TourFactsDocument
import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.TourMedia
import com.wego.toursoperator.domain.fingerprint
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** Everything staff see for one tour: both stages of every document plus media. */
data class StaffTourContent(
    val content: Map<ContentLocale, List<StagedDocument<TourContentDocument>>>,
    val facts: List<StagedDocument<TourFactsDocument>>,
    val media: List<TourMedia>,
) {
    val mediaRevision: String get() = mediaListRevision(media)
}

private fun mediaListRevision(media: List<TourMedia>): String =
    fingerprint(
        media.sortedBy { it.position }.joinToString("|") {
            "${it.id}:${it.position}:${it.revision}:${it.width}:${it.height}:${it.isCover}:${it.rightsStatus}:${it.approvedAt}"
        },
    )

/** A media item as staff submit it; identity and approval are decided by the service. */
data class MediaInput(
    val id: UUID?,
    val path: String,
    val width: Int,
    val height: Int,
    val isCover: Boolean,
    val alt: Map<ContentLocale, String>,
)

sealed class ContentCommandResult {
    data object Done : ContentCommandResult()

    data object TourNotFound : ContentCommandResult()

    data object NothingToPublish : ContentCommandResult()

    /** The draft changed after the publisher reviewed it (revision mismatch). */
    data object DraftChanged : ContentCommandResult()

    data object MediaNotFound : ContentCommandResult()

    data class Invalid(
        val reason: String,
    ) : ContentCommandResult()
}

/**
 * Staff editing and publishing of tour content. Drafts are free to change;
 * publishing copies the draft to the PUBLISHED stage, which is the only stage
 * the public site reads. Unpublishing removes a locale (or the facts) from the
 * site without touching the draft.
 */
class TourContentService(
    private val tourRepository: TourRepository,
    private val contentRepository: TourContentRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
    private val assetRepository: AssetRepository,
) {
    fun staffView(tourId: TourId): StaffTourContent? =
        // The transaction runner cannot return null, so absence travels in a list.
        transactionRunner
            .runInTransaction {
                tourRepository.findById(tourId) ?: return@runInTransaction emptyList()
                listOf(
                    StaffTourContent(
                        content = contentRepository.findAllContent(tourId),
                        facts =
                            listOfNotNull(
                                contentRepository.findFacts(tourId, ContentStage.DRAFT),
                                contentRepository.findFacts(tourId, ContentStage.PUBLISHED),
                            ),
                        media = contentRepository.findMedia(tourId),
                    ),
                )
            }.firstOrNull()

    fun saveContentDraft(
        tourId: TourId,
        locale: ContentLocale,
        document: TourContentDocument,
        actorUserId: UUID?,
    ): ContentCommandResult =
        inTourTransaction(tourId) {
            contentRepository.saveContent(tourId, locale, ContentStage.DRAFT, document, Instant.now(clock), actorUserId)
            ContentCommandResult.Done
        }

    fun publishContent(
        tourId: TourId,
        locale: ContentLocale,
        reviewedRevision: String,
        actorUserId: UUID?,
    ): ContentCommandResult =
        inTourTransaction(tourId) {
            val draft =
                contentRepository.findContent(tourId, locale, ContentStage.DRAFT)
                    ?: return@inTourTransaction ContentCommandResult.NothingToPublish
            if (draft.revision != reviewedRevision) return@inTourTransaction ContentCommandResult.DraftChanged
            contentRepository.saveContent(tourId, locale, ContentStage.PUBLISHED, draft.document, Instant.now(clock), actorUserId)
            ContentCommandResult.Done
        }

    fun unpublishContent(
        tourId: TourId,
        locale: ContentLocale,
    ): ContentCommandResult =
        inTourTransaction(tourId) {
            if (contentRepository.deleteContent(tourId, locale, ContentStage.PUBLISHED)) {
                ContentCommandResult.Done
            } else {
                ContentCommandResult.NothingToPublish
            }
        }

    fun saveFactsDraft(
        tourId: TourId,
        document: TourFactsDocument,
        actorUserId: UUID?,
    ): ContentCommandResult =
        inTourTransaction(tourId) {
            contentRepository.saveFacts(tourId, ContentStage.DRAFT, document, Instant.now(clock), actorUserId)
            ContentCommandResult.Done
        }

    fun publishFacts(
        tourId: TourId,
        reviewedRevision: String,
        actorUserId: UUID?,
    ): ContentCommandResult =
        inTourTransaction(tourId) {
            val draft =
                contentRepository.findFacts(tourId, ContentStage.DRAFT)
                    ?: return@inTourTransaction ContentCommandResult.NothingToPublish
            if (draft.revision != reviewedRevision) return@inTourTransaction ContentCommandResult.DraftChanged
            contentRepository.saveFacts(tourId, ContentStage.PUBLISHED, draft.document, Instant.now(clock), actorUserId)
            ContentCommandResult.Done
        }

    fun unpublishFacts(tourId: TourId): ContentCommandResult =
        inTourTransaction(tourId) {
            if (contentRepository.deleteFacts(tourId, ContentStage.PUBLISHED)) {
                ContentCommandResult.Done
            } else {
                ContentCommandResult.NothingToPublish
            }
        }

    /**
     * Replaces the media list. An item keeps its rights approval only while
     * its id, file and alt texts are unchanged (same revision); anything a
     * publisher has not seen starts as DRAFT and needs approval again. Layout
     * changes (order, size, cover flag) keep approval.
     */
    fun replaceMedia(
        tourId: TourId,
        items: List<MediaInput>,
        reviewedMediaRevision: String? = null,
    ): ContentCommandResult =
        inTourTransaction(tourId) {
            if (items.size > MAX_MEDIA) return@inTourTransaction ContentCommandResult.Invalid("too_many_media")
            if (items.count { it.isCover } > 1) return@inTourTransaction ContentCommandResult.Invalid("multiple_covers")
            if (items.map { it.path }.toSet().size != items.size) {
                return@inTourTransaction ContentCommandResult.Invalid("duplicate_media_path")
            }
            val ids = items.mapNotNull { it.id }
            if (ids.toSet().size != ids.size) return@inTourTransaction ContentCommandResult.Invalid("duplicate_media_id")
            val existing = contentRepository.findMedia(tourId).associateBy { it.id }
            val managedList =
                existing.values.any { MANAGED_ID.containsMatchIn(it.path) } || items.any { MANAGED_ID.containsMatchIn(it.path) }
            if (managedList && reviewedMediaRevision == null) {
                return@inTourTransaction ContentCommandResult.Invalid("media_revision_required")
            }
            if (reviewedMediaRevision != null && reviewedMediaRevision != mediaListRevision(existing.values.toList())) {
                return@inTourTransaction ContentCommandResult.DraftChanged
            }
            val tour = requireNotNull(tourRepository.findById(tourId))
            items.forEach { input ->
                val assetId = MANAGED_ID.find(input.path)?.value?.let(UUID::fromString)
                if (assetId != null) {
                    val asset =
                        assetRepository.findById(assetId)
                            ?: return@inTourTransaction ContentCommandResult.Invalid("unknown_managed_asset")
                    val expectedPath = "/media/tours/${tour.slug}/${asset.id}.${asset.mimeType.extension}"
                    if (asset.ownerType != "tour_media" ||
                        asset.ownerRef != tourId.value.toString() ||
                        input.path != expectedPath ||
                        input.width != asset.originalWidth ||
                        input.height != asset.originalHeight
                    ) {
                        return@inTourTransaction ContentCommandResult.Invalid("managed_asset_mismatch")
                    }
                }
            }
            val now = Instant.now(clock)
            val media =
                items.mapIndexed { position, input ->
                    val previous = input.id?.let(existing::get)
                    val candidate =
                        TourMedia(
                            id = previous?.id ?: UUID.randomUUID(),
                            tourId = tourId,
                            position = position,
                            path = input.path,
                            width = input.width,
                            height = input.height,
                            isCover = input.isCover,
                            alt = input.alt,
                            rightsStatus = MediaRightsStatus.DRAFT,
                            approvedAt = null,
                            approvedByUserId = null,
                            createdAt = previous?.createdAt ?: now,
                        )
                    val unchanged =
                        previous != null && previous.rightsStatus == MediaRightsStatus.APPROVED && previous.revision == candidate.revision
                    if (!unchanged) {
                        candidate
                    } else {
                        TourMedia(
                            candidate.id,
                            tourId,
                            position,
                            candidate.path,
                            candidate.width,
                            candidate.height,
                            candidate.isCover,
                            candidate.alt,
                            MediaRightsStatus.APPROVED,
                            previous!!.approvedAt,
                            previous.approvedByUserId,
                            candidate.createdAt,
                        )
                    }
                }
            contentRepository.replaceMedia(tourId, media)
            ContentCommandResult.Done
        }

    fun approveMedia(
        tourId: TourId,
        mediaId: UUID,
        reviewedRevision: String,
        actorUserId: UUID?,
    ): ContentCommandResult =
        inTourTransaction(tourId) {
            val media = contentRepository.findMedia(tourId)
            val target = media.firstOrNull { it.id == mediaId } ?: return@inTourTransaction ContentCommandResult.MediaNotFound
            if (target.revision != reviewedRevision) return@inTourTransaction ContentCommandResult.DraftChanged
            if (!target.alt.containsKey(ContentLocale.EN)) {
                return@inTourTransaction ContentCommandResult.Invalid("english_alt_required")
            }
            target.approve(Instant.now(clock), actorUserId)
            contentRepository.replaceMedia(tourId, media)
            ContentCommandResult.Done
        }

    /** Locks the tour row so concurrent edits of one tour's content serialize. */
    private fun inTourTransaction(
        tourId: TourId,
        block: () -> ContentCommandResult,
    ): ContentCommandResult =
        transactionRunner.runInTransaction {
            tourRepository.findByIdForUpdate(tourId) ?: return@runInTransaction ContentCommandResult.TourNotFound
            block()
        }

    private companion object {
        const val MAX_MEDIA = 30
        val MANAGED_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
