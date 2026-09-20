package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.AuditAggregateType
import com.wego.travelmarketplace.domain.Provider
import com.wego.travelmarketplace.domain.ProviderId
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class UpdateProviderCommand(
    val providerId: ProviderId,
    val name: String,
    val contactEmail: String?,
    val contactPhone: String?,
    val actorUserId: UUID?,
)

sealed interface UpdateProviderResult {
    data class Updated(
        val provider: Provider,
    ) : UpdateProviderResult

    data object NotFound : UpdateProviderResult

    data object Archived : UpdateProviderResult
}

class UpdateProviderService(
    private val providerRepository: ProviderRepository,
    private val auditRecorder: TravelMarketplaceAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun update(command: UpdateProviderCommand): UpdateProviderResult =
        transactionRunner.runInTransaction {
            val existing =
                providerRepository.findByIdForUpdate(command.providerId) ?: return@runInTransaction UpdateProviderResult.NotFound
            if (!existing.isActive) return@runInTransaction UpdateProviderResult.Archived

            val updated = existing.withUpdatedDetails(command.name, command.contactEmail, command.contactPhone)
            providerRepository.save(updated)
            val now = Instant.now(clock)
            auditRecorder.record(AuditAggregateType.PROVIDER, updated.id.value, "UPDATED", command.actorUserId, now)
            UpdateProviderResult.Updated(updated)
        }
}
