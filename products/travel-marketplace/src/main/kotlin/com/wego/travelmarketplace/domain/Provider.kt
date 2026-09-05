package com.wego.travelmarketplace.domain

import java.time.Instant

/**
 * The organization operationally/legally delivering a [Service] when it is
 * not Sharm To Go itself — see `SERVICE_OWNERSHIP.md`'s "Fulfilment owner"
 * row. Deliberately minimal per that same document: "Provider commission,
 * payout and legal documents can be added when partner settlement is
 * actually implemented. They do not block designing or building the basic
 * catalog." No commission, payout, or self-service login field exists here
 * on purpose — not an oversight.
 */
class Provider(
    val id: ProviderId,
    val name: String,
    val contactEmail: String?,
    val contactPhone: String?,
    status: ProviderStatus,
    val createdAt: Instant,
    archivedAt: Instant?,
) {
    var status: ProviderStatus = status
        private set

    var archivedAt: Instant? = archivedAt
        private set

    init {
        require(name.isNotBlank()) { "Provider name must not be blank" }
        require(!contactEmail.isNullOrBlank() || !contactPhone.isNullOrBlank()) {
            "Provider contact must include an email or a phone number"
        }
        require((status == ProviderStatus.ARCHIVED) == (archivedAt != null)) {
            "archivedAt must be set if and only if the provider is archived"
        }
    }

    val isActive: Boolean get() = status == ProviderStatus.ACTIVE

    /** Terminal: an already-archived provider cannot be archived again. */
    fun archive(now: Instant) {
        require(status == ProviderStatus.ACTIVE) { "Only an active provider can be archived" }
        status = ProviderStatus.ARCHIVED
        archivedAt = now
    }

    fun withUpdatedDetails(
        name: String,
        contactEmail: String?,
        contactPhone: String?,
    ): Provider =
        Provider(
            id = id,
            name = name,
            contactEmail = contactEmail,
            contactPhone = contactPhone,
            status = status,
            createdAt = createdAt,
            archivedAt = archivedAt,
        )

    companion object {
        fun create(
            id: ProviderId,
            name: String,
            contactEmail: String?,
            contactPhone: String?,
            now: Instant,
        ): Provider =
            Provider(
                id = id,
                name = name,
                contactEmail = contactEmail,
                contactPhone = contactPhone,
                status = ProviderStatus.ACTIVE,
                createdAt = now,
                archivedAt = null,
            )
    }
}
