package com.wego.travelmarketplace.domain

import java.util.UUID

@JvmInline
value class CategoryId(
    val value: UUID,
) {
    companion object {
        fun generate(): CategoryId = CategoryId(UUID.randomUUID())
    }
}
