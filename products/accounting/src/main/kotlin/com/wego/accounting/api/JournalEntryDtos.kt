package com.wego.accounting.api

import com.wego.accounting.application.PostedLineInput
import com.wego.accounting.domain.AccountId
import com.wego.accounting.domain.JournalEntry
import com.wego.accounting.domain.JournalLine
import com.wego.accounting.domain.JournalLineDirection
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val CURRENCY_CODE_PATTERN = "^[A-Z]{3}$"

// Same pattern as MoneyDto (products/hr) — a decimal string with exactly 2
// places, never a raw JSON number. Amounts are serialized/accepted as
// strings app-wide specifically to avoid a JSON-number parser silently
// normalizing away a significant trailing zero (e.g. "250.00" -> 250.0).
private const val AMOUNT_PATTERN = "^\\d{1,10}\\.\\d{2}$"
private const val MAX_DESCRIPTION_LENGTH = 1000
private const val MAX_REFERENCE_LENGTH = 200
private const val MAX_REASON_LENGTH = 1000

data class JournalLineInput(
    @field:NotNull
    val accountId: UUID,
    @field:NotNull
    val direction: JournalLineDirection,
    @field:NotBlank
    @field:Pattern(regexp = AMOUNT_PATTERN, message = "must be a decimal amount with exactly 2 places, e.g. 250.00")
    val amount: String,
)

data class PostJournalEntryRequest(
    @field:NotNull
    val entryDate: LocalDate,
    @field:NotBlank
    @field:Size(max = MAX_DESCRIPTION_LENGTH)
    val description: String,
    @field:Size(max = MAX_REFERENCE_LENGTH)
    val reference: String?,
    @field:Pattern(regexp = CURRENCY_CODE_PATTERN, message = "must be a 3-letter uppercase ISO 4217 code")
    val currencyCode: String,
    @field:Size(min = 2, message = "a journal entry needs at least 2 lines")
    val lines: List<@Valid JournalLineInput>,
)

data class ReverseJournalEntryRequest(
    @field:NotBlank
    @field:Size(max = MAX_REASON_LENGTH)
    val reason: String,
)

data class JournalLineResponse(
    val id: UUID,
    val accountId: UUID,
    val direction: JournalLineDirection,
    val amount: String,
)

fun JournalLine.toResponse(): JournalLineResponse = JournalLineResponse(id.value, accountId.value, direction, amount.toPlainString())

data class JournalEntryResponse(
    val id: UUID,
    val entryDate: LocalDate,
    val description: String,
    val reference: String?,
    val currencyCode: String,
    val lines: List<JournalLineResponse>,
    val debitTotal: String,
    val creditTotal: String,
    val reversalOfEntryId: UUID?,
    val postedByUserId: UUID?,
    val postedAt: Instant,
)

fun JournalEntry.toResponse(): JournalEntryResponse =
    JournalEntryResponse(
        id = id.value,
        entryDate = entryDate,
        description = description,
        reference = reference,
        currencyCode = currencyCode,
        lines = lines.map { it.toResponse() },
        debitTotal = debitTotal.toPlainString(),
        creditTotal = creditTotal.toPlainString(),
        reversalOfEntryId = reversalOfEntryId?.value,
        postedByUserId = postedByUserId,
        postedAt = postedAt,
    )

fun JournalLineInput.toPostedLineInput(): PostedLineInput =
    PostedLineInput(accountId = AccountId(accountId), direction = direction, amount = BigDecimal(amount))

data class JournalEntryErrorResponse(
    val error: String,
)
