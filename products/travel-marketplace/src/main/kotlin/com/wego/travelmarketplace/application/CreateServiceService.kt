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
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class CreateServiceCommand(
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

sealed interface CreateServiceResult {
    data class Created(
        val service: Service,
    ) : CreateServiceResult

    data object CategoryNotFound : CreateServiceResult

    data object ProviderNotFound : CreateServiceResult
}

class CreateServiceService(
    private val serviceRepository: ServiceRepository,
    private val categoryRepository: CategoryRepository,
    private val providerRepository: ProviderRepository,
    private val auditRecorder: TravelMarketplaceAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun create(command: CreateServiceCommand): CreateServiceResult =
        transactionRunner.runInTransaction {
            if (categoryRepository.findById(command.categoryId) == null) return@runInTransaction CreateServiceResult.CategoryNotFound
            if (command.providerId != null && providerRepository.findById(command.providerId) == null) {
                return@runInTransaction CreateServiceResult.ProviderNotFound
            }

            val now = Instant.now(clock)
            val service =
                Service.create(
                    id = ServiceId.generate(),
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
                    now = now,
                )
            serviceRepository.save(service)
            auditRecorder.record(AuditAggregateType.SERVICE, service.id.value, "CREATED", command.actorUserId, now)
            CreateServiceResult.Created(service)
        }
}
