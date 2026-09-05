package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.AuditAggregateType
import com.wego.travelmarketplace.domain.Service
import com.wego.travelmarketplace.domain.ServiceId
import com.wego.travelmarketplace.domain.ServiceStatus
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface ApproveServiceResult {
    data class Approved(
        val service: Service,
    ) : ApproveServiceResult

    data object NotFound : ApproveServiceResult

    data object InvalidTransition : ApproveServiceResult
}

class ApproveServiceService(
    private val serviceRepository: ServiceRepository,
    private val auditRecorder: TravelMarketplaceAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun approve(
        serviceId: ServiceId,
        actorUserId: UUID?,
    ): ApproveServiceResult =
        transactionRunner.runInTransaction {
            val service = serviceRepository.findByIdForUpdate(serviceId) ?: return@runInTransaction ApproveServiceResult.NotFound
            if (service.status != ServiceStatus.REVIEW) return@runInTransaction ApproveServiceResult.InvalidTransition

            service.approve()
            serviceRepository.save(service)
            auditRecorder.record(AuditAggregateType.SERVICE, service.id.value, "APPROVED", actorUserId, Instant.now(clock))
            ApproveServiceResult.Approved(service)
        }
}
