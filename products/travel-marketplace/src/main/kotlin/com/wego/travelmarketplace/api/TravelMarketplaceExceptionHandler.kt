package com.wego.travelmarketplace.api

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
 * Same unified `400 {"error":"validation_failed","message":"..."}` shape as
 * `com.wego.divers.api.DiversExceptionHandler` — see that class for the full
 * rationale per exception type. Scoped to this package only.
 */
@RestControllerAdvice(basePackages = ["com.wego.travelmarketplace.api"])
class TravelMarketplaceExceptionHandler {
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

    /** A real DB-level constraint (e.g. a duplicate category code race) fired despite an application-layer pre-check having passed. */
    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(exception: DataIntegrityViolationException): ResponseEntity<ValidationErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ValidationErrorResponse(message = "This conflicts with an existing record"))

    private fun badRequest(message: String): ResponseEntity<ValidationErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ValidationErrorResponse(message = message))
}
