package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.ConfirmationType
import com.wego.travelmarketplace.domain.ServiceId
import com.wego.travelmarketplace.domain.TravelRequest
import com.wego.travelmarketplace.domain.TravelRequestActorType
import com.wego.travelmarketplace.domain.TravelRequestCustomer
import com.wego.travelmarketplace.domain.TravelRequestId
import com.wego.travelmarketplace.domain.TravelRequestReference
import com.wego.travelmarketplace.domain.TravelRequestSourceChannel
import com.wego.travelmarketplace.domain.TravelRequestStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class CreateTravelRequestCommand(
    val serviceId: ServiceId,
    val serviceOptionId: UUID,
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
    val correlationId: UUID?,
)

sealed interface CreateTravelRequestResult {
    /** A brand-new request was created. */
    data class Created(
        val request: TravelRequest,
    ) : CreateTravelRequestResult

    /**
     * [idempotencyKey] was already used — returns the request that call
     * originally created instead of creating a duplicate. Same HTTP outcome
     * either way at the controller layer (200/201 with the request), so a
     * retried submission after a dropped response is always safe.
     */
    data class AlreadyExists(
        val request: TravelRequest,
    ) : CreateTravelRequestResult

    data object ServiceNotFound : CreateTravelRequestResult

    data object OptionNotFound : CreateTravelRequestResult

    data class PartySizeExceedsCapacity(
        val maxParticipants: Int,
    ) : CreateTravelRequestResult
}

/**
 * "Capacity" here is this single request's own party size against the
 * snapshotted option's `maxParticipants` — a plain application-layer
 * validation, not a row-locked shared pool. `products/travel-marketplace`
 * has no cross-request availability concept yet (unlike, say, a per-day
 * slot/calendar system) — see `TravelRequest`'s class doc. The idempotency
 * check races only against itself: a unique DB constraint on
 * `idempotency_key` (V5 migration) is the actual safety net under two
 * concurrent submissions of the same key; the [TravelRequestRepository.findByIdempotencyKey]
 * pre-check here is the fast, common-case path.
 */
class CreateTravelRequestService(
    private val serviceRepository: ServiceRepository,
    private val requestRepository: TravelRequestRepository,
    private val auditRecorder: TravelRequestAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun create(command: CreateTravelRequestCommand): CreateTravelRequestResult =
        transactionRunner.runInTransaction {
            requestRepository.findByIdempotencyKey(command.idempotencyKey)?.let {
                return@runInTransaction CreateTravelRequestResult.AlreadyExists(it)
            }

            val service =
                serviceRepository.findPublishedById(command.serviceId)
                    ?: return@runInTransaction CreateTravelRequestResult.ServiceNotFound
            val option =
                service.options.find { it.id == command.serviceOptionId }
                    ?: return@runInTransaction CreateTravelRequestResult.OptionNotFound
            val partySize = command.adults + command.children
            if (partySize > option.maxParticipants) {
                return@runInTransaction CreateTravelRequestResult.PartySizeExceedsCapacity(option.maxParticipants)
            }

            val now = Instant.now(clock)
            var request =
                TravelRequest.create(
                    id = TravelRequestId.generate(),
                    reference = generateUniqueReference(),
                    serviceId = service.id,
                    serviceOptionId = option.id,
                    serviceName = service.name,
                    optionLabel = option.label,
                    price = option.price,
                    priceBasis = option.priceBasis,
                    cancellationPolicy = service.cancellationPolicy,
                    requestedDate = command.requestedDate,
                    requestedTime = command.requestedTime,
                    adults = command.adults,
                    children = command.children,
                    hotelOrPickup = command.hotelOrPickup,
                    locale = command.locale,
                    notes = command.notes,
                    sourceChannel = command.sourceChannel,
                    customer = command.customer,
                    idempotencyKey = command.idempotencyKey,
                    now = now,
                )
            requestRepository.save(request)
            auditRecorder.record(
                requestId = request.id.value,
                fromStatus = null,
                toStatus = request.status,
                actorType = TravelRequestActorType.CUSTOMER,
                actorUserId = null,
                reason = null,
                detail = null,
                correlationId = command.correlationId,
                occurredAt = now,
            )

            // Request receipt is never itself confirmation (delivery/01's explicit
            // rule) — but for an INSTANT service, the system performs the confirm
            // as its own separate, immediately-following domain action.
            if (service.confirmationType == ConfirmationType.INSTANT) {
                val confirmedAt = Instant.now(clock)
                request.confirm(confirmedAt)
                requestRepository.save(request)
                auditRecorder.record(
                    requestId = request.id.value,
                    fromStatus = TravelRequestStatus.NEW,
                    toStatus = request.status,
                    actorType = TravelRequestActorType.SYSTEM,
                    actorUserId = null,
                    reason = null,
                    detail = "Auto-confirmed: service confirmationType is INSTANT",
                    correlationId = command.correlationId,
                    occurredAt = confirmedAt,
                )
            }

            CreateTravelRequestResult.Created(request)
        }

    private fun generateUniqueReference(): TravelRequestReference {
        repeat(MAX_REFERENCE_ATTEMPTS) {
            val candidate = TravelRequestReference.generate()
            if (requestRepository.findByReference(candidate) == null) return candidate
        }
        error("Could not generate a unique travel request reference after $MAX_REFERENCE_ATTEMPTS attempts")
    }

    private companion object {
        const val MAX_REFERENCE_ATTEMPTS = 10
    }
}
