package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.TravelRequest
import com.wego.travelmarketplace.domain.TravelRequestActorType
import com.wego.travelmarketplace.domain.TravelRequestCancelReason
import com.wego.travelmarketplace.domain.TravelRequestId
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class CancelTravelRequestCommand(
    val requestId: TravelRequestId,
    val reason: TravelRequestCancelReason,
    val detail: String?,
    val actorType: TravelRequestActorType,
    /** Required when [actorType] is `STAFF`; must be `null` for `CUSTOMER` — the public cancel endpoint has no authenticated identity. */
    val actorUserId: UUID?,
    val correlationId: UUID?,
)

sealed interface CancelTravelRequestResult {
    data class Cancelled(
        val request: TravelRequest,
    ) : CancelTravelRequestResult

    data object NotFound : CancelTravelRequestResult

    data object AlreadyTerminal : CancelTravelRequestResult
}

class CancelTravelRequestService(
    private val requestRepository: TravelRequestRepository,
    private val auditRecorder: TravelRequestAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun cancel(command: CancelTravelRequestCommand): CancelTravelRequestResult {
        require((command.actorType == TravelRequestActorType.STAFF) == (command.actorUserId != null)) {
            "actorUserId must be set if and only if the actor is STAFF"
        }
        return transactionRunner.runInTransaction {
            val request =
                requestRepository.findByIdForUpdate(command.requestId) ?: return@runInTransaction CancelTravelRequestResult.NotFound
            if (request.status.isTerminal) return@runInTransaction CancelTravelRequestResult.AlreadyTerminal

            val fromStatus = request.status
            val now = Instant.now(clock)
            request.cancel(command.reason, command.detail, now)
            requestRepository.save(request)
            auditRecorder.record(
                requestId = request.id.value,
                fromStatus = fromStatus,
                toStatus = request.status,
                actorType = command.actorType,
                actorUserId = command.actorUserId,
                reason = command.reason,
                detail = command.detail,
                correlationId = command.correlationId,
                occurredAt = now,
            )
            CancelTravelRequestResult.Cancelled(request)
        }
    }
}
