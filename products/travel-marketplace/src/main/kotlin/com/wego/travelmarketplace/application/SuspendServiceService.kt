package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.AuditAggregateType
import com.wego.travelmarketplace.domain.Service
import com.wego.travelmarketplace.domain.ServiceId
import com.wego.travelmarketplace.domain.ServiceStatus
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface SuspendServiceResult {
    data class Suspended(
        val service: Service,
    ) : SuspendServiceResult

    data object NotFound : SuspendServiceResult

    data object InvalidTransition : SuspendServiceResult
}

class SuspendServiceService(
    private val serviceRepository: ServiceRepository,
    private val auditRecorder: TravelMarketplaceAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun suspend(
        serviceId: ServiceId,
        actorUserId: UUID?,
    ): SuspendServiceResult =
        transactionRunner.runInTransaction {
            val service = serviceRepository.findByIdForUpdate(serviceId) ?: return@runInTransaction SuspendServiceResult.NotFound
            if (service.status != ServiceStatus.PUBLISHED) return@runInTransaction SuspendServiceResult.InvalidTransition

            service.suspend()
            serviceRepository.save(service)
            auditRecorder.record(AuditAggregateType.SERVICE, service.id.value, "SUSPENDED", actorUserId, Instant.now(clock))
            SuspendServiceResult.Suspended(service)
        }
}
