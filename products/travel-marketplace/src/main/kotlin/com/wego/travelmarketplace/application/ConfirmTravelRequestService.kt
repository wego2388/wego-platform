package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.TravelRequest
import com.wego.travelmarketplace.domain.TravelRequestActorType
import com.wego.travelmarketplace.domain.TravelRequestId
import com.wego.travelmarketplace.domain.TravelRequestStatus
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface ConfirmTravelRequestResult {
    data class Confirmed(
        val request: TravelRequest,
    ) : ConfirmTravelRequestResult

    data object NotFound : ConfirmTravelRequestResult

    data object InvalidTransition : ConfirmTravelRequestResult
}

/** Staff-initiated confirmation — a `STAFF_REVIEW` service's request, or any `NEW`/`IN_REVIEW` request staff confirms manually. */
class ConfirmTravelRequestService(
    private val requestRepository: TravelRequestRepository,
    private val auditRecorder: TravelRequestAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun confirm(
        id: TravelRequestId,
        actorUserId: UUID,
        correlationId: UUID?,
    ): ConfirmTravelRequestResult =
        transactionRunner.runInTransaction {
            val request = requestRepository.findByIdForUpdate(id) ?: return@runInTransaction ConfirmTravelRequestResult.NotFound
            if (request.status != TravelRequestStatus.NEW && request.status != TravelRequestStatus.IN_REVIEW) {
                return@runInTransaction ConfirmTravelRequestResult.InvalidTransition
            }

            val fromStatus = request.status
            val now = Instant.now(clock)
            request.confirm(now)
            requestRepository.save(request)
            auditRecorder.record(
                requestId = request.id.value,
                fromStatus = fromStatus,
                toStatus = request.status,
                actorType = TravelRequestActorType.STAFF,
                actorUserId = actorUserId,
                reason = null,
                detail = null,
                correlationId = correlationId,
                occurredAt = now,
            )
            ConfirmTravelRequestResult.Confirmed(request)
        }
}
