package com.wego.toursoperator.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.wego.identity.AuthenticatedUser
import com.wego.toursoperator.application.ContentCommandResult
import com.wego.toursoperator.application.MediaInput
import com.wego.toursoperator.application.PublicTourContent
import com.wego.toursoperator.application.PublicTourContentQuery
import com.wego.toursoperator.application.StaffTourContent
import com.wego.toursoperator.application.TourContentService
import com.wego.toursoperator.domain.ContentLocale
import com.wego.toursoperator.domain.HotelPickup
import com.wego.toursoperator.domain.StopKind
import com.wego.toursoperator.domain.StopText
import com.wego.toursoperator.domain.TourContentDocument
import com.wego.toursoperator.domain.TourFactsDocument
import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.TourStop
import jakarta.validation.Valid
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/**
 * Tour content. The public endpoint serves PUBLISHED content and APPROVED
 * media only; staff endpoints edit drafts, and publishing requires the
 * separate `tours-operator.content:publish` permission.
 */
@Validated
@RestController("toursOperatorTourContentController")
@RequestMapping("/api/v1/tours-operator")
class TourContentController(
    private val publicTourContentQuery: PublicTourContentQuery,
    private val tourContentService: TourContentService,
) {
    // ── Public ───────────────────────────────────────────────────────────────

    @GetMapping("/tours/by-slug/content")
    fun publicContent(
        @RequestParam slug: String,
        @RequestParam(required = false, defaultValue = "en") locale: String,
    ): ResponseEntity<Any> {
        val requested =
            ContentLocale.fromCode(locale) ?: return ResponseEntity.badRequest().body(ContentErrorResponse("unsupported_locale", null))
        val content = publicTourContentQuery.forSlug(slug, requested) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(content.toResponse())
    }

    // ── Staff ────────────────────────────────────────────────────────────────

    @GetMapping("/staff/tours/{id}/content")
    @PreAuthorize("hasAuthority('tours-operator.tour:view')")
    fun staffContent(
        @PathVariable id: UUID,
    ): ResponseEntity<StaffTourContentResponse> =
        tourContentService.staffView(TourId(id))?.let { ResponseEntity.ok(it.toResponse()) }
            ?: ResponseEntity.notFound().build()

    @PutMapping("/staff/tours/{id}/content/{locale}")
    @PreAuthorize("hasAuthority('tours-operator.tour:manage')")
    fun saveContentDraft(
        @PathVariable id: UUID,
        @PathVariable locale: String,
        @Valid @RequestBody request: TourContentRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val contentLocale =
            ContentLocale.fromCode(locale) ?: return ResponseEntity.badRequest().body(ContentErrorResponse("unsupported_locale", null))
        val document = runCatching { request.toDocument() }.getOrElse { return invalid(it) }
        return tourContentService.saveContentDraft(TourId(id), contentLocale, document, actor(authentication)).toResponse()
    }

    @PostMapping("/staff/tours/{id}/content/{locale}/publish")
    @PreAuthorize("hasAuthority('tours-operator.content:publish')")
    fun publishContent(
        @PathVariable id: UUID,
        @PathVariable locale: String,
        @Valid @RequestBody request: PublishRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val contentLocale =
            ContentLocale.fromCode(locale) ?: return ResponseEntity.badRequest().body(ContentErrorResponse("unsupported_locale", null))
        return tourContentService.publishContent(TourId(id), contentLocale, request.revision, actor(authentication)).toResponse()
    }

    @PostMapping("/staff/tours/{id}/content/{locale}/unpublish")
    @PreAuthorize("hasAuthority('tours-operator.content:publish')")
    fun unpublishContent(
        @PathVariable id: UUID,
        @PathVariable locale: String,
    ): ResponseEntity<Any> {
        val contentLocale =
            ContentLocale.fromCode(locale) ?: return ResponseEntity.badRequest().body(ContentErrorResponse("unsupported_locale", null))
        return tourContentService.unpublishContent(TourId(id), contentLocale).toResponse()
    }

    @PutMapping("/staff/tours/{id}/facts")
    @PreAuthorize("hasAuthority('tours-operator.tour:manage')")
    fun saveFactsDraft(
        @PathVariable id: UUID,
        @Valid @RequestBody request: TourFactsRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val document = runCatching { request.toDocument() }.getOrElse { return invalid(it) }
        return tourContentService.saveFactsDraft(TourId(id), document, actor(authentication)).toResponse()
    }

    @PostMapping("/staff/tours/{id}/facts/publish")
    @PreAuthorize("hasAuthority('tours-operator.content:publish')")
    fun publishFacts(
        @PathVariable id: UUID,
        @Valid @RequestBody request: PublishRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> = tourContentService.publishFacts(TourId(id), request.revision, actor(authentication)).toResponse()

    @PostMapping("/staff/tours/{id}/facts/unpublish")
    @PreAuthorize("hasAuthority('tours-operator.content:publish')")
    fun unpublishFacts(
        @PathVariable id: UUID,
    ): ResponseEntity<Any> = tourContentService.unpublishFacts(TourId(id)).toResponse()

    @PutMapping("/staff/tours/{id}/media")
    @PreAuthorize("hasAuthority('tours-operator.tour:manage')")
    fun replaceMedia(
        @PathVariable id: UUID,
        @Valid @RequestBody @Size(max = 30) request: List<TourMediaRequest>,
        @RequestParam(required = false) revision: String?,
    ): ResponseEntity<Any> {
        val items = runCatching { request.map { it.toInput() } }.getOrElse { return invalid(it) }
        return runCatching { tourContentService.replaceMedia(TourId(id), items, revision) }
            .getOrElse { return invalid(it) }
            .toResponse()
    }

    @PostMapping("/staff/tours/{id}/media/{mediaId}/approve")
    @PreAuthorize("hasAuthority('tours-operator.content:publish')")
    fun approveMedia(
        @PathVariable id: UUID,
        @PathVariable mediaId: UUID,
        @Valid @RequestBody request: PublishRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> = tourContentService.approveMedia(TourId(id), mediaId, request.revision, actor(authentication)).toResponse()

    private fun actor(authentication: Authentication): UUID? = (authentication.principal as? AuthenticatedUser)?.userId

    /** Domain validation messages are safe to return: they describe the field, never stored data. */
    private fun invalid(error: Throwable): ResponseEntity<Any> =
        if (error is IllegalArgumentException) {
            ResponseEntity.badRequest().body(ContentErrorResponse("invalid_content", error.message))
        } else {
            throw error
        }
}

// ── Requests ──────────────────────────────────────────────────────────────────

/** The revision the publisher reviewed; publishing fails with draft_changed if the draft moved on. */
data class PublishRequest(
    @field:jakarta.validation.constraints.NotBlank
    val revision: String,
)

data class StopTextRequest(
    val stopKey: String,
    val name: String,
    val description: String? = null,
)

// The web layer's JSON mapper does not apply Kotlin default values, so every
// optional field is nullable and null means "empty".
data class TourContentRequest(
    val name: String,
    val shortDescription: String,
    val description: String,
    val includes: List<String>? = null,
    val excludes: List<String>? = null,
    val knowBeforeYouGo: List<String>? = null,
    val meetingPoint: String? = null,
    val stops: List<StopTextRequest>? = null,
) {
    fun toDocument() =
        TourContentDocument(
            name = name.trim(),
            shortDescription = shortDescription.trim(),
            description = description.trim(),
            includes = includes.orEmpty().map { it.trim() },
            excludes = excludes.orEmpty().map { it.trim() },
            knowBeforeYouGo = knowBeforeYouGo.orEmpty().map { it.trim() },
            meetingPoint = meetingPoint?.trim()?.ifEmpty { null },
            stops = stops.orEmpty().map { StopText(it.stopKey, it.name.trim(), it.description?.trim()?.ifEmpty { null }) },
        )
}

data class TourStopRequest(
    val key: String,
    val kind: StopKind,
    val latitude: Double,
    val longitude: Double,
)

data class TourFactsRequest(
    val childrenAllowed: Boolean? = null,
    val minimumAge: Int? = null,
    val guideLanguages: List<String>? = null,
    val hotelPickup: HotelPickup? = null,
    val stops: List<TourStopRequest>? = null,
) {
    fun toDocument() =
        TourFactsDocument(
            childrenAllowed = childrenAllowed,
            minimumAge = minimumAge,
            guideLanguages = guideLanguages.orEmpty(),
            hotelPickup = hotelPickup,
            stops = stops.orEmpty().map { TourStop(it.key, it.kind, it.latitude, it.longitude) },
        )
}

data class TourMediaRequest(
    val id: UUID? = null,
    val path: String,
    val width: Int,
    val height: Int,
    @param:JsonProperty("isCover")
    @get:JsonProperty("isCover")
    val isCover: Boolean? = null,
    val alt: Map<String, String>? = null,
) {
    fun toInput(): MediaInput {
        val pairs =
            alt
                .orEmpty()
                .map { (code, text) ->
                    (ContentLocale.fromCode(code) ?: throw IllegalArgumentException("unsupported alt locale: $code")) to text.trim()
                }
        require(pairs.map { it.first }.toSet().size == pairs.size) { "alt has the same locale twice" }
        val localized = pairs.toMap()
        return MediaInput(id, path, width, height, isCover == true, localized)
    }
}

// ── Responses ─────────────────────────────────────────────────────────────────

/** Validation failure with the rule that was broken (field-level, never stored data). */
data class ContentErrorResponse(
    val error: String,
    val detail: String?,
)

data class PublicStopResponse(
    val key: String,
    val kind: StopKind,
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val description: String?,
)

data class PublicMediaResponse(
    val path: String,
    val width: Int,
    val height: Int,
    @get:JsonProperty("isCover")
    val isCover: Boolean,
    val alt: String,
)

data class PublicFactsResponse(
    val childrenAllowed: Boolean?,
    val minimumAge: Int?,
    val guideLanguages: List<String>,
    val hotelPickup: HotelPickup?,
)

data class PublicTourContentResponse(
    val tourId: UUID,
    val slug: String,
    val requestedLocale: String,
    /** Locale of the text actually served; null when no text is published for the tour. */
    val servedLocale: String?,
    val name: String?,
    val shortDescription: String?,
    val description: String?,
    val includes: List<String>,
    val excludes: List<String>,
    val knowBeforeYouGo: List<String>,
    val meetingPoint: String?,
    val facts: PublicFactsResponse?,
    val stops: List<PublicStopResponse>,
    val media: List<PublicMediaResponse>,
)

data class StagedContentResponse(
    val stage: String,
    val document: TourContentRequest,
    val updatedAt: Instant,
    val updatedByUserId: UUID?,
    val revision: String,
)

data class StagedFactsResponse(
    val stage: String,
    val document: TourFactsRequest,
    val updatedAt: Instant,
    val updatedByUserId: UUID?,
    val revision: String,
)

data class StaffMediaResponse(
    val id: UUID,
    val position: Int,
    val path: String,
    val width: Int,
    val height: Int,
    @get:JsonProperty("isCover")
    val isCover: Boolean,
    val alt: Map<String, String>,
    val rightsStatus: String,
    val approvedAt: Instant?,
    val revision: String,
)

data class StaffTourContentResponse(
    /** locale → its DRAFT and/or PUBLISHED documents. */
    val content: Map<String, List<StagedContentResponse>>,
    val facts: List<StagedFactsResponse>,
    val media: List<StaffMediaResponse>,
    val mediaRevision: String,
)

private fun PublicTourContent.toResponse() =
    PublicTourContentResponse(
        tourId = tour.id.value,
        slug = tour.slug,
        requestedLocale = requestedLocale.code,
        servedLocale = servedLocale?.code,
        name = content?.name,
        shortDescription = content?.shortDescription,
        description = content?.description,
        includes = content?.includes.orEmpty(),
        excludes = content?.excludes.orEmpty(),
        knowBeforeYouGo = content?.knowBeforeYouGo.orEmpty(),
        meetingPoint = content?.meetingPoint,
        facts = facts?.let { PublicFactsResponse(it.childrenAllowed, it.minimumAge, it.guideLanguages, it.hotelPickup) },
        stops = stops.map { PublicStopResponse(it.key, it.kind, it.latitude, it.longitude, it.name, it.description) },
        media = media.map { PublicMediaResponse(it.path, it.width, it.height, it.isCover, it.alt) },
    )

private fun TourContentDocument.toRequestShape() =
    TourContentRequest(
        name,
        shortDescription,
        description,
        includes,
        excludes,
        knowBeforeYouGo,
        meetingPoint,
        stops.map { StopTextRequest(it.stopKey, it.name, it.description) },
    )

private fun TourFactsDocument.toRequestShape() =
    TourFactsRequest(
        childrenAllowed,
        minimumAge,
        guideLanguages,
        hotelPickup,
        stops.map { TourStopRequest(it.key, it.kind, it.latitude, it.longitude) },
    )

private fun StaffTourContent.toResponse() =
    StaffTourContentResponse(
        mediaRevision = mediaRevision,
        content =
            content
                .mapKeys { it.key.code }
                .mapValues { (_, docs) ->
                    docs.map {
                        StagedContentResponse(
                            it.stage.name,
                            it.document.toRequestShape(),
                            it.updatedAt,
                            it.updatedByUserId,
                            it.revision,
                        )
                    }
                },
        facts =
            facts.map {
                StagedFactsResponse(
                    it.stage.name,
                    it.document.toRequestShape(),
                    it.updatedAt,
                    it.updatedByUserId,
                    it.revision,
                )
            },
        media =
            media.map {
                StaffMediaResponse(
                    it.id,
                    it.position,
                    it.path,
                    it.width,
                    it.height,
                    it.isCover,
                    it.alt.mapKeys { entry -> entry.key.code },
                    it.rightsStatus.name,
                    it.approvedAt,
                    it.revision,
                )
            },
    )

private fun ContentCommandResult.toResponse(): ResponseEntity<Any> =
    when (this) {
        ContentCommandResult.Done -> ResponseEntity.noContent().build()
        ContentCommandResult.TourNotFound -> ResponseEntity.notFound().build()
        ContentCommandResult.MediaNotFound -> ResponseEntity.notFound().build()
        ContentCommandResult.NothingToPublish ->
            ResponseEntity
                .status(
                    HttpStatus.CONFLICT,
                ).body(ContentErrorResponse("nothing_to_publish", null))
        ContentCommandResult.DraftChanged -> ResponseEntity.status(HttpStatus.CONFLICT).body(ContentErrorResponse("draft_changed", null))
        is ContentCommandResult.Invalid -> ResponseEntity.badRequest().body(ContentErrorResponse(reason, null))
    }
