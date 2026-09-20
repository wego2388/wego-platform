package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.AuditAggregateType
import com.wego.travelmarketplace.domain.Service
import com.wego.travelmarketplace.domain.ServiceId
import com.wego.travelmarketplace.domain.ServiceStatus
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface SubmitServiceForReviewResult {
    data class Submitted(
        val service: Service,
    ) : SubmitServiceForReviewResult

    data object NotFound : SubmitServiceForReviewResult

    data object InvalidTransition : SubmitServiceForReviewResult
}

class SubmitServiceForReviewService(
    private val serviceRepository: ServiceRepository,
    private val auditRecorder: TravelMarketplaceAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun submit(
        serviceId: ServiceId,
        actorUserId: UUID?,
    ): SubmitServiceForReviewResult =
        transactionRunner.runInTransaction {
            val service =
                serviceRepository.findByIdForUpdate(serviceId) ?: return@runInTransaction SubmitServiceForReviewResult.NotFound
            if (service.status != ServiceStatus.DRAFT) return@runInTransaction SubmitServiceForReviewResult.InvalidTransition

            service.submitForReview()
            serviceRepository.save(service)
            auditRecorder.record(AuditAggregateType.SERVICE, service.id.value, "SUBMITTED_FOR_REVIEW", actorUserId, Instant.now(clock))
            SubmitServiceForReviewResult.Submitted(service)
        }
}
