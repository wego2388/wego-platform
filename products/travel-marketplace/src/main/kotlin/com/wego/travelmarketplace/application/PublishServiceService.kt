package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.AuditAggregateType
import com.wego.travelmarketplace.domain.Service
import com.wego.travelmarketplace.domain.ServiceId
import com.wego.travelmarketplace.domain.ServiceStatus
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface PublishServiceResult {
    data class Published(
        val service: Service,
    ) : PublishServiceResult

    data object NotFound : PublishServiceResult

    data object InvalidTransition : PublishServiceResult

    data object MissingPublishableOption : PublishServiceResult

    data object MissingRightsClearedMedia : PublishServiceResult
}

/**
 * Enforces `SERVICE_CONTENT_TEMPLATE.md`'s closing rule at the one place a
 * service can actually go live — see [Service.publish]'s own domain guard,
 * which this service supplies with real, freshly-loaded facts about the
 * aggregate's own [Service.options]/[Service.media] rather than trusting a
 * caller-supplied flag.
 */
class PublishServiceService(
    private val serviceRepository: ServiceRepository,
    private val auditRecorder: TravelMarketplaceAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun publish(
        serviceId: ServiceId,
        actorUserId: UUID?,
    ): PublishServiceResult =
        transactionRunner.runInTransaction {
            val service = serviceRepository.findByIdForUpdate(serviceId) ?: return@runInTransaction PublishServiceResult.NotFound
            if (service.status != ServiceStatus.APPROVED && service.status != ServiceStatus.SUSPENDED) {
                return@runInTransaction PublishServiceResult.InvalidTransition
            }
            if (service.options.isEmpty()) return@runInTransaction PublishServiceResult.MissingPublishableOption
            if (service.media.isEmpty()) return@runInTransaction PublishServiceResult.MissingRightsClearedMedia

            val now = Instant.now(clock)
            service.publish(hasPublishableOption = true, hasRightsClearedMedia = true, now = now)
            serviceRepository.save(service)
            auditRecorder.record(AuditAggregateType.SERVICE, service.id.value, "PUBLISHED", actorUserId, now)
            PublishServiceResult.Published(service)
        }
}
