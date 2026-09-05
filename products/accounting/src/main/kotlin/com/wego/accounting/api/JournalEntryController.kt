package com.wego.accounting.api

import com.wego.accounting.application.JournalEntryQueryService
import com.wego.accounting.application.PostJournalEntryResult
import com.wego.accounting.application.PostJournalEntryService
import com.wego.accounting.application.ReverseJournalEntryResult
import com.wego.accounting.application.ReverseJournalEntryService
import com.wego.accounting.domain.AccountId
import com.wego.accounting.domain.JournalEntryId
import com.wego.events.CorrelationContext
import com.wego.identity.AuthenticatedUser
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@Validated
@RestController
@RequestMapping("/api/v1/accounting/journal-entries")
class JournalEntryController(
    private val postJournalEntryService: PostJournalEntryService,
    private val reverseJournalEntryService: ReverseJournalEntryService,
    private val journalEntryQueryService: JournalEntryQueryService,
) {
    @PostMapping
    @PreAuthorize("hasAuthority('accounting:journal-manage')")
    fun post(
        @Valid @RequestBody request: PostJournalEntryRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                postJournalEntryService.post(
                    entryDate = request.entryDate,
                    description = request.description,
                    reference = request.reference,
                    currencyCode = request.currencyCode,
                    lines = request.lines.map { it.toPostedLineInput() },
                    postedByUserId = actorUserId,
                    correlationId = CorrelationContext.currentCorrelationId(),
                )
        ) {
            is PostJournalEntryResult.Posted -> ResponseEntity.status(HttpStatus.CREATED).body(result.journalEntry.toResponse())
            PostJournalEntryResult.TooFewLines -> ResponseEntity.badRequest().body(JournalEntryErrorResponse("too_few_lines"))
            PostJournalEntryResult.MissingDebitOrCredit ->
                ResponseEntity.badRequest().body(JournalEntryErrorResponse("missing_debit_or_credit"))
            PostJournalEntryResult.Unbalanced -> ResponseEntity.badRequest().body(JournalEntryErrorResponse("unbalanced"))
            is PostJournalEntryResult.AccountNotFound -> ResponseEntity.badRequest().body(JournalEntryErrorResponse("account_not_found"))
            is PostJournalEntryResult.AccountInactive -> ResponseEntity.badRequest().body(JournalEntryErrorResponse("account_inactive"))
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('accounting:journal-view')")
    fun list(
        @RequestParam(required = false) from: LocalDate?,
        @RequestParam(required = false) to: LocalDate?,
        @RequestParam(required = false) accountId: UUID?,
        @RequestParam(required = false) reference: String?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) size: Int,
    ): List<JournalEntryResponse> =
        journalEntryQueryService.list(from, to, accountId?.let(::AccountId), reference, page, size).map { it.toResponse() }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('accounting:journal-view')")
    fun get(
        @PathVariable id: UUID,
    ): ResponseEntity<JournalEntryResponse> {
        val entry = journalEntryQueryService.findById(JournalEntryId(id)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(entry.toResponse())
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('accounting:journal-manage')")
    fun reverse(
        @PathVariable id: UUID,
        @Valid @RequestBody request: ReverseJournalEntryRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                reverseJournalEntryService.reverse(
                    id = JournalEntryId(id),
                    reason = request.reason,
                    actorUserId = actorUserId,
                    correlationId = CorrelationContext.currentCorrelationId(),
                )
        ) {
            is ReverseJournalEntryResult.Reversed -> ResponseEntity.status(HttpStatus.CREATED).body(result.reversalEntry.toResponse())
            ReverseJournalEntryResult.NotFound -> ResponseEntity.notFound().build()
            ReverseJournalEntryResult.AlreadyReversed ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(JournalEntryErrorResponse("already_reversed"))
        }
    }
}
