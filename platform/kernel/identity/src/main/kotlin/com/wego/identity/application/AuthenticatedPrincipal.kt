package com.wego.identity.application

import com.wego.identity.domain.User
import com.wego.security.PermissionCode

class AuthenticatedPrincipal(
    val user: User,
    val session: com.wego.identity.domain.Session,
    val permissions: Set<PermissionCode>,
)
