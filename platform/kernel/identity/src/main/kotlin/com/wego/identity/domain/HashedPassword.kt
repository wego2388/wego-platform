package com.wego.identity.domain

@JvmInline
value class HashedPassword private constructor(
    val value: String,
) {
    companion object {
        fun of(hash: String): HashedPassword {
            require(hash.isNotBlank()) { "Password hash must not be blank" }
            return HashedPassword(hash)
        }
    }
}
