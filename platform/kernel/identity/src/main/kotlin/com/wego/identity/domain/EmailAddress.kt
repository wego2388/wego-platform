package com.wego.identity.domain

@JvmInline
value class EmailAddress private constructor(
    val value: String,
) {
    companion object {
        private val FORMAT = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

        // Matches identity_user.email's varchar(320) column (RFC 5321's own
        // limit). Enforced here, not just at the HTTP boundary, so every
        // EmailAddress in the system is guaranteed to fit whatever storage
        // or audit column it's written to — a syntactically valid but
        // absurdly long value must fail validation, not reach a DB write
        // sized for a real address.
        private const val MAX_LENGTH = 320

        fun of(raw: String): EmailAddress {
            val normalized = raw.trim().lowercase()
            require(normalized.isNotBlank()) { "Email address must not be blank" }
            require(normalized.length <= MAX_LENGTH) { "Email address must be at most $MAX_LENGTH characters" }
            require(FORMAT.matches(normalized)) { "Email address must be a valid address" }
            return EmailAddress(normalized)
        }
    }
}
