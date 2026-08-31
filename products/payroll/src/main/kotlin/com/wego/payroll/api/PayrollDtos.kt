package com.wego.payroll.api

import com.wego.payroll.domain.PayrollLine
import com.wego.payroll.domain.PayrollRun
import com.wego.payroll.domain.PayrollRunStatus
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreatePayrollRunRequest(
    @field:NotNull
    val payPeriodStart: LocalDate,
    @field:NotNull
    val payPeriodEnd: LocalDate,
)

data class PayrollLineResponse(
    val employeeId: UUID,
    val amount: String,
)

fun PayrollLine.toResponse(): PayrollLineResponse = PayrollLineResponse(employeeId, amount.toPlainString())

data class PayrollRunResponse(
    val id: UUID,
    val payPeriodStart: LocalDate,
    val payPeriodEnd: LocalDate,
    val currencyCode: String,
    val totalAmount: String,
    val status: PayrollRunStatus,
    val lines: List<PayrollLineResponse>,
    val createdByUserId: UUID?,
    val createdAt: Instant,
    val postedByUserId: UUID?,
    val postedAt: Instant?,
    val journalEntryId: UUID?,
)

fun PayrollRun.toResponse(): PayrollRunResponse =
    PayrollRunResponse(
        id = id.value,
        payPeriodStart = payPeriodStart,
        payPeriodEnd = payPeriodEnd,
        currencyCode = currencyCode,
        totalAmount = totalAmount.toPlainString(),
        status = status,
        lines = lines.map { it.toResponse() },
        createdByUserId = createdByUserId,
        createdAt = createdAt,
        postedByUserId = postedByUserId,
        postedAt = postedAt,
        journalEntryId = journalEntryId,
    )

data class PayrollErrorResponse(
    val error: String,
)
