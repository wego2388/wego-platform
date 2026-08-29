package com.wego.identity.application

import com.wego.identity.AuthenticatedUser
import com.wego.identity.domain.User
import com.wego.security.PermissionCode
import java.util.UUID

class AuthenticatedPrincipal(
    val user: User,
    val session: com.wego.identity.domain.Session,
    val permissions: Set<PermissionCode>,
) : AuthenticatedUser {
    override val userId: UUID get() = user.id.value
}
