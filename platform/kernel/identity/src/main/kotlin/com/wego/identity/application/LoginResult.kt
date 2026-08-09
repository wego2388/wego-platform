package com.wego.identity.application

import com.wego.identity.domain.Session
import com.wego.identity.domain.User

class LoginResult private constructor(
    val success: Boolean,
    val rawToken: String?,
    val session: Session?,
    val user: User?,
    val failureReason: String?,
    val retryAfterSeconds: Long?,
) {
    companion object {
        fun success(
            rawToken: String,
            session: Session,
            user: User,
        ): LoginResult =
            LoginResult(
                success = true,
                rawToken = rawToken,
                session = session,
                user = user,
                failureReason = null,
                retryAfterSeconds = null,
            )

        fun failure(
            reason: String,
            retryAfterSeconds: Long? = null,
        ): LoginResult =
            LoginResult(
                success = false,
                rawToken = null,
                session = null,
                user = null,
                failureReason = reason,
                retryAfterSeconds = retryAfterSeconds,
            )
    }
}
