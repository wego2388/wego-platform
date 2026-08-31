package com.wego.hr.api

import com.wego.events.CorrelationContext
import com.wego.hr.application.ApproveLeaveRequestResult
import com.wego.hr.application.ApproveLeaveRequestService
import com.wego.hr.application.CancelLeaveRequestResult
import com.wego.hr.application.CancelLeaveRequestService
import com.wego.hr.application.LeaveRequestQueryService
import com.wego.hr.application.RejectLeaveRequestResult
import com.wego.hr.application.RejectLeaveRequestService
import com.wego.hr.application.SubmitLeaveRequestResult
import com.wego.hr.application.SubmitLeaveRequestService
import com.wego.hr.domain.EmployeeId
import com.wego.hr.domain.LeaveRequestId
import com.wego.hr.domain.LeaveRequestStatus
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
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Validated
@RestController
@RequestMapping("/api/v1/hr/leave-requests")
class LeaveRequestController(
    private val submitLeaveRequestService: SubmitLeaveRequestService,
    private val approveLeaveRequestService: ApproveLeaveRequestService,
    private val rejectLeaveRequestService: RejectLeaveRequestService,
    private val cancelLeaveRequestService: CancelLeaveRequestService,
    private val leaveRequestQueryService: LeaveRequestQueryService,
) {
    @PostMapping
    @PreAuthorize("hasAuthority('hr:leave-manage')")
    fun submit(
        @Valid @RequestBody request: SubmitLeaveRequestRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                submitLeaveRequestService.submit(
                    employeeId = EmployeeId(request.employeeId),
                    leaveType = request.leaveType,
                    startDate = request.startDate,
                    endDate = request.endDate,
                    reason = request.reason,
                    requestedByUserId = actorUserId,
                    correlationId = CorrelationContext.currentCorrelationId(),
                )
        ) {
            is SubmitLeaveRequestResult.Submitted -> ResponseEntity.status(HttpStatus.CREATED).body(result.leaveRequest.toResponse())
            SubmitLeaveRequestResult.EmployeeNotFound -> ResponseEntity.badRequest().body(LeaveRequestErrorResponse("employee_not_found"))
            SubmitLeaveRequestResult.EmployeeNotActive -> ResponseEntity.badRequest().body(LeaveRequestErrorResponse("employee_not_active"))
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('hr:leave-view')")
    fun list(
        @RequestParam(required = false) employeeId: UUID?,
        @RequestParam(required = false) status: LeaveRequestStatus?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) size: Int,
    ): List<LeaveRequestResponse> = leaveRequestQueryService.list(employeeId?.let(::EmployeeId), status, page, size).map { it.toResponse() }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('hr:leave-view')")
    fun get(
        @PathVariable id: UUID,
    ): ResponseEntity<LeaveRequestResponse> {
        val leaveRequest = leaveRequestQueryService.findById(LeaveRequestId(id)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(leaveRequest.toResponse())
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('hr:leave-manage')")
    fun approve(
        @PathVariable id: UUID,
        @RequestBody(required = false) request: LeaveDecisionRequest?,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                approveLeaveRequestService.approve(
                    id = LeaveRequestId(id),
                    actorUserId = actorUserId,
                    notes = request?.notes,
                    correlationId = CorrelationContext.currentCorrelationId(),
                )
        ) {
            is ApproveLeaveRequestResult.Approved -> ResponseEntity.ok(result.leaveRequest.toResponse())
            ApproveLeaveRequestResult.NotFound -> ResponseEntity.notFound().build()
            ApproveLeaveRequestResult.NotPending ->
                ResponseEntity
                    .status(
                        HttpStatus.CONFLICT,
                    ).body(LeaveRequestErrorResponse("not_pending"))
            ApproveLeaveRequestResult.OverlapsApprovedLeave ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(LeaveRequestErrorResponse("overlaps_approved_leave"))
        }
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('hr:leave-manage')")
    fun reject(
        @PathVariable id: UUID,
        @RequestBody(required = false) request: LeaveDecisionRequest?,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                rejectLeaveRequestService.reject(
                    id = LeaveRequestId(id),
                    actorUserId = actorUserId,
                    notes = request?.notes,
                    correlationId = CorrelationContext.currentCorrelationId(),
                )
        ) {
            is RejectLeaveRequestResult.Rejected -> ResponseEntity.ok(result.leaveRequest.toResponse())
            RejectLeaveRequestResult.NotFound -> ResponseEntity.notFound().build()
            RejectLeaveRequestResult.NotPending -> ResponseEntity.status(HttpStatus.CONFLICT).body(LeaveRequestErrorResponse("not_pending"))
        }
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('hr:leave-manage')")
    fun cancel(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                cancelLeaveRequestService.cancel(
                    id = LeaveRequestId(id),
                    actorUserId = actorUserId,
                    correlationId = CorrelationContext.currentCorrelationId(),
                )
        ) {
            is CancelLeaveRequestResult.Cancelled -> ResponseEntity.ok(result.leaveRequest.toResponse())
            CancelLeaveRequestResult.NotFound -> ResponseEntity.notFound().build()
            CancelLeaveRequestResult.NotPending -> ResponseEntity.status(HttpStatus.CONFLICT).body(LeaveRequestErrorResponse("not_pending"))
        }
    }
}
