package com.wego.hr.application

import com.wego.hr.domain.AttendanceRecord
import com.wego.hr.domain.EmployeeId
import java.time.LocalDate

interface AttendanceRecordRepository {
    fun findByEmployeeAndDate(
        employeeId: EmployeeId,
        date: LocalDate,
    ): AttendanceRecord?

    fun findAll(
        employeeId: EmployeeId?,
        from: LocalDate?,
        to: LocalDate?,
        limit: Int,
        offset: Int,
    ): List<AttendanceRecord>

    /** Insert, or correct the existing record for the same `(employeeId, attendanceDate)` — see the DB's own unique constraint. */
    fun save(record: AttendanceRecord)
}
