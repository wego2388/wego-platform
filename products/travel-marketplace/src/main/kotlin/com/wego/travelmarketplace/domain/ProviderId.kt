package com.wego.travelmarketplace.domain

import java.util.UUID

@JvmInline
value class ProviderId(
    val value: UUID,
) {
    companion object {
        fun generate(): ProviderId = ProviderId(UUID.randomUUID())
    }
}
