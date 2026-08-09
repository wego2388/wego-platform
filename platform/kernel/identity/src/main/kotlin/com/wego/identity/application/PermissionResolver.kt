package com.wego.identity.application

import com.wego.identity.domain.RoleCode
import com.wego.security.PermissionCode

interface PermissionResolver {
    fun resolve(roles: Set<RoleCode>): Set<PermissionCode>
}
