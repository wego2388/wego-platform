package com.wego.identity.api

import java.time.Instant
import java.util.UUID

data class LoginRequest(
    val email: String,
    val password: String,
)

data class LoginResponse(
    val token: String,
    val expiresAt: Instant,
)

data class LoginErrorResponse(
    val error: String,
)

data class MeResponse(
    val userId: UUID,
    val email: String,
    val roles: List<String>,
    val permissions: List<String>,
)
