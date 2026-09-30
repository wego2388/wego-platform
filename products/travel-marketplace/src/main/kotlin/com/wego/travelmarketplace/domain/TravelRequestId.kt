package com.wego.travelmarketplace.domain

import java.util.UUID

@JvmInline
value class TravelRequestId(
    val value: UUID,
) {
    companion object {
        fun generate(): TravelRequestId = TravelRequestId(UUID.randomUUID())
    }
}
