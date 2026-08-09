package com.wego.identity.infrastructure

import com.wego.identity.application.PasswordHasher
import com.wego.identity.domain.HashedPassword
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.stereotype.Component

@Component
class SpringPasswordHasher : PasswordHasher {
    private val encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

    override fun hash(rawPassword: String): HashedPassword =
        HashedPassword.of(requireNotNull(encoder.encode(rawPassword)) { "Password encoder returned no hash" })

    override fun matches(
        rawPassword: String,
        hashed: HashedPassword,
    ): Boolean = encoder.matches(rawPassword, hashed.value)
}
