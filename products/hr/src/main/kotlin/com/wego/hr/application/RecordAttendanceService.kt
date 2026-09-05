package com.wego.hr.application

import com.wego.hr.domain.AttendanceRecord
import com.wego.hr.domain.AttendanceRecordId
import com.wego.hr.domain.AttendanceStatus
import com.wego.hr.domain.EmployeeId
import com.wego.transaction.TransactionRunner
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

sealed interface RecordAttendanceResult {
    data class Recorded(
        val record: AttendanceRecord,
    ) : RecordAttendanceResult

    data object EmployeeNotFound : RecordAttendanceResult

    /** Attendance is a fact about a real employee still on staff — a terminated employee cannot have new attendance recorded against them. */
    data object EmployeeNotActive : RecordAttendanceResult

    /** Attendance records a fact about the past or today, never a future claim. */
    data object AttendanceDateInFuture : RecordAttendanceResult
}

class RecordAttendanceService(
    private val employeeRepository: EmployeeRepository,
    private val attendanceRecordRepository: AttendanceRecordRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun record(
        employeeId: EmployeeId,
        attendanceDate: LocalDate,
        status: AttendanceStatus,
        clockIn: Instant?,
        clockOut: Instant?,
        notes: String?,
        actorUserId: UUID?,
    ): RecordAttendanceResult =
        transactionRunner.runInTransaction {
            val employee = employeeRepository.findById(employeeId) ?: return@runInTransaction RecordAttendanceResult.EmployeeNotFound
            if (!employee.isActive) return@runInTransaction RecordAttendanceResult.EmployeeNotActive
            val now = Instant.now(clock)
            if (attendanceDate.isAfter(LocalDate.now(clock))) return@runInTransaction RecordAttendanceResult.AttendanceDateInFuture

            // Correct the existing day's record if one exists (same real-world
            // day), otherwise this is a fresh one — either way the id and
            // createdAt/createdByUserId of a prior record for this day are
            // preserved, only the observed facts and updatedAt change.
            val existing = attendanceRecordRepository.findByEmployeeAndDate(employeeId, attendanceDate)
            val record =
                AttendanceRecord(
                    id = existing?.id ?: AttendanceRecordId.generate(),
                    employeeId = employeeId,
                    attendanceDate = attendanceDate,
                    status = status,
                    clockIn = clockIn,
                    clockOut = clockOut,
                    notes = notes,
                    createdByUserId = existing?.createdByUserId ?: actorUserId,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                )
            attendanceRecordRepository.save(record)
            RecordAttendanceResult.Recorded(record)
        }
}
