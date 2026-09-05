package com.wego.identity.application

import com.wego.identity.domain.Permission
import com.wego.identity.domain.Role
import com.wego.identity.domain.User

class IdentityAdminQueryService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val permissionCatalogRepository: PermissionCatalogRepository,
) {
    fun listUsers(): List<User> = userRepository.findAll()

    fun listRoles(): List<Role> = roleRepository.findAll()

    fun listPermissions(): List<Permission> = permissionCatalogRepository.findAll()
}
