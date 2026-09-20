package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.AuditAggregateType
import com.wego.travelmarketplace.domain.Provider
import com.wego.travelmarketplace.domain.ProviderId
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class CreateProviderCommand(
    val name: String,
    val contactEmail: String?,
    val contactPhone: String?,
    val actorUserId: UUID?,
)

class CreateProviderService(
    private val providerRepository: ProviderRepository,
    private val auditRecorder: TravelMarketplaceAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun create(command: CreateProviderCommand): Provider =
        transactionRunner.runInTransaction {
            val now = Instant.now(clock)
            val provider =
                Provider.create(
                    id = ProviderId.generate(),
                    name = command.name,
                    contactEmail = command.contactEmail,
                    contactPhone = command.contactPhone,
                    now = now,
                )
            providerRepository.save(provider)
            auditRecorder.record(AuditAggregateType.PROVIDER, provider.id.value, "CREATED", command.actorUserId, now)
            provider
        }
}
