package com.wego.toursoperator.api

import com.wego.toursoperator.application.TourQueryService
import com.wego.toursoperator.domain.Tour
import com.wego.toursoperator.domain.TourCategory
import com.wego.toursoperator.domain.TourId
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Validated
@RestController
@RequestMapping("/api/v1/tours-operator/tours")
class TourController(
    private val tourQueryService: TourQueryService,
) {
    /** Public endpoint — no authentication required for the catalog. */
    @GetMapping
    fun list(
        @RequestParam(required = false) category: TourCategory?,
        @RequestParam(required = false, defaultValue = "true") activeOnly: Boolean,
        @RequestParam(required = false, defaultValue = "0") @Min(0) page: Int,
        @RequestParam(required = false, defaultValue = "50") @Min(1) @Max(100) size: Int,
    ): List<TourSummaryResponse> = tourQueryService.list(category, activeOnly, page, size).map { it.toSummaryResponse() }

    @GetMapping("/{id}")
    fun findById(
        @PathVariable id: UUID,
    ): ResponseEntity<TourSummaryResponse> {
        val tour = tourQueryService.findById(TourId(id)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(tour.toSummaryResponse())
    }

    @GetMapping("/by-slug")
    fun findBySlug(
        @RequestParam slug: String,
    ): ResponseEntity<TourSummaryResponse> {
        val tour = tourQueryService.findBySlug(slug) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(tour.toSummaryResponse())
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
    )

private fun Long.toMoneyResponse(): MoneyResponse =
    MoneyResponse(amount = toBigDecimal().movePointLeft(2).setScale(2).toPlainString())
