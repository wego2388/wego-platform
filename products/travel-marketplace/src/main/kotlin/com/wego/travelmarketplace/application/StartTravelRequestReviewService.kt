package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.TravelRequest
import com.wego.travelmarketplace.domain.TravelRequestActorType
import com.wego.travelmarketplace.domain.TravelRequestId
import com.wego.travelmarketplace.domain.TravelRequestStatus
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface StartTravelRequestReviewResult {
    data class Started(
        val request: TravelRequest,
    ) : StartTravelRequestReviewResult

    data object NotFound : StartTravelRequestReviewResult

    data object InvalidTransition : StartTravelRequestReviewResult
}

class StartTravelRequestReviewService(
    private val requestRepository: TravelRequestRepository,
    private val auditRecorder: TravelRequestAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun start(
        id: TravelRequestId,
        actorUserId: UUID,
        correlationId: UUID?,
    ): StartTravelRequestReviewResult =
        transactionRunner.runInTransaction {
            val request = requestRepository.findByIdForUpdate(id) ?: return@runInTransaction StartTravelRequestReviewResult.NotFound
            if (request.status != TravelRequestStatus.NEW) return@runInTransaction StartTravelRequestReviewResult.InvalidTransition

            request.startReview()
            requestRepository.save(request)
            auditRecorder.record(
                requestId = request.id.value,
                fromStatus = TravelRequestStatus.NEW,
                toStatus = request.status,
                actorType = TravelRequestActorType.STAFF,
                actorUserId = actorUserId,
                reason = null,
                detail = null,
                correlationId = correlationId,
                occurredAt = Instant.now(clock),
            )
            StartTravelRequestReviewResult.Started(request)
        }
}
