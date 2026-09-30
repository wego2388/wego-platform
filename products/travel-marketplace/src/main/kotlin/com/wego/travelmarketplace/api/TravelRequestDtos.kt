package com.wego.travelmarketplace.api

import com.wego.travelmarketplace.domain.PriceBasis
import com.wego.travelmarketplace.domain.TravelRequestCancelReason
import com.wego.travelmarketplace.domain.TravelRequestSourceChannel
import com.wego.travelmarketplace.domain.TravelRequestStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Future
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
    @field:Size(max = 32) val phone: String?,
    @field:Email @field:Size(max = 320) val email: String?,
)

data class CreateTravelRequestApiRequest(
    val serviceId: UUID,
    val serviceOptionId: UUID,
    @field:Future val requestedDate: LocalDate,
    val requestedTime: LocalTime?,
    @field:Min(1) val adults: Int,
    @field:Min(0) val children: Int = 0,
    @field:Size(max = 500) val hotelOrPickup: String?,
    @field:Pattern(regexp = "^(en|ar)$") val locale: String,
    @field:Size(max = 2000) val notes: String?,
    val sourceChannel: TravelRequestSourceChannel,
    @field:Valid val customer: TravelRequestCustomerDto,
)

data class CancelTravelRequestApiRequest(
    val reason: TravelRequestCancelReason,
    @field:Size(max = 1000) val detail: String?,
)

data class TravelRequestErrorResponse(
    val error: String,
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
