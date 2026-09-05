package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.AuditAggregateType
import com.wego.travelmarketplace.domain.Provider
import com.wego.travelmarketplace.domain.ProviderId
import java.time.Clock
import java.time.Instant
import java.util.UUID

sealed interface ArchiveProviderResult {
    data class Archived(
        val provider: Provider,
    ) : ArchiveProviderResult

    data object NotFound : ArchiveProviderResult

    data object AlreadyArchived : ArchiveProviderResult
}

class ArchiveProviderService(
    private val providerRepository: ProviderRepository,
    private val auditRecorder: TravelMarketplaceAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun archive(
        providerId: ProviderId,
        actorUserId: UUID?,
    ): ArchiveProviderResult =
        transactionRunner.runInTransaction {
            val provider = providerRepository.findByIdForUpdate(providerId) ?: return@runInTransaction ArchiveProviderResult.NotFound
            if (!provider.isActive) return@runInTransaction ArchiveProviderResult.AlreadyArchived

            val now = Instant.now(clock)
            provider.archive(now)
            providerRepository.save(provider)
            auditRecorder.record(AuditAggregateType.PROVIDER, provider.id.value, "ARCHIVED", actorUserId, now)
            ArchiveProviderResult.Archived(provider)
        }
}
