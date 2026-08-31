package com.wego.hr.api

import com.wego.hr.application.AttendanceQueryService
import com.wego.hr.application.RecordAttendanceResult
import com.wego.hr.application.RecordAttendanceService
import com.wego.hr.domain.EmployeeId
import com.wego.identity.AuthenticatedUser
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

@Validated
@RestController
@RequestMapping("/api/v1/hr/attendance")
class AttendanceController(
    private val recordAttendanceService: RecordAttendanceService,
    private val attendanceQueryService: AttendanceQueryService,
) {
    /** Recording again for the same employee/date corrects that day's record — an upsert, not a strict create, so this always returns 200 rather than 201. */
    @PostMapping
    @PreAuthorize("hasAuthority('hr:attendance-manage')")
    fun record(
        @Valid @RequestBody request: RecordAttendanceRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                recordAttendanceService.record(
                    employeeId = EmployeeId(request.employeeId),
                    attendanceDate = request.attendanceDate,
                    status = request.status,
                    clockIn = request.clockIn,
                    clockOut = request.clockOut,
                    notes = request.notes,
                    actorUserId = actorUserId,
                )
        ) {
            is RecordAttendanceResult.Recorded -> ResponseEntity.ok(result.record.toResponse())
            RecordAttendanceResult.EmployeeNotFound -> ResponseEntity.badRequest().body(AttendanceErrorResponse("employee_not_found"))
            RecordAttendanceResult.EmployeeNotActive -> ResponseEntity.badRequest().body(AttendanceErrorResponse("employee_not_active"))
            RecordAttendanceResult.AttendanceDateInFuture ->
                ResponseEntity.badRequest().body(AttendanceErrorResponse("attendance_date_in_future"))
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('hr:attendance-view')")
    fun list(
        @RequestParam(required = false) employeeId: UUID?,
        @RequestParam(required = false) from: LocalDate?,
        @RequestParam(required = false) to: LocalDate?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) size: Int,
    ): List<AttendanceRecordResponse> =
        attendanceQueryService.list(employeeId?.let(::EmployeeId), from, to, page, size).map { it.toResponse() }
}
