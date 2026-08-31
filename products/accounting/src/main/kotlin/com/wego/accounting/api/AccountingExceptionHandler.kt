package com.wego.accounting.api

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
 * A single, unified `400 {"message":"..."}` shape for every way an
 * accounting request can fail framework-level validation — same discipline
 * as `com.wego.divers.api.DiversExceptionHandler`, warranted here by the
 * same reasoning: this module handles money, and a raw 500 or a leaked
 * SQL constraint name is never an acceptable failure mode for it either.
 */
@RestControllerAdvice(basePackages = ["com.wego.accounting.api"])
class AccountingExceptionHandler {
    /** Domain constructors (`Account`, `JournalEntry`, `JournalLine`) validate their own invariants via `require(...)`, throwing this with an already human-readable, safe-to-return message. */
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

    /**
     * The real backstop against two concurrent reversal requests for the
     * same journal entry racing past `ReverseJournalEntryService`'s
     * pre-check — the DB's own unique partial index on
     * `reversal_of_entry_id` is what actually prevents a double reversal;
     * this only ensures the loser of that race gets a clean 409, not a raw
     * constraint-name 500.
     */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(exception: DataIntegrityViolationException): ResponseEntity<ValidationErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ValidationErrorResponse(message = "This conflicts with an existing record"))

    private fun badRequest(message: String): ResponseEntity<ValidationErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ValidationErrorResponse(message = message))
}

data class ValidationErrorResponse(
    val message: String,
)
