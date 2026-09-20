package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.AuditAggregateType
import com.wego.travelmarketplace.domain.Service
import com.wego.travelmarketplace.domain.ServiceId
import com.wego.travelmarketplace.domain.ServiceStatus
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface ArchiveServiceResult {
    data class Archived(
        val service: Service,
    ) : ArchiveServiceResult

    data object NotFound : ArchiveServiceResult

    data object AlreadyArchived : ArchiveServiceResult
}

class ArchiveServiceService(
    private val serviceRepository: ServiceRepository,
    private val auditRecorder: TravelMarketplaceAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun archive(
        serviceId: ServiceId,
        actorUserId: UUID?,
    ): ArchiveServiceResult =
        transactionRunner.runInTransaction {
            val service = serviceRepository.findByIdForUpdate(serviceId) ?: return@runInTransaction ArchiveServiceResult.NotFound
            if (service.status == ServiceStatus.ARCHIVED) return@runInTransaction ArchiveServiceResult.AlreadyArchived

            val now = Instant.now(clock)
            service.archive(now)
            serviceRepository.save(service)
            auditRecorder.record(AuditAggregateType.SERVICE, service.id.value, "ARCHIVED", actorUserId, now)
            ArchiveServiceResult.Archived(service)
        }
}
