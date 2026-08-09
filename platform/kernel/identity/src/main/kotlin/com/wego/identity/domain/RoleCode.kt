package com.wego.identity.domain

@JvmInline
value class RoleCode private constructor(
    val value: String,
) {
    companion object {
        private val FORMAT = Regex("^[a-z][a-z0-9-]*$")

        fun of(value: String): RoleCode {
            require(FORMAT.matches(value)) {
                "Role code must use lowercase-with-hyphens format"
            }
            return RoleCode(value)
        }
    }
}
