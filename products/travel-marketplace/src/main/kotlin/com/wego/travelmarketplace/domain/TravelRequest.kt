package com.wego.travelmarketplace.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * A customer's request for a [Service] — **not** a confirmed booking.
 * `delivery/01_REQUEST_AND_BOOKING.md`: "A request or WhatsApp message is
 * not a confirmed booking." Mirrors [Service]'s own conventions (mutable
 * status behind a private setter, `init { require(...) }` invariants, a
 * `create()` companion, named guarded lifecycle methods).
 *
 * Commercial facts ([serviceName], [optionLabel], [price], [priceBasis],
 * [cancellationPolicy]) are snapshotted at creation from the [Service]/
 * `ServiceOption` that existed at that moment, not re-read live — so a later
 * catalog edit (price change, wording change) never retroactively changes
 * what a customer was already shown or promised.
 *
 * ## Transition table
 *
 * ```
 * NEW ------------> IN_REVIEW --------> CONFIRMED --------> COMPLETED
 *  |  (startReview,     |  (confirm,         (complete, staff actor)
 *  |   staff actor)     |   staff actor)
 *  |                    |
 *  |-- confirm(actor=null) ------------> CONFIRMED
 *  |     system auto-confirm, INSTANT services only — the application
 *  |     layer decides whether to call this immediately after create();
 *  |     the aggregate itself never auto-transitions on its own.
 *  |
 *  |-- cancel(actor, reason) ----------> CANCELLED   (from NEW/IN_REVIEW/CONFIRMED)
 *  |-- expire() -----------------------> EXPIRED     (from NEW/IN_REVIEW only, system actor)
 * ```
 *
 * `COMPLETED`/`CANCELLED`/`EXPIRED` are terminal — [TravelRequestStatus.isTerminal].
 * Request creation itself never implies confirmation, even for an
 * `INSTANT`-confirmation service: the two are always separate domain
 * actions, per `delivery/01_REQUEST_AND_BOOKING.md`'s explicit instant-vs-
 * staff rule. Capacity here means this single request's own party size
 * against the snapshotted option's `maxParticipants` — checked by the
 * application layer before [create], since a shared cross-request
 * availability pool (a `TourSlot`-style calendar) does not exist yet in this
 * product and is out of this phase's scope.
 */
