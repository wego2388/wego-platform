package com.wego.identity.application

import java.time.Clock
import java.time.Instant

class SessionAuthenticationService(
    private val sessionRepository: SessionRepository,
    private val userRepository: UserRepository,
    private val permissionResolver: PermissionResolver,
    private val sessionTokenGenerator: SessionTokenGenerator,
    private val clock: Clock,
) {
    fun authenticate(rawToken: String): AuthenticatedPrincipal? {
        val now = Instant.now(clock)
        val tokenHash = sessionTokenGenerator.hash(rawToken)
        val session = sessionRepository.findActiveByTokenHash(tokenHash, now) ?: return null
        if (!session.isActive(now)) return null

        val user = userRepository.findById(session.userId) ?: return null
        if (!user.canAuthenticate(now)) return null

        return AuthenticatedPrincipal(user, session, permissionResolver.resolve(user.roles))
    }
}
