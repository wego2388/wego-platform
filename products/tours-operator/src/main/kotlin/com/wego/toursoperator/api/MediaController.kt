package com.wego.toursoperator.api

import com.wego.identity.AuthenticatedUser
import com.wego.toursoperator.application.AssetRepository
import com.wego.toursoperator.application.AssetStorage
import com.wego.toursoperator.application.CategoryMediaRepository
import com.wego.toursoperator.application.CategoryMediaService
import com.wego.toursoperator.application.ImageProcessor
import com.wego.toursoperator.application.MediaUploadService
import com.wego.toursoperator.application.UploadResult
import com.wego.toursoperator.domain.AssetMimeType
import com.wego.toursoperator.domain.CategoryMedia
import com.wego.toursoperator.domain.ContentLocale
import com.wego.toursoperator.domain.ManagedAsset
import com.wego.toursoperator.domain.MediaRightsStatus
import com.wego.toursoperator.domain.TourId
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.Instant
import java.util.UUID

@RestController("toursOperatorMediaController")
@RequestMapping("/api/v1/tours-operator")
class MediaController(
    private val mediaUploadService: MediaUploadService,
    private val categoryMediaService: CategoryMediaService,
    private val assetRepository: AssetRepository,
    private val assetStorage: AssetStorage,
) {
    @PostMapping("/staff/tours/{tourId}/media/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @PreAuthorize("hasAuthority('tours-operator.media:upload') and hasAuthority('tours-operator.tour:manage')")
    fun uploadTourMedia(
        @PathVariable tourId: UUID,
        @RequestParam("file") file: MultipartFile,
        @RequestParam requestId: UUID,
        @RequestParam(required = false) mediaId: UUID?,
        @RequestParam(required = false) revision: String?,
        @RequestParam(required = false) altEn: String?,
        @RequestParam(required = false) altAr: String?,
        @RequestParam(required = false) altRu: String?,
        @RequestParam(required = false) altIt: String?,
    ): ResponseEntity<Any> {
        fileError(file)?.let { return it }
        return response(
            mediaUploadService.uploadForTourMedia(
                TourId(tourId),
                mediaId,
                file.bytes,
                alt(altEn, altAr, altRu, altIt),
                revision,
                requestId,
            ),
        )
    }

    @PostMapping("/staff/categories/{category}/media/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @PreAuthorize("hasAuthority('tours-operator.media:upload') and hasAuthority('tours-operator.tour:manage')")
    fun uploadCategoryMedia(
        @PathVariable category: String,
        @RequestParam("file") file: MultipartFile,
        @RequestParam requestId: UUID,
        @RequestParam revision: String,
        @RequestParam(required = false) altEn: String?,
        @RequestParam(required = false) altAr: String?,
        @RequestParam(required = false) altRu: String?,
        @RequestParam(required = false) altIt: String?,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        fileError(file)?.let { return it }
        return response(
            categoryMediaService.uploadCover(
                category,
                file.bytes,
                alt(altEn, altAr, altRu, altIt),
                actor(authentication),
                revision,
                requestId,
            ),
        )
    }

    @PutMapping("/staff/categories/{category}/media/alt")
    @PreAuthorize("hasAuthority('tours-operator.tour:manage')")
    fun updateCategoryAlt(
        @PathVariable category: String,
        @RequestBody request: AltUpdateRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> =
        response(
            categoryMediaService.updateAlt(
                category,
                alt(request.altEn, request.altAr, request.altRu, request.altIt),
                actor(authentication),
                request.revision,
            ),
        )

    @PostMapping("/staff/categories/{category}/media/approve")
    @PreAuthorize("hasAuthority('tours-operator.content:publish')")
    fun approveCategoryRights(
        @PathVariable category: String,
        @RequestBody request: ApproveRightsRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> = response(categoryMediaService.approveRights(category, request.revision, actor(authentication)))

    @GetMapping("/staff/categories/media")
    @PreAuthorize("hasAuthority('tours-operator.tour:view')")
    fun listCategoryMedia(): List<CategoryMediaResponse> = categoryMediaService.listCategories().map(::categoryResponse)

    @GetMapping("/staff/categories/{category}/media")
    @PreAuthorize("hasAuthority('tours-operator.tour:view')")
    fun getCategoryMedia(
        @PathVariable category: String,
    ): ResponseEntity<CategoryMediaResponse> =
        categoryMediaService.getCategory(category)?.let { ResponseEntity.ok(categoryResponse(it)) } ?: ResponseEntity.notFound().build()

    @GetMapping("/staff/media/preview/{assetId}")
    @PreAuthorize("hasAuthority('tours-operator.tour:view')")
    fun staffPreview(
        @PathVariable assetId: UUID,
        @RequestParam(defaultValue = "base") variant: String,
    ): ResponseEntity<ByteArray> {
        val asset = assetRepository.findById(assetId) ?: return missingMedia()
        val key = asset.variantKey(variant) ?: return missingMedia()
        val bytes = assetStorage.read(key) ?: return missingMedia()
        return mediaResponse(asset).header("X-Robots-Tag", "noindex, nofollow").body(bytes)
    }

    private fun categoryResponse(media: CategoryMedia): CategoryMediaResponse {
        val asset = media.assetId?.let(assetRepository::findById)
        return CategoryMediaResponse(
            media.category,
            media.assetId,
            media.alt.mapKeys { it.key.code },
            media.rightsStatus.name,
            media.approvedAt,
            media.updatedAt,
            categoryMediaService.revision(media),
            asset?.let { "/media/categories/${media.category}/${it.id}.${it.mimeType.extension}" },
            asset?.originalWidth,
            asset?.originalHeight,
        )
    }

    private fun actor(auth: Authentication): UUID? = (auth.principal as? AuthenticatedUser)?.userId
}

/** Exact ownership, current linkage, rights and activity are checked on every byte read. */
@RestController("toursOperatorPublicMediaServingController")
class PublicMediaServingController(
    private val assetRepository: AssetRepository,
    private val categoryMediaRepository: CategoryMediaRepository,
    private val assetStorage: AssetStorage,
) {
    @GetMapping("/api/v1/tours-operator/categories/media")
    fun categoryCovers(
        @RequestParam(defaultValue = "en") locale: String,
    ): ResponseEntity<Any> {
        val requested = ContentLocale.fromCode(locale) ?: return ResponseEntity.badRequest().body(UploadErrorResponse("unsupported_locale"))
        val covers =
            categoryMediaRepository.findAll().filter { it.rightsStatus == MediaRightsStatus.APPROVED }.mapNotNull { media ->
                val asset = media.assetId?.let(assetRepository::findById) ?: return@mapNotNull null
                if (asset.ownerType != "category_media" || asset.ownerRef != media.category) return@mapNotNull null
                val alt = media.alt[requested] ?: media.alt[ContentLocale.EN] ?: return@mapNotNull null
                PublicCategoryCover(
                    media.category,
                    "/media/categories/${media.category}/${asset.id}.${asset.mimeType.extension}",
                    asset.originalWidth,
                    asset.originalHeight,
                    alt,
                )
            }
        return ResponseEntity.ok(covers)
    }

    @GetMapping("/media/{type}/{ref}/{filename:.+}")
    fun serveMedia(
        @PathVariable type: String,
        @PathVariable ref: String,
        @PathVariable filename: String,
        @RequestParam(defaultValue = "base") v: String,
    ): ResponseEntity<ByteArray> {
        if (!filename.matches(FILE_PATTERN)) return missingMedia()
        val id = UUID.fromString(filename.substringBefore('.'))
        val asset = assetRepository.findById(id) ?: return missingMedia()
        if (filename != "${asset.id}.${asset.mimeType.extension}") return missingMedia()
        val path = "/media/$type/$ref/$filename"
        val allowed =
            when (type) {
                "tours" -> asset.ownerType == "tour_media" && assetRepository.isTourMediaApproved(id, path)
                "categories" ->
                    asset.ownerType == "category_media" &&
                        asset.ownerRef == ref &&
                        categoryMediaRepository
                            .findByCategory(
                                ref,
                            )?.let { it.assetId == id && it.rightsStatus == MediaRightsStatus.APPROVED } ==
                        true
                else -> false
            }
        if (!allowed) return missingMedia()
        val key = asset.variantKey(v) ?: return missingMedia()
        val bytes = assetStorage.read(key) ?: return missingMedia()
        return mediaResponse(asset).body(bytes)
    }

    private companion object {
        val FILE_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)")
    }
}

private fun alt(
    en: String?,
    ar: String?,
    ru: String?,
    it: String?,
): Map<ContentLocale, String> =
    listOf(ContentLocale.EN to en, ContentLocale.AR to ar, ContentLocale.RU to ru, ContentLocale.IT to it)
        .mapNotNull { (locale, text) -> text?.trim()?.takeIf(String::isNotEmpty)?.let { locale to it } }
        .toMap()

private fun fileError(file: MultipartFile): ResponseEntity<Any>? =
    when {
        file.isEmpty -> ResponseEntity.badRequest().body(UploadErrorResponse("empty_file"))
        file.size > ImageProcessor.MAX_FILE_SIZE_BYTES -> ResponseEntity.status(413).body(UploadErrorResponse("file_too_large"))
        else -> null
    }

private fun response(result: UploadResult): ResponseEntity<Any> =
    when (result) {
        is UploadResult.Uploaded -> ResponseEntity.ok(UploadResponse(result.assetId, result.mediaId))
        UploadResult.Done -> ResponseEntity.noContent().build()
        UploadResult.TourNotFound, UploadResult.MediaNotFound, UploadResult.CategoryNotFound -> ResponseEntity.notFound().build()
        is UploadResult.Invalid -> ResponseEntity.badRequest().body(UploadErrorResponse(result.reason))
        is UploadResult.ValidationFailed -> ResponseEntity.badRequest().body(UploadErrorResponse(result.code))
        UploadResult.DraftChanged -> ResponseEntity.status(409).body(UploadErrorResponse("draft_changed"))
        UploadResult.NothingToPublish -> ResponseEntity.status(409).body(UploadErrorResponse("no_asset"))
        UploadResult.StorageFailed -> ResponseEntity.status(503).body(UploadErrorResponse("storage_failed"))
        UploadResult.Busy -> ResponseEntity.status(503).header("Retry-After", "3").body(UploadErrorResponse("upload_busy"))
    }

private fun ManagedAsset.variantKey(label: String): String? =
    if (label == "base") storageKey else variants.firstOrNull { it.variant.label == label }?.storageKey

private fun mediaResponse(asset: ManagedAsset): ResponseEntity.BodyBuilder =
    ResponseEntity
        .ok()
        .contentType(if (asset.mimeType == AssetMimeType.PNG) MediaType.IMAGE_PNG else MediaType.IMAGE_JPEG)
        .header("Cache-Control", "no-store, private")
        .header("X-Content-Type-Options", "nosniff")

private fun missingMedia(): ResponseEntity<ByteArray> = ResponseEntity.status(404).header("Cache-Control", "no-store, private").build()

data class UploadErrorResponse(
    val error: String,
)

data class UploadResponse(
    val assetId: UUID,
    val mediaId: UUID?,
)

data class AltUpdateRequest(
    val revision: String,
    val altEn: String? = null,
    val altAr: String? = null,
    val altRu: String? = null,
    val altIt: String? = null,
)

data class ApproveRightsRequest(
    val revision: String,
)

data class CategoryMediaResponse(
    val category: String,
    val assetId: UUID?,
    val alt: Map<String, String>,
    val rightsStatus: String,
    val approvedAt: Instant?,
    val updatedAt: Instant,
    val revision: String,
    val path: String?,
    val width: Int?,
    val height: Int?,
)

data class PublicCategoryCover(
    val category: String,
    val path: String,
    val width: Int,
    val height: Int,
    val alt: String,
)
