package com.wego.payroll.api

import jakarta.validation.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.ServletRequestBindingException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * A single, unified `400 {"message":"..."}` shape for every way a payroll
 * request can fail framework-level validation — same discipline as
 * `com.wego.accounting.api.AccountingExceptionHandler`, warranted here for
 * the same reason: this module posts real money into the ledger.
 */
@RestControllerAdvice(basePackages = ["com.wego.payroll.api"])
class PayrollExceptionHandler {
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(exception: IllegalArgumentException): ResponseEntity<ValidationErrorResponse> =
        badRequest(exception.message ?: "Invalid request")

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleBodyValidation(exception: MethodArgumentNotValidException): ResponseEntity<ValidationErrorResponse> =
        badRequest(
            exception.bindingResult.fieldErrors
                .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
                .ifBlank { "Invalid request body" },
        )

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleParameterValidation(exception: HandlerMethodValidationException): ResponseEntity<ValidationErrorResponse> =
        badRequest(
            exception.allErrors
                .joinToString("; ") { it.defaultMessage ?: "Invalid value" }
                .ifBlank { "Invalid request parameters" },
        )

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(exception: ConstraintViolationException): ResponseEntity<ValidationErrorResponse> =
        badRequest(
            exception.constraintViolations
                .joinToString("; ") { "${it.propertyPath}: ${it.message}" }
                .ifBlank { "Invalid request" },
        )

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(exception: MethodArgumentTypeMismatchException): ResponseEntity<ValidationErrorResponse> =
        badRequest("${exception.name}: has an invalid value")

    @ExceptionHandler(ServletRequestBindingException::class)
    fun handleRequestBinding(exception: ServletRequestBindingException): ResponseEntity<ValidationErrorResponse> =
        badRequest(exception.message ?: "A required request value is missing")

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(exception: HttpMessageNotReadableException): ResponseEntity<ValidationErrorResponse> =
        badRequest("Request body is malformed or contains an unrecognized field")

    /** The real backstop against two concurrent creates for the same overlapping pay period racing past the pre-check, or any other genuine DB-level conflict. */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(exception: DataIntegrityViolationException): ResponseEntity<ValidationErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ValidationErrorResponse(message = "This conflicts with an existing record"))

    private fun badRequest(message: String): ResponseEntity<ValidationErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ValidationErrorResponse(message = message))
}

data class ValidationErrorResponse(
    val message: String,
)
