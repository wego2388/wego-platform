package com.wego.identity.api

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Malformed JSON or an unknown property (rejected app-wide by
 * `com.wego.JacksonConfiguration`) would otherwise fall through to Spring's
 * default `/error` handling, which the deny-by-default security filter
 * chain rejects as 401 for an unauthenticated caller — a login request with
 * one typo'd field would come back looking like a credentials problem
 * instead of the malformed-request problem it actually is. Returns the same
 * `LoginErrorResponse` shape every other `/login` failure already uses,
 * with a distinct `validation_failed` code so a client can tell the two
 * apart, unlike the credentials/lockout cases this endpoint deliberately
 * keeps indistinguishable from each other.
 */
@RestControllerAdvice(basePackages = ["com.wego.identity.api"])
class IdentityExceptionHandler {
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(exception: HttpMessageNotReadableException): ResponseEntity<LoginErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(LoginErrorResponse("validation_failed"))
}