class TravelRequest(
    val id: TravelRequestId,
    val reference: TravelRequestReference,
    val serviceId: ServiceId,
    val serviceOptionId: java.util.UUID,
    val serviceName: LocalizedText,
    val optionLabel: LocalizedText,
    val price: Money,
    val priceBasis: PriceBasis,
    val cancellationPolicy: LocalizedText,
    val requestedDate: LocalDate,
    val requestedTime: LocalTime?,
    val adults: Int,
    val children: Int,
    val hotelOrPickup: String?,
    val locale: String,
    val notes: String?,
    val sourceChannel: TravelRequestSourceChannel,
    val customer: TravelRequestCustomer,
    val idempotencyKey: String,
    status: TravelRequestStatus,
    val createdAt: Instant,
    confirmedAt: Instant?,
    completedAt: Instant?,
    cancelledAt: Instant?,
    cancelReason: TravelRequestCancelReason?,
    cancelDetail: String?,
    expiredAt: Instant?,
) {
    var status: TravelRequestStatus = status
        private set

    var confirmedAt: Instant? = confirmedAt
        private set

    var completedAt: Instant? = completedAt
        private set

    var cancelledAt: Instant? = cancelledAt
        private set

    var cancelReason: TravelRequestCancelReason? = cancelReason
        private set

    var cancelDetail: String? = cancelDetail
        private set

    var expiredAt: Instant? = expiredAt
        private set

    init {
        require(adults >= 1) { "At least one adult is required" }
        require(children >= 0) { "Children count must not be negative" }
        // Not a realistic party size on its own — guards the domain object
        // itself (not just the API DTO) against adults + children ever
        // approaching Int overflow, which would wrap negative and silently
        // defeat a capacity comparison.
        require(adults <= MAX_PARTY_COMPONENT) { "Adults must not exceed $MAX_PARTY_COMPONENT" }
        require(children <= MAX_PARTY_COMPONENT) { "Children must not exceed $MAX_PARTY_COMPONENT" }
        require(locale == "en" || locale == "ar") { "Locale must be en or ar" }
        require(idempotencyKey.isNotBlank()) { "Idempotency key must not be blank" }
        // Sticky "first confirmed at" marker, same pattern as Service.publishedAt: a
        // later cancel(reason) from CONFIRMED deliberately leaves confirmedAt set, so
        // only the CONFIRMED/COMPLETED-without-a-timestamp direction is actually invalid.
        require(!status.isConfirmedOrLater || confirmedAt != null) {
            "confirmedAt must be set once the request has reached CONFIRMED or later"
        }
        require((status == TravelRequestStatus.COMPLETED) == (completedAt != null)) {
            "completedAt must be set if and only if the request is completed"
        }
        require((status == TravelRequestStatus.CANCELLED) == (cancelledAt != null)) {
            "cancelledAt must be set if and only if the request is cancelled"
        }
        require((status == TravelRequestStatus.CANCELLED) == (cancelReason != null)) {
            "cancelReason must be set if and only if the request is cancelled"
        }
        require((status == TravelRequestStatus.EXPIRED) == (expiredAt != null)) {
            "expiredAt must be set if and only if the request is expired"
        }
    }

    private val TravelRequestStatus.isConfirmedOrLater: Boolean
        get() = this == TravelRequestStatus.CONFIRMED || this == TravelRequestStatus.COMPLETED

    /** `NEW -> IN_REVIEW`. A staff member has claimed this request for review. */
    fun startReview() {
        require(status == TravelRequestStatus.NEW) { "Only a new request can be moved into review" }
        status = TravelRequestStatus.IN_REVIEW
    }

    /**
     * `NEW|IN_REVIEW -> CONFIRMED`. [actorUserId] is `null` for a system
     * auto-confirmation of an `INSTANT` service — the application layer
     * decides when that applies, this method only enforces the state guard.
     */
    fun confirm(now: Instant) {
        require(status == TravelRequestStatus.NEW || status == TravelRequestStatus.IN_REVIEW) {
            "Only a new or in-review request can be confirmed"
        }
        status = TravelRequestStatus.CONFIRMED
        confirmedAt = now
    }

    /** `CONFIRMED -> COMPLETED`. */
    fun complete(now: Instant) {
        require(status == TravelRequestStatus.CONFIRMED) { "Only a confirmed request can be completed" }
        status = TravelRequestStatus.COMPLETED
        completedAt = now
    }

    /** `NEW|IN_REVIEW|CONFIRMED -> CANCELLED`. Always requires a typed reason — never a silent drop. */
    fun cancel(
        reason: TravelRequestCancelReason,
        detail: String?,
        now: Instant,
    ) {
        require(!status.isTerminal) { "A terminal request cannot be cancelled" }
        status = TravelRequestStatus.CANCELLED
        cancelledAt = now
        cancelReason = reason
        cancelDetail = detail
    }

    /** `NEW|IN_REVIEW -> EXPIRED`. System-only — never called with a staff/customer actor. */
    fun expire(now: Instant) {
        require(status == TravelRequestStatus.NEW || status == TravelRequestStatus.IN_REVIEW) {
            "Only a new or in-review request can expire"
        }
        status = TravelRequestStatus.EXPIRED
        expiredAt = now
    }

    companion object {
        const val MAX_PARTY_COMPONENT = 100

        fun create(
            id: TravelRequestId,
            reference: TravelRequestReference,
            serviceId: ServiceId,
            serviceOptionId: java.util.UUID,
            serviceName: LocalizedText,
            optionLabel: LocalizedText,
            price: Money,
            priceBasis: PriceBasis,
            cancellationPolicy: LocalizedText,
            requestedDate: LocalDate,
            requestedTime: LocalTime?,
            adults: Int,
            children: Int,
            hotelOrPickup: String?,
            locale: String,
            notes: String?,
            sourceChannel: TravelRequestSourceChannel,
            customer: TravelRequestCustomer,
            idempotencyKey: String,
            now: Instant,
        ): TravelRequest =
            TravelRequest(
                id = id,
                reference = reference,
                serviceId = serviceId,
                serviceOptionId = serviceOptionId,
                serviceName = serviceName,
                optionLabel = optionLabel,
                price = price,
                priceBasis = priceBasis,
                cancellationPolicy = cancellationPolicy,
                requestedDate = requestedDate,
                requestedTime = requestedTime,
                adults = adults,
                children = children,
                hotelOrPickup = hotelOrPickup,
                locale = locale,
                notes = notes,
                sourceChannel = sourceChannel,
                customer = customer,
                idempotencyKey = idempotencyKey,
                status = TravelRequestStatus.NEW,
                createdAt = now,
                confirmedAt = null,
                completedAt = null,
                cancelledAt = null,
                cancelReason = null,
                cancelDetail = null,
                expiredAt = null,
            )
    }
}
