package com.wego.hr.domain

import java.util.UUID

@JvmInline
value class LeaveRequestId(
    val value: UUID,
) {
    companion object {
        fun generate(): LeaveRequestId = LeaveRequestId(UUID.randomUUID())
    }
}
