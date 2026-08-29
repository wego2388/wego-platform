package com.wego.identity.api

import com.wego.events.CorrelationContext
import com.wego.identity.application.AuthenticatedPrincipal
import com.wego.identity.application.LoginService
import com.wego.identity.application.LogoutService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/identity")
class IdentityController(
    private val loginService: LoginService,
    private val logoutService: LogoutService,
) {
    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest,
    ): ResponseEntity<Any> {
        val result = loginService.login(request.email, request.password, CorrelationContext.currentCorrelationId())

        if (!result.success) {
            val reason = result.failureReason ?: LoginService.FAILURE_REASON
            if (reason == LoginService.RATE_LIMITED_REASON) {
                // Same JSON contract as nginx's edge-level 429 (see
                // infrastructure/nginx/nginx.conf) — a client shouldn't need
                // to handle two different rate-limit response shapes
                // depending on which layer caught it. retryAfterSeconds
                // comes from the throttle decision itself, not a fixed
                // constant — it grows with each recorded failure.
                return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", (result.retryAfterSeconds ?: 1L).toString())
                    .body(LoginErrorResponse(reason))
            }
            return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(LoginErrorResponse(reason))
        }

        return ResponseEntity.ok(LoginResponse(requireNotNull(result.rawToken), requireNotNull(result.session).expiresAt))
    }

    @PostMapping("/logout")
    fun logout(authentication: Authentication): ResponseEntity<Void> {
        val principal = authentication.principal as AuthenticatedPrincipal
        logoutService.logout(principal.session, principal.user.id, CorrelationContext.currentCorrelationId())
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/me")
    fun me(authentication: Authentication): MeResponse {
        val principal = authentication.principal as AuthenticatedPrincipal
        return MeResponse(
            userId = principal.user.id.value,
            email = principal.user.email.value,
            roles =
                principal.user.roles
                    .map { it.value }
                    .sorted(),
            permissions = principal.permissions.map { it.value }.sorted(),
        )
    }
}
