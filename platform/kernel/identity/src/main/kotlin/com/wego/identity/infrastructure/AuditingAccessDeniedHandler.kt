package com.wego.identity.infrastructure

import com.wego.identity.application.AuthenticatedPrincipal
import com.wego.identity.application.IdentityAuditRecorder
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authorization.AuthorityAuthorizationDecision
import org.springframework.security.authorization.AuthorizationDeniedException
import org.springframework.security.authorization.ExpressionAuthorizationDecision
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/**
 * Reached only for an *authenticated* principal denied by `hasAuthority`
 * (Spring Security's `ExceptionTranslationFilter` routes a denial from an
 * anonymous/unauthenticated caller to [BearerAuthenticationEntryPoint]
 * instead — see its own doc comment). Records which route was denied to
 * whom before returning 403.
 */
@Component
class AuditingAccessDeniedHandler(
    private val auditRecorder: IdentityAuditRecorder,
    private val clock: Clock,
) : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedPrincipal
        if (principal != null) {
            auditRecorder.recordPermissionDenied(
                principal.user.id,
                requiredPermissionOrFallback(accessDeniedException, request),
                Instant.now(clock),
                null,
            )
        }
        response.status = HttpServletResponse.SC_FORBIDDEN
    }

    /**
     * `@PreAuthorize("hasAuthority('identity:administer')")` denials carry
     * the actual SpEL expression evaluated, and a `.hasAuthority(...)` URL
     * matcher denial carries the actual required authority — recording
     * "identity:administer" is what an operator reviewing the audit log
     * actually wants, not just which URL was hit. `.denyAll()` (the
     * catch-all default) has no specific permission to name, so it falls
     * back to method+path, same as before.
     */
    private fun requiredPermissionOrFallback(
        exception: AccessDeniedException,
        request: HttpServletRequest,
    ): String {
        val decision = (exception as? AuthorizationDeniedException)?.authorizationResult
        val detail =
            when (decision) {
                is ExpressionAuthorizationDecision -> decision.expression.expressionString
                is AuthorityAuthorizationDecision -> decision.authorities.joinToString(",") { it.authority.orEmpty() }
                else -> null
            }
        return detail ?: "${request.method} ${request.requestURI}"
    }
}
