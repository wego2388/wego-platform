package com.wego.divers.domain

import java.util.UUID

@JvmInline
value class OfferingId(
    val value: UUID,
) {
    companion object {
        fun generate(): OfferingId = OfferingId(UUID.randomUUID())
    }
}
