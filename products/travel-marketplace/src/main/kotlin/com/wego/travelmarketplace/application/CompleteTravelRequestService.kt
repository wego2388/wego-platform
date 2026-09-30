package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.TravelRequest
import com.wego.travelmarketplace.domain.TravelRequestActorType
import com.wego.travelmarketplace.domain.TravelRequestId
import com.wego.travelmarketplace.domain.TravelRequestStatus
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface CompleteTravelRequestResult {
    data class Completed(
        val request: TravelRequest,
    ) : CompleteTravelRequestResult

    data object NotFound : CompleteTravelRequestResult

    data object InvalidTransition : CompleteTravelRequestResult
}

class CompleteTravelRequestService(
    private val requestRepository: TravelRequestRepository,
    private val auditRecorder: TravelRequestAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun complete(
        id: TravelRequestId,
        actorUserId: UUID,
        correlationId: UUID?,
    ): CompleteTravelRequestResult =
        transactionRunner.runInTransaction {
            val request = requestRepository.findByIdForUpdate(id) ?: return@runInTransaction CompleteTravelRequestResult.NotFound
            if (request.status != TravelRequestStatus.CONFIRMED) return@runInTransaction CompleteTravelRequestResult.InvalidTransition

            val now = Instant.now(clock)
            request.complete(now)
            requestRepository.save(request)
            auditRecorder.record(
                requestId = request.id.value,
                fromStatus = TravelRequestStatus.CONFIRMED,
                toStatus = request.status,
                actorType = TravelRequestActorType.STAFF,
                actorUserId = actorUserId,
                reason = null,
                detail = null,
                correlationId = correlationId,
                occurredAt = now,
            )
            CompleteTravelRequestResult.Completed(request)
        }
}
