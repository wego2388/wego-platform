package com.wego.divers.domain

import java.util.UUID

@JvmInline
value class BoatCharterId(
    val value: UUID,
) {
    companion object {
        fun generate(): BoatCharterId = BoatCharterId(UUID.randomUUID())
    }
}
