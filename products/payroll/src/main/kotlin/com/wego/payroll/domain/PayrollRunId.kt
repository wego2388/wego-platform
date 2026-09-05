package com.wego.payroll.domain

import java.util.UUID

@JvmInline
value class PayrollRunId(
    val value: UUID,
) {
    companion object {
        fun generate(): PayrollRunId = PayrollRunId(UUID.randomUUID())
    }
}
