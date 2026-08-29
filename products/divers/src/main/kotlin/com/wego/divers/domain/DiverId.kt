package com.wego.divers.domain

import java.util.UUID

@JvmInline
value class DiverId(
    val value: UUID,
) {
    companion object {
        fun generate(): DiverId = DiverId(UUID.randomUUID())
    }
}
