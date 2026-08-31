package com.wego.payroll.api

import com.wego.events.CorrelationContext
import com.wego.identity.AuthenticatedUser
import com.wego.payroll.application.CreatePayrollRunResult
import com.wego.payroll.application.CreatePayrollRunService
import com.wego.payroll.application.DiscardPayrollRunResult
import com.wego.payroll.application.DiscardPayrollRunService
import com.wego.payroll.application.PayrollRunQueryService
import com.wego.payroll.application.PostPayrollRunResult
import com.wego.payroll.application.PostPayrollRunService
import com.wego.payroll.domain.PayrollRunId
import com.wego.payroll.domain.PayrollRunStatus
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
@RequestMapping("/api/v1/payroll/runs")
class PayrollController(
    private val createPayrollRunService: CreatePayrollRunService,
    private val postPayrollRunService: PostPayrollRunService,
    private val discardPayrollRunService: DiscardPayrollRunService,
    private val payrollRunQueryService: PayrollRunQueryService,
) {
    @PostMapping
    @PreAuthorize("hasAuthority('payroll:manage')")
    fun create(
        @Valid @RequestBody request: CreatePayrollRunRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result = createPayrollRunService.create(request.payPeriodStart, request.payPeriodEnd, actorUserId)
        ) {
            is CreatePayrollRunResult.Created -> ResponseEntity.status(HttpStatus.CREATED).body(result.payrollRun.toResponse())
            CreatePayrollRunResult.NoEligibleEmployees -> ResponseEntity.badRequest().body(PayrollErrorResponse("no_eligible_employees"))
            is CreatePayrollRunResult.MixedCurrencies -> ResponseEntity.badRequest().body(PayrollErrorResponse("mixed_currencies"))
            CreatePayrollRunResult.OverlapsExistingRun ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(PayrollErrorResponse("overlaps_existing_run"))
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('payroll:view')")
    fun list(
        @RequestParam(required = false) status: PayrollRunStatus?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) size: Int,
    ): List<PayrollRunResponse> = payrollRunQueryService.list(status, page, size).map { it.toResponse() }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('payroll:view')")
    fun get(
        @PathVariable id: UUID,
    ): ResponseEntity<PayrollRunResponse> {
        val run = payrollRunQueryService.findById(PayrollRunId(id)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(run.toResponse())
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('payroll:manage')")
    fun post(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result = postPayrollRunService.post(PayrollRunId(id), actorUserId, CorrelationContext.currentCorrelationId())
        ) {
            is PostPayrollRunResult.Posted -> ResponseEntity.ok(result.payrollRun.toResponse())
            PostPayrollRunResult.NotFound -> ResponseEntity.notFound().build()
            PostPayrollRunResult.NotDraft -> ResponseEntity.status(HttpStatus.CONFLICT).body(PayrollErrorResponse("not_draft"))
            PostPayrollRunResult.SalariesExpenseAccountNotFound ->
                ResponseEntity.badRequest().body(PayrollErrorResponse("salaries_expense_account_not_found"))
            PostPayrollRunResult.SalariesExpenseAccountInactive ->
                ResponseEntity.badRequest().body(PayrollErrorResponse("salaries_expense_account_inactive"))
            PostPayrollRunResult.WagesPayableAccountNotFound ->
                ResponseEntity.badRequest().body(PayrollErrorResponse("wages_payable_account_not_found"))
            PostPayrollRunResult.WagesPayableAccountInactive ->
                ResponseEntity.badRequest().body(PayrollErrorResponse("wages_payable_account_inactive"))
        }
    }

    @PostMapping("/{id}/discard")
    @PreAuthorize("hasAuthority('payroll:manage')")
    fun discard(
        @PathVariable id: UUID,
    ): ResponseEntity<Any> =
        when (discardPayrollRunService.discard(PayrollRunId(id))) {
            DiscardPayrollRunResult.Discarded -> ResponseEntity.noContent().build()
            DiscardPayrollRunResult.NotFound -> ResponseEntity.notFound().build()
            DiscardPayrollRunResult.NotDraft -> ResponseEntity.status(HttpStatus.CONFLICT).body(PayrollErrorResponse("not_draft"))
        }
}
