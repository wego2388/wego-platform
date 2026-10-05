package com.wego.toursoperator.application

import com.wego.toursoperator.domain.AssetVariantRecord
import com.wego.toursoperator.domain.CategoryMedia
import com.wego.toursoperator.domain.ContentLocale
import com.wego.toursoperator.domain.ManagedAsset
import com.wego.toursoperator.domain.MediaRightsStatus
import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.TourMedia
import com.wego.toursoperator.domain.ValidatedImage
import java.time.Clock
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Semaphore

sealed class UploadResult {
    data class Uploaded(
        val assetId: UUID,
        val mediaId: UUID? = null,
    ) : UploadResult()

    data object Done : UploadResult()

    data object TourNotFound : UploadResult()

    data object MediaNotFound : UploadResult()

    data object CategoryNotFound : UploadResult()

    data class ValidationFailed(
        val code: String,
        val detail: String,
    ) : UploadResult()

    data object StorageFailed : UploadResult()

    data object Busy : UploadResult()

    data object DraftChanged : UploadResult()

    data object NothingToPublish : UploadResult()

    data class Invalid(
        val reason: String,
    ) : UploadResult()
}

/** Only validated re-encoded assets can be linked; uploads never confer rights. */
class MediaUploadService(
    private val tourRepository: TourRepository,
    private val contentRepository: TourContentRepository,
    private val assetRepository: AssetRepository,
    private val assetStorage: AssetStorage,
    private val imageProcessor: ImageProcessor,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun uploadForTourMedia(
        tourId: TourId,
        mediaId: UUID?,
        rawBytes: ByteArray,
        alt: Map<ContentLocale, String>,
        reviewedRevision: String?,
        requestId: UUID,
    ): UploadResult {
        if (!validAlt(alt)) return UploadResult.Invalid("invalid_alt")
        if (tourRepository.findById(tourId) == null) return UploadResult.TourNotFound
        return guardedUpload(rawBytes, imageProcessor, assetStorage, assetRepository) { image, written ->
            transactionRunner.runInTransaction {
                val tour = tourRepository.findByIdForUpdate(tourId) ?: return@runInTransaction UploadResult.TourNotFound
                val media = contentRepository.findMedia(tourId)
                val replay = assetRepository.findByUploadRequest("tour_media", tourId.value.toString(), requestId)
                if (replay != null) {
                    val linked = media.firstOrNull { it.path == "/media/tours/${tour.slug}/${replay.id}.${replay.mimeType.extension}" }
                    if (replay.sha256 != image.sha256 || linked == null || linked.alt != alt || (mediaId != null && linked.id != mediaId)) {
                        return@runInTransaction UploadResult.Invalid("upload_request_reused")
                    }
                    return@runInTransaction UploadResult.Uploaded(replay.id, linked.id)
                }
                val previous = mediaId?.let { id -> media.firstOrNull { it.id == id } }
                if (mediaId != null && previous == null) return@runInTransaction UploadResult.MediaNotFound
                if (previous != null && previous.revision != reviewedRevision) return@runInTransaction UploadResult.DraftChanged
                if (previous == null && media.size >= 30) return@runInTransaction UploadResult.Invalid("too_many_media")
                val asset = persistAsset("tour_media", tourId.value.toString(), image, assetRepository, assetStorage, written, requestId)
                val id = previous?.id ?: UUID.randomUUID()
                val replacement =
                    TourMedia(
                        id,
                        tourId,
                        previous?.position ?: media.size,
                        "/media/tours/${tour.slug}/${asset.id}.${asset.mimeType.extension}",
                        image.width,
                        image.height,
                        previous?.isCover ?: media.isEmpty(),
                        alt,
                        MediaRightsStatus.DRAFT,
                        null,
                        null,
                        previous?.createdAt ?: Instant.now(clock),
                    )
                val updated = if (previous == null) media + replacement else media.map { if (it.id == id) replacement else it }
                contentRepository.replaceMedia(tourId, updated)
                UploadResult.Uploaded(asset.id, id)
            }
        }
    }
}

