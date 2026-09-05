package com.wego.hr

import com.wego.hr.domain.AttendanceRecord
import com.wego.hr.domain.AttendanceRecordId
import com.wego.hr.domain.AttendanceStatus
import com.wego.hr.domain.EmployeeId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class AttendanceRecordTest {
    private val now = Instant.parse("2026-08-31T00:00:00Z")
    private val date = LocalDate.parse("2026-08-30")

    @Test
    fun `records a present day with no clock times`() {
        val record =
            AttendanceRecord.create(
                id = AttendanceRecordId.generate(),
                employeeId = EmployeeId.generate(),
                attendanceDate = date,
                status = AttendanceStatus.PRESENT,
                clockIn = null,
                clockOut = null,
                notes = null,
                createdByUserId = null,
                now = now,
            )
        assertThat(record.status).isEqualTo(AttendanceStatus.PRESENT)
        assertThat(record.createdAt).isEqualTo(now)
        assertThat(record.updatedAt).isEqualTo(now)
    }

    @Test
    fun `rejects a clockOut before clockIn`() {
        assertThatIllegalArgumentException().isThrownBy {
            AttendanceRecord.create(
                id = AttendanceRecordId.generate(),
                employeeId = EmployeeId.generate(),
                attendanceDate = date,
                status = AttendanceStatus.PRESENT,
                clockIn = Instant.parse("2026-08-30T09:00:00Z"),
                clockOut = Instant.parse("2026-08-30T08:00:00Z"),
                notes = null,
                createdByUserId = null,
                now = now,
            )
        }
    }

    @Test
    fun `accepts a clockOut equal to clockIn`() {
        val at = Instant.parse("2026-08-30T09:00:00Z")
        val record =
            AttendanceRecord.create(
                id = AttendanceRecordId.generate(),
                employeeId = EmployeeId.generate(),
                attendanceDate = date,
                status = AttendanceStatus.PRESENT,
                clockIn = at,
                clockOut = at,
                notes = null,
                createdByUserId = null,
                now = now,
            )
        assertThat(record.clockIn).isEqualTo(record.clockOut)
    }
}
