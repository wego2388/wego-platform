package com.wego.identity.infrastructure

import com.wego.identity.AuthenticatedApiPrefix
import com.wego.identity.PublicApiPrefix
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

/**
 * Web-only by construction: a filter chain has no meaning without a servlet
 * context. This matters concretely for the `bootstrap-admin` profile, which
 * runs with `spring.main.web-application-type: none` and would otherwise
 * fail to start — Spring Security's `HttpSecurity` bean is itself only
 * registered inside a web application context.
 */
@ConditionalOnWebApplication
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfiguration {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        correlationIdFilter: CorrelationIdFilter,
        bearerTokenAuthenticationFilter: BearerTokenAuthenticationFilter,
        bearerAuthenticationEntryPoint: BearerAuthenticationEntryPoint,
        auditingAccessDeniedHandler: AuditingAccessDeniedHandler,
        authenticatedApiPrefixes: List<AuthenticatedApiPrefix>,
        publicApiPrefixes: List<PublicApiPrefix>,
    ): SecurityFilterChain {
        http
            .authorizeHttpRequests { requests ->
                requests
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/api/v1/identity/login")
                    .permitAll()
                // Every public prefix is registered before any authenticated
                // one (including the general "/api/v1/identity/**" rule
                // below) — Spring Security's authorizeHttpRequests matches in
                // registration order, so a product's own public sub-path
                // (e.g. ".../public/**") must be added first to take
                // precedence over that same product's broader authenticated
                // prefix, not be shadowed by it.
                for (prefix in publicApiPrefixes) {
                    requests.requestMatchers(prefix.pattern).permitAll()
                }
                requests.requestMatchers("/api/v1/identity/**").authenticated()
                for (prefix in authenticatedApiPrefixes) {
                    requests.requestMatchers(prefix.pattern).authenticated()
                }
                requests.anyRequest().denyAll()
            }.sessionManagement { sessions ->
                sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            }.requestCache { cache -> cache.disable() }
            // CSRF protects cookie-carried sessions from being driven by a
            // third-party page; it has no purpose for a stateless Bearer
            // token that only ever travels in an explicit Authorization
            // header a browser never attaches automatically.
            .csrf { csrf -> csrf.disable() }
            .formLogin { login -> login.disable() }
            .httpBasic { basic -> basic.disable() }
            .logout { logout -> logout.disable() }
            .exceptionHandling { handling ->
                handling
                    .authenticationEntryPoint(bearerAuthenticationEntryPoint)
                    .accessDeniedHandler(auditingAccessDeniedHandler)
            }.addFilterBefore(bearerTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterBefore(correlationIdFilter, BearerTokenAuthenticationFilter::class.java)

        return http.build()
    }
}
