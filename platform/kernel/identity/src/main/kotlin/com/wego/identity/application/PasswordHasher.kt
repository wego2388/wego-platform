package com.wego.identity.application

import com.wego.identity.domain.HashedPassword

interface PasswordHasher {
    fun hash(rawPassword: String): HashedPassword

    fun matches(
        rawPassword: String,
        hashed: HashedPassword,
    ): Boolean
}
