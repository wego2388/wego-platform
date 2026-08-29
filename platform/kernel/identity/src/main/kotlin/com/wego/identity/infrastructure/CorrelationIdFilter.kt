package com.wego.identity.infrastructure

import com.wego.events.CorrelationContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Runs before [BearerTokenAuthenticationFilter] so a correlation id is
 * available for the entire request lifecycle, including an unauthenticated
 * 401/403 response. Placed here (identity's own filter-chain wiring),
 * not in `com.wego.events` where [CorrelationContext] itself lives — this
 * module already centralizes the whole HTTP filter chain's construction
 * (`SecurityConfiguration`, `BearerAuthenticationEntryPoint`,
 * `AuditingAccessDeniedHandler` all live here too), and a Servlet `Filter`
 * is Spring/HTTP-coupled infrastructure that does not belong at a
 * cross-module-public root.
 */
@Component
class CorrelationIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        // An invalid or missing incoming value never fails the request and
        // never leaves correlation unset — it silently falls back to a
        // freshly generated id, so a malformed client header can degrade
        // traceability but can never cause a 500.
        val incoming = request.getHeader(CORRELATION_HEADER)
        val correlationId = incoming?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: UUID.randomUUID()

        CorrelationContext.set(correlationId)
        response.setHeader(CORRELATION_HEADER, correlationId.toString())
        try {
            filterChain.doFilter(request, response)
        } finally {
            CorrelationContext.clear()
        }
    }

    companion object {
        const val CORRELATION_HEADER = "X-Correlation-Id"
    }
}
