package com.wego.toursoperator.api

import com.fasterxml.jackson.annotation.JsonProperty
import com.wego.toursoperator.domain.BookingStatus
import com.wego.toursoperator.domain.CancellationPolicy
import com.wego.toursoperator.domain.TimeSlot
import com.wego.toursoperator.domain.TourCategory
import com.wego.toursoperator.domain.TourType
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ── Shared ────────────────────────────────────────────────────────────────────

data class MoneyResponse(
    val amount: String,
    val currencyCode: String = "EUR",
)

data class ErrorResponse(
    val error: String,
)

// ── Tour responses ────────────────────────────────────────────────────────────

data class TourSummaryResponse(
    val cover: PublicMediaResponse? = null,
    val id: UUID,
    val slug: String,
    val category: TourCategory,
    val durationText: String,
    val priceAdult: MoneyResponse,
    val priceChild: MoneyResponse?,
    val capacity: Int,
    val availableTimeSlots: List<TimeSlot>,
    val sortOrder: Int,
    @get:JsonProperty("isActive")
    val isActive: Boolean,
    val nameEn: String?,
    val tourType: String,
    val imageUrl: String?,
    val cancellationPolicy: String,
    val pricingNote: String? = null,
    /** Published localized name/summary, present only when `locale` was requested and content is published. */
    val localized: LocalizedTourSummaryResponse? = null,
    /** PER_PERSON or PER_UNIT. Per-unit tours are booked by choosing one of [priceOptions] and a unit count. */
    val priceBasis: String = "PER_PERSON",
    val priceOptions: List<TourPriceOptionResponse> = emptyList(),
)

data class TourPriceOptionResponse(
    val code: String,
    /** Catalogue label in English; sites translate known codes. */
    val label: String,
    val seatsPerUnit: Int,
    /** Price per unit. */
    val price: MoneyResponse,
)

/** What a per-unit booking bought (snapshot at booking time). */
data class BookingUnitResponse(
    val optionCode: String,
    val optionLabel: String,
    val seatsPerUnit: Int,
    val unitCount: Int,
    val unitPrice: MoneyResponse,
)

data class LocalizedTourSummaryResponse(
    /** The locale the text is in: the requested one, or English as fallback. */
    val locale: String,
    val name: String,
    val shortDescription: String,
)

// ── Slot responses ────────────────────────────────────────────────────────────

data class TourSlotResponse(
    val id: UUID,
    val tourId: UUID,
    val date: LocalDate,
    val timeSlot: TimeSlot,
    val capacity: Int,
    val bookedCount: Int,
    val available: Int,
    @get:JsonProperty("isBlocked")
    val isBlocked: Boolean,
)

// ── Tour staff requests ───────────────────────────────────────────────────────

data class CreateTourRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^[a-z0-9][a-z0-9-]{1,78}[a-z0-9]$")
    val slug: String,
    @field:NotNull
    val category: TourCategory,
    @field:NotBlank
    @field:Size(max = 80)
    val durationText: String,
    @field:Min(0)
    val priceAdultCents: Long,
    val priceChildCents: Long? = null,
    @field:Min(1)
    @field:Max(1000)
    val capacity: Int,
    @field:NotEmpty
    val availableTimeSlots: Set<TimeSlot>,
    @field:Min(0)
    val sortOrder: Int,
    @field:Size(max = 200)
    val nameEn: String? = null,
    val tourType: TourType? = null,
    @field:Size(max = 2048)
    val imageUrl: String? = null,
    val cancellationPolicy: CancellationPolicy? = null,
    @field:Size(max = 500)
    val pricingNote: String? = null,
)

data class UpdateTourRequest(
    @field:NotNull
    val category: TourCategory,
    @field:NotBlank
    @field:Size(max = 80)
    val durationText: String,
    @field:Min(0)
    val priceAdultCents: Long,
    val priceChildCents: Long? = null,
    @field:Min(1)
    @field:Max(1000)
    val capacity: Int,
    @field:NotEmpty
    val availableTimeSlots: Set<TimeSlot>,
    @field:Min(0)
    val sortOrder: Int,
    @field:Size(max = 200)
    val nameEn: String? = null,
    val tourType: TourType? = null,
    @field:Size(max = 2048)
    val imageUrl: String? = null,
    val cancellationPolicy: CancellationPolicy? = null,
    @field:Size(max = 500)
    val pricingNote: String? = null,
)

// ── Slot staff requests ───────────────────────────────────────────────────────

