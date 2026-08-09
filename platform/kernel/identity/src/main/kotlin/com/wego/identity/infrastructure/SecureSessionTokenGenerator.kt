package com.wego.identity.infrastructure

import com.wego.identity.application.SessionTokenGenerator
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

@Component
class SecureSessionTokenGenerator : SessionTokenGenerator {
    private val random = SecureRandom()

    override fun generate(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    override fun hash(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest
            .digest(rawToken.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TOKEN_BYTES = 32
    }
}
