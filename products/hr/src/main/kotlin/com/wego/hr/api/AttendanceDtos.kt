package com.wego.hr.api

import com.wego.hr.domain.AttendanceRecord
import com.wego.hr.domain.AttendanceStatus
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val MAX_NOTES_LENGTH = 1000

data class RecordAttendanceRequest(
    @field:NotNull
    val employeeId: UUID,
    @field:NotNull
    val attendanceDate: LocalDate,
    @field:NotNull
    val status: AttendanceStatus,
    val clockIn: Instant?,
    val clockOut: Instant?,
    @field:Size(max = MAX_NOTES_LENGTH)
    val notes: String?,
)

data class AttendanceRecordResponse(
    val id: UUID,
    val employeeId: UUID,
    val attendanceDate: LocalDate,
    val status: AttendanceStatus,
    val clockIn: Instant?,
    val clockOut: Instant?,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun AttendanceRecord.toResponse(): AttendanceRecordResponse =
    AttendanceRecordResponse(
        id = id.value,
        employeeId = employeeId.value,
        attendanceDate = attendanceDate,
        status = status,
        clockIn = clockIn,
        clockOut = clockOut,
        notes = notes,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

data class AttendanceErrorResponse(
    val error: String,
)
