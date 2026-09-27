package com.wego.toursoperator.domain

import java.util.UUID

@JvmInline
value class TourSlotId(
    val value: UUID,
) {
    companion object {
        fun generate(): TourSlotId = TourSlotId(UUID.randomUUID())
    }
}
