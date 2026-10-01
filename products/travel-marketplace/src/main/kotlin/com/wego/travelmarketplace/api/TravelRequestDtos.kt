package com.wego.travelmarketplace.api

import com.fasterxml.jackson.annotation.JsonFormat
import com.wego.travelmarketplace.domain.PriceBasis
import com.wego.travelmarketplace.domain.TravelRequestActorType
import com.wego.travelmarketplace.domain.TravelRequestCancelReason
import com.wego.travelmarketplace.domain.TravelRequestSourceChannel
import com.wego.travelmarketplace.domain.TravelRequestStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class TravelRequestCustomerDto(
    @field:NotBlank @field:Size(max = 200) val name: String,
    // An E.164-shaped plausibility check, not full carrier-verified
    // reachability — see TravelRequestCustomer.isPlausiblePhoneNumber's own
    // doc comment, which this pattern mirrors so a bad value fails here with
    // a clear message rather than surfacing from deep domain construction.
    @field:Size(max = 32)
    @field:Pattern(regexp = "^\\+?[1-9][0-9 ()\\-.]{6,17}$", message = "must be a plausible phone number")
    val phone: String?,
    @field:Email @field:Size(max = 320) val email: String?,
)

data class CreateTravelRequestApiRequest(
    val serviceId: UUID,
    val serviceOptionId: UUID,
    @field:Future val requestedDate: LocalDate,
    val requestedTime: LocalTime?,
    // Upper bounds exist to keep adults + children from ever approaching
    // Int overflow (which would wrap negative and silently bypass the
    // party-capacity check) — not a realistic travel party size on its own.
    @field:Min(1) @field:Max(100) val adults: Int,
    @field:Min(0) @field:Max(100) val children: Int = 0,
    @field:Size(max = 500) val hotelOrPickup: String?,
    @field:Pattern(regexp = "^(en|ar)$") val locale: String,
    @field:Size(max = 2000) val notes: String?,
    val sourceChannel: TravelRequestSourceChannel,
    @field:Valid val customer: TravelRequestCustomerDto,
    /**
     * The price the caller's own review screen displayed for this option —
     * compared against the option's real current price so a catalog edit
     * between review and submit is rejected (`price_changed`) instead of
     * silently confirming at a price the customer never saw. See
     * [CreateTravelRequestResult.PriceChanged].
     */
    @field:JsonFormat(shape = JsonFormat.Shape.STRING)
    val expectedPriceAmount: BigDecimal,
    @field:Pattern(regexp = "^[A-Z]{3}$") val expectedPriceCurrency: String,
)

data class CancelTravelRequestApiRequest(
    val reason: TravelRequestCancelReason,
    @field:Size(max = 1000) val detail: String?,
)

data class TravelRequestErrorResponse(
    val error: String,
    /** Populated only for `error == "price_changed"` — the option's real current price, so the caller can show it without a second fetch. */
    @field:JsonFormat(shape = JsonFormat.Shape.STRING)
    val currentPriceAmount: BigDecimal? = null,
    val currentPriceCurrency: String? = null,
)

/**
 * The narrow public shape a reference-holder (customer, or anyone the
 * reference leaked to) can see without authentication — no customer name,
 * phone or email. `delivery/01_REQUEST_AND_BOOKING.md`'s own Summary spec:
 * "reference, service, date, party, pickup and confirmed/awaiting state" —
 * nothing more. Compare [TravelRequestStaffResponse], which is behind
 * `travel-request:view` and includes the customer contact staff need.
 */
data class TravelRequestPublicResponse(
    val reference: String,
    val status: TravelRequestStatus,
    val serviceName: LocalizedTextDto,
    val optionLabel: LocalizedTextDto,
    @field:JsonFormat(shape = JsonFormat.Shape.STRING)
    val priceAmount: BigDecimal,
    val priceCurrency: String,
    val priceBasis: PriceBasis,
    val cancellationPolicy: LocalizedTextDto,
    val requestedDate: LocalDate,
    val requestedTime: LocalTime?,
    val adults: Int,
    val children: Int,
    val hotelOrPickup: String?,
    val createdAt: Instant,
    val confirmedAt: Instant?,
    val completedAt: Instant?,
    val cancelledAt: Instant?,
    val expiredAt: Instant?,
)

data class TravelRequestStaffResponse(
    val id: UUID,
    val reference: String,
    val status: TravelRequestStatus,
    val serviceId: UUID,
    val serviceOptionId: UUID,
    val serviceName: LocalizedTextDto,
    val optionLabel: LocalizedTextDto,
    @field:JsonFormat(shape = JsonFormat.Shape.STRING)
    val priceAmount: BigDecimal,
    val priceCurrency: String,
    val priceBasis: PriceBasis,
    val cancellationPolicy: LocalizedTextDto,
    val requestedDate: LocalDate,
    val requestedTime: LocalTime?,
    val adults: Int,
    val children: Int,
    val hotelOrPickup: String?,
    val locale: String,
    val notes: String?,
    val sourceChannel: TravelRequestSourceChannel,
    val customer: TravelRequestCustomerDto,
    val createdAt: Instant,
    val confirmedAt: Instant?,
    val completedAt: Instant?,
    val cancelledAt: Instant?,
    val cancelReason: TravelRequestCancelReason?,
    val cancelDetail: String?,
    val expiredAt: Instant?,
)

data class TravelRequestAuditEventResponse(
    val id: UUID,
    val occurredAt: Instant,
    val fromStatus: TravelRequestStatus?,
    val toStatus: TravelRequestStatus,
    val actorType: TravelRequestActorType,
    val actorUserId: UUID?,
    val reason: TravelRequestCancelReason?,
    val detail: String?,
)
