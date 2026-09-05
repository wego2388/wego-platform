package com.wego.accounting.domain

import java.util.UUID

@JvmInline
value class JournalEntryId(
    val value: UUID,
) {
    companion object {
        fun generate(): JournalEntryId = JournalEntryId(UUID.randomUUID())
    }
}
