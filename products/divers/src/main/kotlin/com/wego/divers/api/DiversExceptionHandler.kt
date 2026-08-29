package com.wego.divers.api

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
 * A single, unified `400 {"error":"validation_failed","message":"..."}`
 * shape for every way a divers request can fail validation — never a raw
 * 500, never Spring's default plain-text/whitespace error body, and never
 * a stack trace or SQL constraint name. Scoped to this package only, not a
 * platform-wide change to how `com.wego.identity` handles its own
 * validation (see `LoginService`'s own, differently-shaped, deliberate
 * handling of `EmailAddress.of(...)`).
 */
@RestControllerAdvice(basePackages = ["com.wego.divers.api"])
class DiversExceptionHandler {
    /**
     * Domain constructors (`Offering.create`, `Booking.confirm`, `Money`,
     * `CustomerContact`, ...) validate their own invariants via Kotlin's
     * `require(...)`, throwing this with an already human-readable,
     * safe-to-return message (no internals, no stack trace — see each
     * `require` call site).
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

    /** `@RequestParam`/`@RequestHeader`/`@PathVariable` constraint failures (requires `@Validated` on the controller). */
    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleParameterValidation(exception: HandlerMethodValidationException): ResponseEntity<ValidationErrorResponse> =
        badRequest(
            exception.allErrors
                .joinToString("; ") { it.defaultMessage ?: "Invalid value" }
                .ifBlank { "Invalid request parameters" },
        )

    /** Older-style method-parameter validation path some Spring versions still raise instead of [HandlerMethodValidationException]. */
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(exception: ConstraintViolationException): ResponseEntity<ValidationErrorResponse> =
        badRequest(
            exception.constraintViolations
                .joinToString("; ") { "${it.propertyPath}: ${it.message}" }
                .ifBlank { "Invalid request" },
        )

    /** A query/path value could not be converted to its declared UUID/enum/number type. */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(exception: MethodArgumentTypeMismatchException): ResponseEntity<ValidationErrorResponse> =
        badRequest("${exception.name}: has an invalid value")

    /** A required framework-bound value such as `Idempotency-Key` is missing. */
    @ExceptionHandler(ServletRequestBindingException::class)
    fun handleRequestBinding(exception: ServletRequestBindingException): ResponseEntity<ValidationErrorResponse> =
        badRequest(exception.message ?: "A required request value is missing")

    /**
     * Malformed JSON, an unknown property (rejected app-wide by
     * `com.wego.JacksonConfiguration`'s `FAIL_ON_UNKNOWN_PROPERTIES`
     * customizer), or a JSON value of the wrong shape for its target field.
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(exception: HttpMessageNotReadableException): ResponseEntity<ValidationErrorResponse> =
        badRequest("Request body is malformed or contains an unrecognized field")

    /**
     * A real DB-level unique constraint fired despite an application-layer
     * pre-check having passed — the true backstop against a race between
     * two concurrent requests (e.g. the same equipment QR code, or two
     * rentals starting on the same item at once), not the everyday path.
     * A clean 409, never the raw constraint name or a 500.
     */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(exception: DataIntegrityViolationException): ResponseEntity<ValidationErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ValidationErrorResponse(message = "This conflicts with an existing record"))

    private fun badRequest(message: String): ResponseEntity<ValidationErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ValidationErrorResponse(message = message))
}
