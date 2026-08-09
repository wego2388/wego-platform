package com.wego.identity.infrastructure

import com.wego.identity.application.SessionAuthenticationService
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class BearerTokenAuthenticationFilter(
    private val sessionAuthenticationService: SessionAuthenticationService,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            val rawToken = header.removePrefix(BEARER_PREFIX).trim()
            if (rawToken.isNotEmpty()) {
                val principal = sessionAuthenticationService.authenticate(rawToken)
                if (principal != null) {
                    SecurityContextHolder.getContext().authentication = IdentitySessionAuthenticationToken(principal)
                }
            }
        }
        filterChain.doFilter(request, response)
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}
