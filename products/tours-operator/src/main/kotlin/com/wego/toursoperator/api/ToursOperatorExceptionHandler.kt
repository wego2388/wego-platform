package com.wego.toursoperator.api

import jakarta.validation.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * Unified error handler for all tours-operator API controllers.
 * Converts every failure mode to a safe, structured response — never a raw 500,
 * never a stack trace, and never a Spring default whitespace body.
 *
 * Scoped to this package only to avoid interfering with
 * com.wego.identity.api or com.wego.divers.api handlers.
 */
@RestControllerAdvice(basePackages = ["com.wego.toursoperator.api"])
class ToursOperatorExceptionHandler {
    /**
     * Domain invariants are enforced via Kotlin's `require(...)` in domain
     * constructors and state-machine methods. The messages are safe to return
     * as-is (no internal details, no SQL, no stack frames).
     */
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(exception: IllegalArgumentException): ResponseEntity<ValidationErrorResponse> =
        badRequest(exception.message ?: "Invalid request")

    /** `@Valid @RequestBody` failures — Bean Validation on a request body DTO. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleBodyValidation(exception: MethodArgumentNotValidException): ResponseEntity<ValidationErrorResponse> =
        badRequest(
            exception.bindingResult.fieldErrors
                .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
                .ifBlank { "Invalid request body" },
        )

    /** `@RequestParam`/`@PathVariable` constraint failures. */
    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleParameterValidation(exception: HandlerMethodValidationException): ResponseEntity<ValidationErrorResponse> =
        badRequest(
            exception.allErrors
                .joinToString("; ") { it.defaultMessage ?: "Invalid value" }
                .ifBlank { "Invalid request parameters" },
        )

    /** Older-style constraint violation path some Spring versions still raise. */
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(exception: ConstraintViolationException): ResponseEntity<ValidationErrorResponse> =
        badRequest(
            exception.constraintViolations
                .joinToString("; ") { "${it.propertyPath}: ${it.message}" }
                .ifBlank { "Invalid request" },
        )

    /** A path variable or query param could not be converted to its target type. */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(exception: MethodArgumentTypeMismatchException): ResponseEntity<ValidationErrorResponse> =
        badRequest("${exception.name}: has an invalid value")

    /** Malformed JSON, unknown properties, or wrong-shaped values. */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(exception: HttpMessageNotReadableException): ResponseEntity<ValidationErrorResponse> =
        badRequest("Request body is malformed or contains an unrecognized field")

    /**
     * DB-level unique constraint fired despite application pre-checks — the
     * backstop against a race condition. Returns 409 with a safe message,
     * never the raw constraint name or a 500.
     */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(exception: DataIntegrityViolationException): ResponseEntity<ValidationErrorResponse> =
        ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ValidationErrorResponse(message = "This conflicts with an existing record"))

    private fun badRequest(message: String): ResponseEntity<ValidationErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ValidationErrorResponse(message = message))
}

data class ValidationErrorResponse(
    val error: String = "validation_failed",
    val message: String,
)
