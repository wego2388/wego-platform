package com.wego.hr.api

import com.wego.hr.domain.Employee
import com.wego.hr.domain.EmployeeStatus
import com.wego.hr.domain.Money
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val CURRENCY_CODE_PATTERN = "^[A-Z]{3}$"
private const val MAX_NAME_LENGTH = 200
private const val MAX_EMAIL_LENGTH = 320
private const val MAX_PHONE_LENGTH = 32
private const val MAX_REASON_LENGTH = 1000

data class MoneyDto(
    @field:NotBlank
    @field:Pattern(regexp = "^\\d{1,8}\\.\\d{2}$", message = "must be a decimal amount with exactly 2 places, e.g. 15000.00")
    val amount: String,
    @field:Pattern(regexp = CURRENCY_CODE_PATTERN, message = "must be a 3-letter uppercase ISO 4217 code")
    val currencyCode: String,
)

data class UpsertEmployeeRequest(
    @field:NotBlank
    @field:Size(max = MAX_NAME_LENGTH)
    val fullName: String,
    @field:NotBlank
    @field:Size(max = MAX_NAME_LENGTH)
    val position: String,
    @field:Size(max = MAX_NAME_LENGTH)
    val department: String?,
    val hireDate: LocalDate,
    @field:Email
    @field:Size(max = MAX_EMAIL_LENGTH)
    val email: String?,
    @field:Size(max = MAX_PHONE_LENGTH)
    val phone: String?,
    val baseSalary: MoneyDto?,
    val linkedUserId: UUID?,
)

data class TerminateEmployeeRequest(
    @field:Size(max = MAX_REASON_LENGTH)
    val reason: String?,
)

data class EmployeeResponse(
    val id: UUID,
    val fullName: String,
    val position: String,
    val department: String?,
    val hireDate: LocalDate,
    val email: String?,
    val phone: String?,
    val baseSalary: MoneyDto?,
    val linkedUserId: UUID?,
    val status: EmployeeStatus,
    val createdAt: Instant,
    val terminatedAt: Instant?,
)

/** The roster/list projection deliberately omits salary — the same PII-minimization discipline `DiverSummaryResponse` (products/divers) already established for a bulk read under one broad `hr:employee-view` permission. */
data class EmployeeSummaryResponse(
    val id: UUID,
    val fullName: String,
    val position: String,
    val department: String?,
    val status: EmployeeStatus,
)

fun Employee.toResponse(): EmployeeResponse =
    EmployeeResponse(
        id = id.value,
        fullName = fullName,
        position = position,
        department = department,
        hireDate = hireDate,
        email = email,
        phone = phone,
        baseSalary = baseSalary?.let { MoneyDto(it.amount.toPlainString(), it.currencyCode) },
        linkedUserId = linkedUserId,
        status = status,
        createdAt = createdAt,
        terminatedAt = terminatedAt,
    )

fun Employee.toSummaryResponse(): EmployeeSummaryResponse =
    EmployeeSummaryResponse(id = id.value, fullName = fullName, position = position, department = department, status = status)

fun MoneyDto.toDomain(): Money = Money(java.math.BigDecimal(amount), currencyCode)
