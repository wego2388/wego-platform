package com.wego.hr.application

import com.wego.hr.domain.AttendanceRecord
import com.wego.hr.domain.EmployeeId
import java.time.LocalDate

class AttendanceQueryService(
    private val attendanceRecordRepository: AttendanceRecordRepository,
) {
    fun list(
        employeeId: EmployeeId?,
        from: LocalDate?,
        to: LocalDate?,
        page: Int = 0,
        size: Int = Pagination.DEFAULT_PAGE_SIZE,
    ): List<AttendanceRecord> =
        attendanceRecordRepository.findAll(
            employeeId,
            from,
            to,
            limit = Pagination.boundedSize(size),
            offset = Pagination.offsetFor(page, size),
        )
}
