package com.wego.divers.domain

import java.util.UUID

@JvmInline
value class EquipmentId(
    val value: UUID,
) {
    companion object {
        fun generate(): EquipmentId = EquipmentId(UUID.randomUUID())
    }
}