data class CreateSlotRequest(
    @field:NotNull
    val date: LocalDate,
    @field:NotNull
    val timeSlot: TimeSlot,
    @field:Min(1)
    @field:Max(1000)
    val capacity: Int,
)

// ── Booking requests ──────────────────────────────────────────────────────────

data class BookingCustomerRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val fullName: String,
    @field:NotBlank
    @field:Size(max = 32)
    val phone: String,
    @field:Pattern(regexp = "^[A-Z]{2}$")
    val nationality: String,
    @field:Email
    @field:Size(max = 320)
    val email: String?,
)

data class CreateBookingRequest(
    val slotId: UUID,
    @field:Min(1)
    @field:Max(50)
    val adultsCount: Int,
    @field:Min(0)
    @field:Max(50)
    val childrenCount: Int,
    /** Per-unit tours only: the chosen price option. */
    @field:Size(max = 40)
    @field:Pattern(regexp = "^[a-z0-9][a-z0-9-]{0,39}$")
    val priceOptionCode: String? = null,
    /** Per-unit tours only: how many units of the option. */
    @field:Min(1)
    @field:Max(50)
    val unitCount: Int? = null,
    @field:Valid
    val customer: BookingCustomerRequest,
    @field:NotBlank
    @field:Size(max = 200)
    val hotelName: String,
    @field:Size(max = 32)
    val hotelRoom: String?,
    @field:Size(max = 4000)
    val specialRequests: String?,
    @field:Pattern(regexp = "^(en|ar|ru|it)$")
    val locale: String,
)

/** A booking entered by staff for a customer; locale is the language to use with the customer. */
data class CreateOfficeBookingRequest(
    /** Idempotency key generated by the client; re-sending it returns the booking already created. */
    val clientRequestId: UUID,
    val slotId: UUID,
    @field:Min(1)
    @field:Max(50)
    val adultsCount: Int,
    @field:Min(0)
    @field:Max(50)
    val childrenCount: Int,
    @field:Size(max = 40)
    @field:Pattern(regexp = "^[a-z0-9][a-z0-9-]{0,39}$")
    val priceOptionCode: String? = null,
    @field:Min(1)
    @field:Max(50)
    val unitCount: Int? = null,
    @field:Valid
    val customer: BookingCustomerRequest,
    @field:NotBlank
    @field:Size(max = 200)
    val hotelName: String,
    @field:Size(max = 32)
    val hotelRoom: String?,
    @field:Size(max = 4000)
    val specialRequests: String?,
    @field:Pattern(regexp = "^(en|ar|ru|it)$")
    val locale: String,
)

data class RecordCollectionRequest(
    val clientRequestId: UUID,
    val method: com.wego.toursoperator.domain.CollectionMethod,
    /** What the customer handed over, in [currency]: at most two decimals, greater than zero. */
    @field:jakarta.validation.constraints.DecimalMin("0.01")
    @field:jakarta.validation.constraints.Digits(integer = 10, fraction = 2)
    val amount: java.math.BigDecimal,
    /** EUR (default) or EGP. EGP settles at today's manager-set rate; staff cannot supply a rate. */
    val currency: com.wego.toursoperator.domain.PaidCurrency = com.wego.toursoperator.domain.PaidCurrency.EUR,
    /** Receipt number from the terminal/wallet/InstaPay/Fawry: required for non-cash, forbidden for cash. */
    @field:Size(max = 64)
    val reference: String? = null,
    /** The rate id the staff member was quoted; required for EGP. A changed rate returns 409 fx_rate_changed. */
    val fxRateId: UUID? = null,
    /** Re-records this reversed collection (same method and reference); the only way a receipt is reused. */
    val correctsCollectionId: UUID? = null,
)

data class SetFxRateRequest(
    @field:jakarta.validation.constraints.DecimalMin("1")
    @field:jakarta.validation.constraints.DecimalMax("1000")
    @field:jakarta.validation.constraints.Digits(integer = 4, fraction = 4)
    val egpPerEur: java.math.BigDecimal,
)

data class FxRateResponse(
    val id: UUID,
    val rateDate: LocalDate,
    /** EGP per 1 EUR. */
    val egpPerEur: String,
    val setByUserId: UUID?,
    val setAt: Instant,
)

data class FxRateTodayResponse(
    val date: LocalDate,
    val rate: FxRateResponse?,
)

