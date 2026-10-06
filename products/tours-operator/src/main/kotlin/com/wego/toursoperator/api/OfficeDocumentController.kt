package com.wego.toursoperator.api

import com.wego.identity.AuthenticatedUser
import com.wego.toursoperator.application.DocumentLanguage
import com.wego.toursoperator.application.DocumentResult
import com.wego.toursoperator.application.DocumentStamp
import com.wego.toursoperator.application.OfficeDocumentService
import com.wego.toursoperator.domain.BookingId
import com.wego.toursoperator.domain.TourSlotId
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class PrintDocumentRequest(
    @field:NotNull @field:Pattern(regexp = "^(en|ar)$") val language: String?,
)

data class PrintRunSheetRequest(
    @field:NotNull val date: LocalDate?,
    @field:NotNull @field:Pattern(regexp = "^(en|ar)$") val language: String?,
)

data class DocumentStampResponse(
    val type: String,
    val number: String,
    val version: Int,
    val language: String,
    val copy: Boolean,
    val revised: Boolean,
    val originalPrintedAt: Instant,
    val printedAt: Instant,
    val printedByEmail: String?,
)

data class DocumentResponse<T>(
    val document: DocumentStampResponse,
    val data: T,
)

/**
 * WEGO-016-OPS2-D printable office documents. Every call records one print in
 * the append-only register (original, then numbered reprints) and returns the
 * data the ERP renders for the browser to print. No PDF is generated or stored,
 * and responses are never cached because they carry customer data.
 *
 * Customer documents (voucher, receipt, cancellation form) need
 * `tours-operator.document:print`; operations documents (run sheet, pickup
 * manifest, which carries phone numbers) need `tours-operator.document:print-ops`.
 */
@Validated
@RestController("toursOperatorOfficeDocumentController")
@RequestMapping("/api/v1/tours-operator/documents")
class OfficeDocumentController(
    private val documents: OfficeDocumentService,
) {
    @PostMapping("/bookings/{id}/voucher")
    @PreAuthorize("hasAuthority('tours-operator.document:print')")
    fun voucher(
        @PathVariable id: UUID,
        @Valid @RequestBody request: PrintDocumentRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> = respond(documents.voucher(BookingId(id), language(request.language), actor(authentication)))

    @PostMapping("/collections/{id}/receipt")
    @PreAuthorize("hasAuthority('tours-operator.document:print')")
    fun receipt(
        @PathVariable id: UUID,
        @Valid @RequestBody request: PrintDocumentRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> = respond(documents.receipt(id, language(request.language), actor(authentication)))

    @PostMapping("/bookings/{id}/cancellation-form")
    @PreAuthorize("hasAuthority('tours-operator.document:print')")
    fun cancellationForm(
        @PathVariable id: UUID,
        @Valid @RequestBody request: PrintDocumentRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> = respond(documents.cancellationForm(BookingId(id), language(request.language), actor(authentication)))

    @PostMapping("/run-sheet")
    @PreAuthorize("hasAuthority('tours-operator.document:print-ops')")
    fun runSheet(
        @Valid @RequestBody request: PrintRunSheetRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> = respond(documents.runSheet(checkNotNull(request.date), language(request.language), actor(authentication)))

    @PostMapping("/slots/{slotId}/pickup-manifest")
    @PreAuthorize("hasAuthority('tours-operator.document:print-ops')")
    fun pickupManifest(
        @PathVariable slotId: UUID,
        @Valid @RequestBody request: PrintDocumentRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> = respond(documents.pickupManifest(TourSlotId(slotId), language(request.language), actor(authentication)))

    private fun actor(authentication: Authentication): UUID = (authentication.principal as AuthenticatedUser).userId

    private fun language(code: String?): DocumentLanguage = checkNotNull(code?.let(DocumentLanguage::fromCode))

    private fun <T> respond(result: DocumentResult<T>): ResponseEntity<Any> =
        when (result) {
            is DocumentResult.Ready ->
                ResponseEntity
                    .ok()
                    .cacheControl(CacheControl.noStore())
                    .body(DocumentResponse(result.stamp.toResponse(), result.data))
            DocumentResult.NotFound -> ResponseEntity.notFound().build()
            is DocumentResult.Refused -> ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(result.code))
        }
}

private fun DocumentStamp.toResponse() =
    DocumentStampResponse(
        type = type.name,
        number = number,
        version = version,
        language = language.code,
        copy = copy,
        revised = revised,
        originalPrintedAt = originalPrintedAt,
        printedAt = printedAt,
        printedByEmail = printedByEmail,
    )
