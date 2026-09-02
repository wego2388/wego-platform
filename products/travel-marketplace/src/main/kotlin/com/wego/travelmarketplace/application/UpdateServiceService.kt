package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.AuditAggregateType
import com.wego.travelmarketplace.domain.CategoryId
import com.wego.travelmarketplace.domain.ConfirmationType
import com.wego.travelmarketplace.domain.FulfilmentModel
import com.wego.travelmarketplace.domain.LocalizedText
import com.wego.travelmarketplace.domain.ProviderId
import com.wego.travelmarketplace.domain.Service
import com.wego.travelmarketplace.domain.ServiceId
import com.wego.travelmarketplace.domain.ServiceMedia
import com.wego.travelmarketplace.domain.ServiceOption
import com.wego.travelmarketplace.domain.ServiceStatus
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class UpdateServiceCommand(
    val serviceId: ServiceId,
    val categoryId: CategoryId,
    val name: LocalizedText,
    val description: LocalizedText,
    val fulfilmentModel: FulfilmentModel,
    val providerId: ProviderId?,
    val confirmationType: ConfirmationType,
    val cancellationPolicy: LocalizedText,
    val pickupInfo: LocalizedText?,
    val inclusions: LocalizedText?,
    val exclusions: LocalizedText?,
    val options: List<ServiceOption>,
    val media: List<ServiceMedia>,
    val actorUserId: UUID?,
)

sealed interface UpdateServiceResult {
    data class Updated(
        val service: Service,
    ) : UpdateServiceResult

    data object NotFound : UpdateServiceResult

    data object Archived : UpdateServiceResult

    data object CategoryNotFound : UpdateServiceResult

    data object ProviderNotFound : UpdateServiceResult
}

/**
 * A service may be edited in any non-archived status, including `PUBLISHED`
 * — a live listing's content can still be corrected. Editing never resets
 * its lifecycle status back to `DRAFT`; that would silently un-publish a
 * live service as a side effect of a typo fix, which is worse than allowing
 * the (rare, staff-only, permission-gated) edit-while-live case.
 */
class UpdateServiceService(
    private val serviceRepository: ServiceRepository,
    private val categoryRepository: CategoryRepository,
    private val providerRepository: ProviderRepository,
    private val auditRecorder: TravelMarketplaceAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun update(command: UpdateServiceCommand): UpdateServiceResult =
        transactionRunner.runInTransaction {
            val existing = serviceRepository.findByIdForUpdate(command.serviceId) ?: return@runInTransaction UpdateServiceResult.NotFound
            if (existing.status == ServiceStatus.ARCHIVED) {
                return@runInTransaction UpdateServiceResult.Archived
            }
            if (categoryRepository.findById(command.categoryId) == null) return@runInTransaction UpdateServiceResult.CategoryNotFound
            if (command.providerId != null && providerRepository.findById(command.providerId) == null) {
                return@runInTransaction UpdateServiceResult.ProviderNotFound
            }

            val updated =
                existing.withUpdatedDetails(
                    categoryId = command.categoryId,
                    name = command.name,
                    description = command.description,
                    fulfilmentModel = command.fulfilmentModel,
                    providerId = command.providerId,
                    confirmationType = command.confirmationType,
                    cancellationPolicy = command.cancellationPolicy,
                    pickupInfo = command.pickupInfo,
                    inclusions = command.inclusions,
                    exclusions = command.exclusions,
                    options = command.options,
                    media = command.media,
                )
            serviceRepository.save(updated)
            val now = Instant.now(clock)
            auditRecorder.record(AuditAggregateType.SERVICE, updated.id.value, "UPDATED", command.actorUserId, now)
            UpdateServiceResult.Updated(updated)
        }
}
