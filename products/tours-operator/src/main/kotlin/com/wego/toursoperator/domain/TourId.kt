package com.wego.toursoperator.domain

import java.util.UUID

@JvmInline
value class TourId(
    val value: UUID,
) {
    companion object {
        fun generate(): TourId = TourId(UUID.randomUUID())
    }
}
