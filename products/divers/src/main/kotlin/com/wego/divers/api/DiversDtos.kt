package com.wego.divers.api

import com.wego.divers.domain.BookingStatus
import com.wego.divers.domain.OfferingStatus
import com.wego.divers.domain.OfferingType
import com.wego.divers.domain.PaymentStatus
import com.wego.divers.domain.PricingBasis
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val CURRENCY_CODE_PATTERN = "^[A-Z]{3}$"
private const val MAX_NAME_LENGTH = 200
private const val MAX_EMAIL_LENGTH = 320
private const val MAX_PHONE_LENGTH = 32
private const val MAX_REASON_LENGTH = 1000
private const val MAX_TITLE_LENGTH = 200
private const val MAX_DESCRIPTION_LENGTH = 4000

data class MoneyDto(
    @field:NotBlank
    @field:Pattern(regexp = "^\\d{1,8}\\.\\d{2}$", message = "must be a decimal amount with exactly 2 places, e.g. 45.00")
    val amount: String,
    @field:Pattern(regexp = CURRENCY_CODE_PATTERN, message = "must be a 3-letter uppercase ISO 4217 code")
    val currencyCode: String,
)

data class CreateOfferingRequest(
    val offeringType: OfferingType,
    @field:NotBlank
    @field:Size(max = MAX_TITLE_LENGTH)
    val title: String,
    @field:Size(max = MAX_DESCRIPTION_LENGTH)
    val description: String?,
    val startsOn: LocalDate,
    val endsOn: LocalDate?,
    @field:Positive
    val capacity: Int?,
    val pricingBasis: PricingBasis,
    @field:Valid
    val unitPrice: MoneyDto,
)

data class OfferingResponse(
    val id: UUID,
    val offeringType: OfferingType,
    val title: String,
    val description: String?,
    val startsOn: LocalDate,
    val endsOn: LocalDate?,
    val capacity: Int?,
    val pricingBasis: PricingBasis,
    val unitPrice: MoneyDto,
    val status: OfferingStatus,
    val createdAt: Instant,
    val closedAt: Instant?,
)

data class CloseOfferingRequest(
    @field:Size(max = MAX_REASON_LENGTH)
    val reason: String?,
)

data class CreateBookingRequest(
    val offeringId: UUID,
    @field:Positive
    val partySize: Int,
    @field:NotBlank
    @field:Size(max = MAX_NAME_LENGTH)
    val customerName: String,
    @field:Email
    @field:Size(max = MAX_EMAIL_LENGTH)
    val customerEmail: String?,
    @field:Size(max = MAX_PHONE_LENGTH)
    val customerPhone: String?,
)

data class CancelBookingRequest(
    @field:NotBlank
    @field:Size(max = MAX_REASON_LENGTH)
    val reason: String,
)

data class RefundBookingRequest(
    @field:NotBlank
    @field:Size(max = MAX_REASON_LENGTH)
    val reason: String,
)

data class BookingResponse(
    val id: UUID,
    val offeringId: UUID,
    val partySize: Int,
    val customerName: String,
    val customerEmail: String?,
    val customerPhone: String?,
    val status: BookingStatus,
    val paymentStatus: PaymentStatus,
    val pricingBasis: PricingBasis,
    val unitPrice: MoneyDto,
    val billableQuantity: Int,
    val totalPrice: MoneyDto,
    val createdAt: Instant,
    val cancelledAt: Instant?,
    val cancellationReason: String?,
)

data class ValidationErrorResponse(
    val error: String = "validation_failed",
    val message: String,
)

data class BookingErrorResponse(
    val error: String,
)
