package com.wego.hr.domain

import java.util.UUID

@JvmInline
value class EmployeeId(
    val value: UUID,
) {
    companion object {
        fun generate(): EmployeeId = EmployeeId(UUID.randomUUID())
    }
}