data class CollectionQuoteResponse(
    /** OK, RATE_MISSING, BELOW_MINIMUM or EXCEEDS_OUTSTANDING. */
    val status: String,
    val settledEur: MoneyResponse?,
    val fxRate: String?,
    /** Send this back when recording so a rate change between quote and save is detected. */
    val fxRateId: UUID?,
    val outstanding: MoneyResponse,
)

data class ReverseCollectionRequest(
    val clientRequestId: UUID,
    @field:NotBlank
    @field:Size(max = 1000)
    val reason: String,
)

data class LookupBookingRequest(
    @field:NotBlank
    @field:Size(max = 64)
    @field:Pattern(regexp = "^[Ss][Tt][Rr]-[0-9]{4}-[0-9]+$")
    val reference: String,
    @field:NotBlank
    @field:Size(max = 32)
    val phone: String,
)

data class CancelBookingRequest(
    @field:NotBlank
    @field:Size(max = 1000)
    val reason: String,
)

// ── Booking responses ─────────────────────────────────────────────────────────

data class BookingCustomerResponse(
    val fullName: String,
    val phone: String,
    val nationality: String,
    val email: String?,
)

data class BookingResponse(
    val id: UUID,
    val reference: String,
    val tourId: UUID,
    val slotId: UUID,
    val tourDate: LocalDate,
    val timeSlot: TimeSlot,
    val adultsCount: Int,
    val childrenCount: Int,
    val priceAdult: MoneyResponse,
    val priceChild: MoneyResponse?,
    val totalPrice: MoneyResponse,
    /** Present for per-unit bookings (then priceAdult is 0 and priceChild null). */
    val unit: BookingUnitResponse? = null,
    val customer: BookingCustomerResponse,
    val hotelName: String,
    val hotelRoom: String?,
    val specialRequests: String?,
    val locale: String,
    val status: BookingStatus,
    val createdAt: Instant,
    val confirmedAt: Instant?,
    val cancelledAt: Instant?,
    val cancellationReason: String?,
    val completedAt: Instant?,
    val expiredAt: Instant?,
    /** ONLINE (public checkout) or OFFICE (created by staff). */
    val channel: com.wego.toursoperator.domain.BookingChannel = com.wego.toursoperator.domain.BookingChannel.ONLINE,
    /** True for a live office booking that is not paid in full: awaiting collection. Never auto-expired. */
    val awaitingCollection: Boolean = false,
    /** True for a COMPLETED office booking that still has a balance: "completed with unpaid balance". */
    val completedWithUnpaidBalance: Boolean = false,
    /** Cash state of an OFFICE booking, derived from its office ledger; null for ONLINE bookings. */
    val officePayment: OfficePaymentResponse? = null,
)

data class OfficePaymentResponse(
    val state: com.wego.toursoperator.domain.OfficePaymentState,
    val collected: MoneyResponse,
    val outstanding: MoneyResponse,
    /** Set when a cancelled booking still holds collected money not yet returned (collected minus refunds recorded). */
    val cashToReturn: MoneyResponse?,
    /** Money already returned to the customer (office refunds, net of reversals; EUR). */
    val refunded: MoneyResponse = MoneyResponse("0.00"),
)

data class OfficeCollectionResponse(
    val id: UUID,
    val kind: com.wego.toursoperator.domain.OfficeCollectionKind,
    val method: com.wego.toursoperator.domain.CollectionMethod,
    /** EUR settled against the booking balance. */
    val amount: MoneyResponse,
    /** What the customer handed over (EUR or EGP). */
    val amountPaid: MoneyResponse,
    /** EGP per EUR used for an EGP payment; null for EUR. */
    val fxRate: String?,
    /** Receipt number of a non-cash payment; null for cash and reversals. */
    val reference: String?,
    val reversesCollectionId: UUID?,
    val reason: String?,
    val recordedByUserId: UUID?,
    /** Who recorded or reversed the entry. */
    val recordedByEmail: String?,
    val recordedAt: Instant,
    val correctsCollectionId: UUID?,
)

data class OfficeCollectionOutcomeResponse(
    val entry: OfficeCollectionResponse,
    val officePayment: OfficePaymentResponse,
)

/** Minimized public projection returned only after reference + phone recovery. */
data class PublicBookingLookupResponse(
    val reference: String,
    val tourDate: LocalDate,
    val timeSlot: TimeSlot,
    val adultsCount: Int,
    val childrenCount: Int,
    val totalPrice: MoneyResponse,
    val unit: BookingUnitResponse? = null,
    val hotelName: String,
    val status: BookingStatus,
    val cancellationReason: String?,
)
