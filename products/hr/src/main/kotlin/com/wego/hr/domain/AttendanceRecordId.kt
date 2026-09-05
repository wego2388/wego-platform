package com.wego.hr.domain

import java.util.UUID

@JvmInline
value class AttendanceRecordId(
    val value: UUID,
) {
    companion object {
        fun generate(): AttendanceRecordId = AttendanceRecordId(UUID.randomUUID())
    }
}
