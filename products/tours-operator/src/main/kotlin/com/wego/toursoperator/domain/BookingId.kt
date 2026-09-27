package com.wego.toursoperator.domain

import java.util.UUID

@JvmInline
value class BookingId(
    val value: UUID,
) {
    companion object {
        fun generate(): BookingId = BookingId(UUID.randomUUID())
    }
}
