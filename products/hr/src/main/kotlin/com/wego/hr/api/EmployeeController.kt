package com.wego.hr.api

import com.wego.events.CorrelationContext
import com.wego.hr.application.CreateEmployeeResult
import com.wego.hr.application.CreateEmployeeService
import com.wego.hr.application.EmployeeQueryService
import com.wego.hr.application.TerminateEmployeeResult
import com.wego.hr.application.TerminateEmployeeService
import com.wego.hr.application.UpdateEmployeeResult
import com.wego.hr.application.UpdateEmployeeService
import com.wego.hr.domain.EmployeeId
import com.wego.hr.domain.EmployeeStatus
import com.wego.identity.AuthenticatedUser
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Validated
@RestController
@RequestMapping("/api/v1/hr/employees")
class EmployeeController(
    private val createEmployeeService: CreateEmployeeService,
    private val updateEmployeeService: UpdateEmployeeService,
    private val terminateEmployeeService: TerminateEmployeeService,
    private val employeeQueryService: EmployeeQueryService,
) {
    @PostMapping
    @PreAuthorize("hasAuthority('hr:employee-manage')")
    fun create(
        @Valid @RequestBody request: UpsertEmployeeRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                createEmployeeService.create(
                    fullName = request.fullName,
                    position = request.position,
                    department = request.department,
                    hireDate = request.hireDate,
                    email = request.email,
                    phone = request.phone,
                    baseSalary = request.baseSalary?.toDomain(),
                    linkedUserId = request.linkedUserId,
                    createdByUserId = actorUserId,
                    correlationId = CorrelationContext.currentCorrelationId(),
                )
        ) {
            is CreateEmployeeResult.Created -> ResponseEntity.status(HttpStatus.CREATED).body(result.employee.toResponse())
            CreateEmployeeResult.LinkedUserNotActiveStaff ->
                ResponseEntity.badRequest().body(EmployeeErrorResponse("linked_user_not_active_staff"))
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('hr:employee-view')")
    fun list(
        @RequestParam(required = false) status: EmployeeStatus?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) size: Int,
    ): List<EmployeeSummaryResponse> = employeeQueryService.list(status, search, page, size).map { it.toSummaryResponse() }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('hr:employee-view')")
    fun get(
        @PathVariable id: UUID,
    ): ResponseEntity<EmployeeResponse> {
        val employee = employeeQueryService.findById(EmployeeId(id)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(employee.toResponse())
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('hr:employee-manage')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpsertEmployeeRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                updateEmployeeService.update(
                    id = EmployeeId(id),
                    fullName = request.fullName,
                    position = request.position,
                    department = request.department,
                    email = request.email,
                    phone = request.phone,
                    baseSalary = request.baseSalary?.toDomain(),
                    linkedUserId = request.linkedUserId,
                    actorUserId = actorUserId,
                    correlationId = CorrelationContext.currentCorrelationId(),
                )
        ) {
            is UpdateEmployeeResult.Updated -> ResponseEntity.ok(result.employee.toResponse())
            UpdateEmployeeResult.NotFound -> ResponseEntity.notFound().build()
            UpdateEmployeeResult.LinkedUserNotActiveStaff ->
                ResponseEntity.badRequest().body(EmployeeErrorResponse("linked_user_not_active_staff"))
        }
    }

    @PostMapping("/{id}/terminate")
    @PreAuthorize("hasAuthority('hr:employee-manage')")
    fun terminate(
        @PathVariable id: UUID,
        @RequestBody(required = false) request: TerminateEmployeeRequest?,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                terminateEmployeeService.terminate(
                    id = EmployeeId(id),
                    actorUserId = actorUserId,
                    reason = request?.reason,
                    correlationId = CorrelationContext.currentCorrelationId(),
                )
        ) {
            is TerminateEmployeeResult.Terminated -> ResponseEntity.ok(result.employee.toResponse())
            TerminateEmployeeResult.NotFound -> ResponseEntity.notFound().build()
            TerminateEmployeeResult.AlreadyTerminated ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(EmployeeErrorResponse("already_terminated"))
        }
    }
}

data class EmployeeErrorResponse(
    val error: String,
)
