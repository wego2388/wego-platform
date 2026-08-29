package com.wego.identity.infrastructure

import com.wego.identity.application.AdminBootstrapService
import com.wego.identity.application.IdentityAuditRecorder
import com.wego.identity.application.LoginAttemptThrottle
import com.wego.identity.application.LoginService
import com.wego.identity.application.LogoutService
import com.wego.identity.application.PasswordHasher
import com.wego.identity.application.PermissionResolver
import com.wego.identity.application.SessionAuthenticationService
import com.wego.identity.application.SessionRepository
import com.wego.identity.application.SessionTokenGenerator
import com.wego.identity.application.TransactionRunner
import com.wego.identity.application.UserRepository
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class IdentityBeanConfiguration {
    @Bean
    fun systemClock(): Clock = Clock.systemUTC()

    /**
     * [BearerTokenAuthenticationFilter] is wired into the Spring Security
     * chain explicitly via `HttpSecurity.addFilterBefore` in
     * `SecurityConfiguration`. Without disabling Boot's automatic servlet
     * registration, a `Filter` bean also runs a second time as a raw
     * container filter outside Spring Security's chain.
     */
    @Bean
    fun bearerTokenAuthenticationFilterRegistration(
        filter: BearerTokenAuthenticationFilter,
    ): FilterRegistrationBean<BearerTokenAuthenticationFilter> = FilterRegistrationBean(filter).apply { isEnabled = false }

    /** Same reasoning as [bearerTokenAuthenticationFilterRegistration] — [CorrelationIdFilter] is wired explicitly, not auto-registered. */
    @Bean
    fun correlationIdFilterRegistration(filter: CorrelationIdFilter): FilterRegistrationBean<CorrelationIdFilter> =
        FilterRegistrationBean(filter).apply { isEnabled = false }

    @Bean
    fun loginService(
        userRepository: UserRepository,
        sessionRepository: SessionRepository,
        passwordHasher: PasswordHasher,
        sessionTokenGenerator: SessionTokenGenerator,
        auditRecorder: IdentityAuditRecorder,
        transactionRunner: TransactionRunner,
        loginAttemptThrottle: LoginAttemptThrottle,
        clock: Clock,
    ): LoginService =
        LoginService(
            userRepository,
            sessionRepository,
            passwordHasher,
            sessionTokenGenerator,
            auditRecorder,
            transactionRunner,
            loginAttemptThrottle,
            clock,
        )

    @Bean
    fun logoutService(
        sessionRepository: SessionRepository,
        auditRecorder: IdentityAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): LogoutService = LogoutService(sessionRepository, auditRecorder, transactionRunner, clock)

    @Bean
    fun sessionAuthenticationService(
        sessionRepository: SessionRepository,
        userRepository: UserRepository,
        permissionResolver: PermissionResolver,
        sessionTokenGenerator: SessionTokenGenerator,
        clock: Clock,
    ): SessionAuthenticationService =
        SessionAuthenticationService(sessionRepository, userRepository, permissionResolver, sessionTokenGenerator, clock)

    @Bean
    fun adminBootstrapService(
        userRepository: UserRepository,
        passwordHasher: PasswordHasher,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): AdminBootstrapService = AdminBootstrapService(userRepository, passwordHasher, transactionRunner, clock)
}