class CategoryMediaService(
    private val categoryMediaRepository: CategoryMediaRepository,
    private val assetRepository: AssetRepository,
    private val assetStorage: AssetStorage,
    private val imageProcessor: ImageProcessor,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun listCategories(): List<CategoryMedia> = categoryMediaRepository.findAll()

    fun getCategory(category: String): CategoryMedia? = categoryMediaRepository.findByCategory(category)

    fun revision(media: CategoryMedia): String = media.revision(media.assetId?.toString().orEmpty())

    fun uploadCover(
        category: String,
        rawBytes: ByteArray,
        alt: Map<ContentLocale, String>,
        actorUserId: UUID?,
        reviewedRevision: String,
        requestId: UUID,
    ): UploadResult {
        if (!validAlt(alt)) return UploadResult.Invalid("invalid_alt")
        if (categoryMediaRepository.findByCategory(category) == null) return UploadResult.CategoryNotFound
        return guardedUpload(rawBytes, imageProcessor, assetStorage, assetRepository) { image, written ->
            transactionRunner.runInTransaction {
                val media =
                    categoryMediaRepository.findByCategoryForUpdate(category)
                        ?: return@runInTransaction UploadResult.CategoryNotFound
                val replay = assetRepository.findByUploadRequest("category_media", category, requestId)
                if (replay != null) {
                    if (media.assetId != replay.id || replay.sha256 != image.sha256 || media.alt != alt) {
                        return@runInTransaction UploadResult.Invalid("upload_request_reused")
                    }
                    return@runInTransaction UploadResult.Uploaded(replay.id)
                }
                if (revision(media) != reviewedRevision) return@runInTransaction UploadResult.DraftChanged
                val asset = persistAsset("category_media", category, image, assetRepository, assetStorage, written, requestId)
                categoryMediaRepository.save(
                    media.copy(
                        assetId = asset.id,
                        alt = alt,
                        rightsStatus = MediaRightsStatus.DRAFT,
                        approvedAt = null,
                        approvedByUserId = null,
                        updatedAt = Instant.now(clock),
                        updatedByUserId = actorUserId,
                    ),
                )
                UploadResult.Uploaded(asset.id)
            }
        }
    }

    fun approveRights(
        category: String,
        reviewedRevision: String,
        actorUserId: UUID?,
    ): UploadResult =
        transactionRunner.runInTransaction {
            val media =
                categoryMediaRepository.findByCategoryForUpdate(category)
                    ?: return@runInTransaction UploadResult.CategoryNotFound
            val asset = media.assetId?.let(assetRepository::findById) ?: return@runInTransaction UploadResult.NothingToPublish
            if (asset.ownerType != "category_media" ||
                asset.ownerRef != category
            ) {
                return@runInTransaction UploadResult.Invalid("managed_asset_mismatch")
            }
            if (revision(media) != reviewedRevision) return@runInTransaction UploadResult.DraftChanged
            if (!media.alt.containsKey(ContentLocale.EN)) return@runInTransaction UploadResult.Invalid("english_alt_required")
            categoryMediaRepository.save(media.approve(Instant.now(clock), actorUserId))
            UploadResult.Done
        }

    fun updateAlt(
        category: String,
        alt: Map<ContentLocale, String>,
        actorUserId: UUID?,
        reviewedRevision: String,
    ): UploadResult =
        transactionRunner.runInTransaction {
            if (!validAlt(alt)) return@runInTransaction UploadResult.Invalid("invalid_alt")
            val media = categoryMediaRepository.findByCategoryForUpdate(category) ?: return@runInTransaction UploadResult.CategoryNotFound
            if (revision(media) != reviewedRevision) return@runInTransaction UploadResult.DraftChanged
            val changed = media.alt != alt
            categoryMediaRepository.save(
                media.copy(
                    alt = alt,
                    rightsStatus = if (changed) MediaRightsStatus.DRAFT else media.rightsStatus,
                    approvedAt = if (changed) null else media.approvedAt,
                    approvedByUserId = if (changed) null else media.approvedByUserId,
                    updatedAt = Instant.now(clock),
                    updatedByUserId = actorUserId,
                ),
            )
            UploadResult.Done
        }
}

private fun validAlt(alt: Map<ContentLocale, String>): Boolean = alt.values.all { it.isNotBlank() && it.length <= 200 }

private val uploadPermits = Semaphore(2)

private fun guardedUpload(
    bytes: ByteArray,
    processor: ImageProcessor,
    storage: AssetStorage,
    repository: AssetRepository,
    block: (ValidatedImage, MutableList<String>) -> UploadResult,
): UploadResult {
    if (!uploadPermits.tryAcquire()) return UploadResult.Busy
    val written = mutableListOf<String>()
    var committed = false
    try {
        val result = block(processor.process(bytes), written)
        committed = result is UploadResult.Uploaded
        return result
    } catch (e: ImageValidationException) {
        return UploadResult.ValidationFailed(e.code, "Image validation failed")
    } catch (_: AssetStorageException) {
        return UploadResult.StorageFailed
    } finally {
        // Delete only this attempt's successfully created immutable keys, never
        // older files. If cleanup fails the orphan remains private, not published.
        if (!committed && written.isNotEmpty()) {
            // A commit may have succeeded even when its acknowledgement failed.
            // Reconcile outside the transaction; unknown state retains private
            // orphans, never deletes bytes potentially linked by a committed row.
            val absent =
                runCatching {
                    written
                        .map { UUID.fromString(it.substringAfterLast('/').substringBefore('_').substringBefore('.')) }
                        .distinct()
                        .all { repository.findById(it) == null }
                }.getOrDefault(false)
            if (absent) written.asReversed().forEach { key -> runCatching { storage.delete(key) } }
        }
        uploadPermits.release()
    }
}

private fun persistAsset(
    ownerType: String,
    ownerRef: String,
    image: ValidatedImage,
    repository: AssetRepository,
    storage: AssetStorage,
    written: MutableList<String>,
    requestId: UUID,
): ManagedAsset {
    val id = UUID.randomUUID()
    val key = "assets/$ownerType/$ownerRef/$id.${image.mimeType.extension}"
    val variants =
        image.variants.map { variant ->
            val variantKey = key.replace(".${image.mimeType.extension}", "_${variant.variant.label}.${image.mimeType.extension}")
            storage.write(variantKey, variant.bytes)
            written.add(variantKey)
            AssetVariantRecord(id, variant.variant, variantKey, variant.width, variant.height, variant.bytes.size.toLong())
        }
    storage.write(key, image.processedBytes)
    written.add(key)
    return repository.save(
        ManagedAsset(
            id,
            ownerType,
            ownerRef,
            key,
            image.mimeType,
            image.width,
            image.height,
            image.fileSizeBytes,
            image.sha256,
            variants,
            requestId,
        ),
    )
}
