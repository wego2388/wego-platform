package com.wego.toursoperator.api

import com.wego.identity.AuthenticatedUser
import com.wego.toursoperator.application.CreateTourCommand
import com.wego.toursoperator.application.CreateTourResult
import com.wego.toursoperator.application.CreateTourService
import com.wego.toursoperator.application.PublicTourContentQuery
import com.wego.toursoperator.application.SetTourActiveResult
import com.wego.toursoperator.application.SetTourActiveService
import com.wego.toursoperator.application.TourQueryService
import com.wego.toursoperator.application.UpdateTourCommand
import com.wego.toursoperator.application.UpdateTourResult
import com.wego.toursoperator.application.UpdateTourService
import com.wego.toursoperator.domain.CancellationPolicy
import com.wego.toursoperator.domain.ContentLocale
import com.wego.toursoperator.domain.Tour
import com.wego.toursoperator.domain.TourCategory
import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.TourType
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import java.util.UUID

@Validated
@RestController
@RequestMapping("/api/v1/tours-operator")
class TourController(
    private val tourQueryService: TourQueryService,
    private val createTourService: CreateTourService,
    private val updateTourService: UpdateTourService,
    private val setTourActiveService: SetTourActiveService,
    private val publicTourContentQuery: PublicTourContentQuery,
) {
    // ── Public endpoints ─────────────────────────────────────────────────────

    @GetMapping("/tours")
    fun list(
        @RequestParam(required = false) category: TourCategory?,
        @RequestParam(required = false, defaultValue = "0") @Min(0) page: Int,
        @RequestParam(required = false, defaultValue = "50") @Min(1) @Max(100) size: Int,
        @RequestParam(required = false) locale: String?,
    ): List<TourSummaryResponse> = localize(tourQueryService.list(category, activeOnly = true, page, size), locale)

    @GetMapping("/tours/{id}")
    fun findById(
        @PathVariable id: UUID,
    ): ResponseEntity<TourSummaryResponse> {
        val tour = tourQueryService.findById(TourId(id)) ?: return ResponseEntity.notFound().build()
        if (!tour.isActive) return ResponseEntity.notFound().build()
        return ResponseEntity.ok(localize(listOf(tour), null).single())
    }

    @GetMapping("/tours/by-slug")
    fun findBySlug(
        @RequestParam slug: String,
        @RequestParam(required = false) locale: String?,
    ): ResponseEntity<TourSummaryResponse> {
        val tour = tourQueryService.findBySlug(slug) ?: return ResponseEntity.notFound().build()
        if (!tour.isActive) return ResponseEntity.notFound().build()
        return ResponseEntity.ok(localize(listOf(tour), locale).single())
    }

    /** Batched covers and text; absent/unknown locale preserves the unlocalized text contract. */
    private fun localize(
        tours: List<Tour>,
        locale: String?,
    ): List<TourSummaryResponse> {
        val contentLocale = locale?.let(ContentLocale::fromCode)
        val summaries = contentLocale?.let { publicTourContentQuery.publishedSummaries(tours.map { it.id }, it) }.orEmpty()
        val covers = publicTourContentQuery.approvedCovers(tours.map { it.id }, contentLocale ?: ContentLocale.EN)
        return tours.map { tour ->
            tour.toSummaryResponse().copy(
                cover = covers[tour.id]?.let { PublicMediaResponse(it.path, it.width, it.height, it.isCover, it.alt) },
                localized =
                    summaries[tour.id]?.let {
                        LocalizedTourSummaryResponse(it.locale.code, it.name, it.shortDescription)
                    },
            )
        }
    }

    // ── Staff endpoints — /staff/tours/** ────────────────────────────────────
    //
    // The dedicated staff tree cannot collide with the public `/tours/*`
    // matcher. Method security is still mandatory: authentication alone must
    // never grant catalog mutation rights.

    @GetMapping("/staff/tours")
    @PreAuthorize("hasAuthority('tours-operator.tour:view')")
    fun listForStaff(
        @RequestParam(required = false) category: TourCategory?,
        @RequestParam(required = false, defaultValue = "false") activeOnly: Boolean,
        @RequestParam(required = false, defaultValue = "0") @Min(0) page: Int,
        @RequestParam(required = false, defaultValue = "50") @Min(1) @Max(100) size: Int,
    ): List<TourSummaryResponse> = tourQueryService.list(category, activeOnly, page, size).map { it.toSummaryResponse() }

    @GetMapping("/staff/tours/{id}")
    @PreAuthorize("hasAuthority('tours-operator.tour:view')")
    fun findByIdForStaff(
        @PathVariable id: UUID,
    ): ResponseEntity<TourSummaryResponse> {
        val tour = tourQueryService.findById(TourId(id)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(tour.toSummaryResponse())
    }

    @PostMapping("/staff/tours")
    @PreAuthorize("hasAuthority('tours-operator.tour:manage')")
    fun create(
        @Valid @RequestBody request: CreateTourRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val createdByUserId = (authentication.principal as AuthenticatedUser).userId
        val command =
            CreateTourCommand(
                slug = request.slug,
                category = request.category,
                durationText = request.durationText,
                priceAdultCents = request.priceAdultCents,
                priceChildCents = request.priceChildCents,
                capacity = request.capacity,
                availableTimeSlots = request.availableTimeSlots,
                sortOrder = request.sortOrder,
                nameEn = request.nameEn,
                tourType = request.tourType ?: TourType.TOUR,
                imageUrl = request.imageUrl,
                cancellationPolicy = request.cancellationPolicy ?: CancellationPolicy.STANDARD,
                createdByUserId = createdByUserId,
                pricingNote = request.pricingNote,
            )
        return when (val result = createTourService.create(command)) {
            is CreateTourResult.Success -> {
                val uri =
                    ServletUriComponentsBuilder
                        .fromCurrentContextPath()
                        .path("/api/v1/tours-operator/staff/tours/{id}")
                        .buildAndExpand(result.tour.id.value)
                        .toUri()
                ResponseEntity.created(uri).body(result.tour.toSummaryResponse())
            }
            CreateTourResult.SlugAlreadyExists ->
                ResponseEntity.status(409).body(ErrorResponse("slug_already_exists"))
        }
    }

    @PutMapping("/staff/tours/{id}")
    @PreAuthorize("hasAuthority('tours-operator.tour:manage')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateTourRequest,
    ): ResponseEntity<Any> {
        val command =
            UpdateTourCommand(
                id = TourId(id),
                category = request.category,
                durationText = request.durationText,
                priceAdultCents = request.priceAdultCents,
                priceChildCents = request.priceChildCents,
                capacity = request.capacity,
                availableTimeSlots = request.availableTimeSlots,
                sortOrder = request.sortOrder,
                nameEn = request.nameEn,
                tourType = request.tourType ?: TourType.TOUR,
                imageUrl = request.imageUrl,
                cancellationPolicy = request.cancellationPolicy ?: CancellationPolicy.STANDARD,
                pricingNote = request.pricingNote,
            )
        return when (updateTourService.update(command)) {
            UpdateTourResult.Success -> ResponseEntity.noContent().build()
            UpdateTourResult.NotFound -> ResponseEntity.notFound().build()
        }
    }

    @PatchMapping("/staff/tours/{id}/activate")
    @PreAuthorize("hasAuthority('tours-operator.tour:manage')")
    fun activate(
        @PathVariable id: UUID,
    ): ResponseEntity<Any> =
        when (setTourActiveService.activate(TourId(id))) {
            SetTourActiveResult.Success -> ResponseEntity.noContent().build()
            SetTourActiveResult.NotFound -> ResponseEntity.notFound().build()
        }

    @PatchMapping("/staff/tours/{id}/deactivate")
    @PreAuthorize("hasAuthority('tours-operator.tour:manage')")
    fun deactivate(
        @PathVariable id: UUID,
    ): ResponseEntity<Any> =
        when (setTourActiveService.deactivate(TourId(id))) {
            SetTourActiveResult.Success -> ResponseEntity.noContent().build()
            SetTourActiveResult.NotFound -> ResponseEntity.notFound().build()
        }
}

internal fun Tour.toSummaryResponse() =
    TourSummaryResponse(
        id = id.value,
        slug = slug,
        category = category,
        durationText = durationText,
        priceAdult = priceAdultCents.toMoneyResponse(),
        priceChild = priceChildCents?.toMoneyResponse(),
        capacity = capacity,
        availableTimeSlots = availableTimeSlots.toList(),
        sortOrder = sortOrder,
        isActive = isActive,
        nameEn = nameEn,
        tourType = tourType.name,
        imageUrl = imageUrl,
        cancellationPolicy = cancellationPolicy.name,
        pricingNote = pricingNote,
        priceBasis = priceBasis.name,
        priceOptions =
            priceOptions.map {
                TourPriceOptionResponse(
                    code = it.code,
                    label = it.labelEn,
                    seatsPerUnit = it.seatsPerUnit,
                    price = it.priceCents.toMoneyResponse(),
                )
            },
    )

internal fun Long.toMoneyResponse(): MoneyResponse = MoneyResponse(amount = toBigDecimal().movePointLeft(2).setScale(2).toPlainString())
