package com.wego.accounting.domain

import java.util.UUID

@JvmInline
value class AccountId(
    val value: UUID,
) {
    companion object {
        fun generate(): AccountId = AccountId(UUID.randomUUID())
    }
}
