package com.wego.security

@JvmInline
value class PermissionCode private constructor(
    val value: String,
) {
    companion object {
        private val FORMAT = Regex("^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*$")

        fun of(value: String): PermissionCode {
            require(FORMAT.matches(value)) {
                "Permission code must use '<resource>:<action>' lowercase format"
            }
            return PermissionCode(value)
        }
    }
}
