package com.wego.accounting.domain

import java.util.UUID

@JvmInline
value class JournalLineId(
    val value: UUID,
) {
    companion object {
        fun generate(): JournalLineId = JournalLineId(UUID.randomUUID())
    }
}
