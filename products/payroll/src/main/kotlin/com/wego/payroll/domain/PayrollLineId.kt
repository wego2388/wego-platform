package com.wego.payroll.domain

import java.util.UUID

@JvmInline
value class PayrollLineId(
    val value: UUID,
) {
    companion object {
        fun generate(): PayrollLineId = PayrollLineId(UUID.randomUUID())
    }
}
