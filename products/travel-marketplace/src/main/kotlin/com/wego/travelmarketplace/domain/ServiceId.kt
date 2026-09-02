package com.wego.travelmarketplace.domain

import java.util.UUID

@JvmInline
value class ServiceId(
    val value: UUID,
) {
    companion object {
        fun generate(): ServiceId = ServiceId(UUID.randomUUID())
    }
}
