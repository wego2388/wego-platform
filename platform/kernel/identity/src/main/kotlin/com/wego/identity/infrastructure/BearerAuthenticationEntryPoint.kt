package com.wego.identity.infrastructure

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

/**
 * Missing, invalid, or expired credentials are a 401 (not authenticated),
 * distinct from an authenticated caller lacking a specific permission (403,
 * see [AuditingAccessDeniedHandler]). Without registering this, Spring
 * Security's default `Http403ForbiddenEntryPoint` returns 403 for both
 * cases, which is the wrong HTTP semantics for the former and gives a caller
 * no `WWW-Authenticate` challenge to act on.
 */
@Component
class BearerAuthenticationEntryPoint : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        response.setHeader("WWW-Authenticate", "Bearer")
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
    }
}
