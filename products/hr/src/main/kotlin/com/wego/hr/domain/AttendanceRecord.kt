package com.wego.hr.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * One real record per employee per calendar day — see the unique
 * `(employee_id, attendance_date)` constraint. Recording attendance again
 * for the same day is a real correction (an upsert via
 * [com.wego.hr.application.RecordAttendanceService]), never a second,
 * conflicting row.
 */
class AttendanceRecord(
    val id: AttendanceRecordId,
    val employeeId: EmployeeId,
    val attendanceDate: LocalDate,
    val status: AttendanceStatus,
    val clockIn: Instant?,
    val clockOut: Instant?,
    val notes: String?,
    val createdByUserId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(clockIn == null || clockOut == null || !clockOut.isBefore(clockIn)) {
            "clockOut must not be before clockIn"
        }
    }

    companion object {
        fun create(
            id: AttendanceRecordId,
            employeeId: EmployeeId,
            attendanceDate: LocalDate,
            status: AttendanceStatus,
            clockIn: Instant?,
            clockOut: Instant?,
            notes: String?,
            createdByUserId: UUID?,
            now: Instant,
        ): AttendanceRecord =
            AttendanceRecord(
                id = id,
                employeeId = employeeId,
                attendanceDate = attendanceDate,
                status = status,
                clockIn = clockIn,
                clockOut = clockOut,
                notes = notes,
                createdByUserId = createdByUserId,
                createdAt = now,
                updatedAt = now,
            )
    }
}
