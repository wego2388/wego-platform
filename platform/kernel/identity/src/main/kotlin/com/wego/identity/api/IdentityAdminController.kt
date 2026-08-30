package com.wego.identity.api

import com.wego.events.CorrelationContext
import com.wego.identity.AuthenticatedUser
import com.wego.identity.application.AssignUserRolesResult
import com.wego.identity.application.AssignUserRolesService
import com.wego.identity.application.CreateRoleResult
import com.wego.identity.application.CreateRoleService
import com.wego.identity.application.CreateUserResult
import com.wego.identity.application.CreateUserService
import com.wego.identity.application.DisableUserResult
import com.wego.identity.application.DisableUserService
import com.wego.identity.application.EnableUserResult
import com.wego.identity.application.EnableUserService
import com.wego.identity.application.IdentityAdminQueryService
import com.wego.identity.application.ResetUserPasswordResult
import com.wego.identity.application.ResetUserPasswordService
import com.wego.identity.application.UpdateRolePermissionsResult
import com.wego.identity.application.UpdateRolePermissionsService
import com.wego.identity.domain.UserId
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/identity")
class IdentityAdminController(
    private val queryService: IdentityAdminQueryService,
    private val createUserService: CreateUserService,
    private val disableUserService: DisableUserService,
    private val enableUserService: EnableUserService,
    private val resetUserPasswordService: ResetUserPasswordService,
    private val assignUserRolesService: AssignUserRolesService,
    private val createRoleService: CreateRoleService,
    private val updateRolePermissionsService: UpdateRolePermissionsService,
) {
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('identity:user-view')")
    fun listUsers(): List<UserResponse> = queryService.listUsers().map { it.toResponse() }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('identity:user-manage')")
    fun createUser(
        authentication: Authentication,
        @RequestBody request: CreateUserRequest,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                createUserService.create(
                    UserId(actorUserId),
                    request.email,
                    request.password,
                    request.roleCodes.toSet(),
                    CorrelationContext.currentCorrelationId(),
                )
        ) {
            is CreateUserResult.Created -> ResponseEntity.status(HttpStatus.CREATED).body(result.user.toResponse())
            CreateUserResult.EmailAlreadyInUse ->
                ResponseEntity
                    .status(
                        HttpStatus.CONFLICT,
                    ).body(IdentityAdminErrorResponse("email_already_in_use"))
            is CreateUserResult.UnknownRole ->
                ResponseEntity.badRequest().body(
                    IdentityAdminErrorResponse("unknown_role:${result.roleCode}"),
                )
        }
    }

    @PostMapping("/users/{id}/disable")
    @PreAuthorize("hasAuthority('identity:user-manage')")
    fun disableUser(
        authentication: Authentication,
        @PathVariable id: UUID,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (val result = disableUserService.disable(UserId(actorUserId), UserId(id), CorrelationContext.currentCorrelationId())) {
            is DisableUserResult.Disabled -> ResponseEntity.ok(result.user.toResponse())
            DisableUserResult.NotFound -> ResponseEntity.notFound().build()
            DisableUserResult.AlreadyDisabled ->
                ResponseEntity
                    .status(
                        HttpStatus.CONFLICT,
                    ).body(IdentityAdminErrorResponse("already_disabled"))
            DisableUserResult.CannotDisableSelf ->
                ResponseEntity
                    .status(
                        HttpStatus.CONFLICT,
                    ).body(IdentityAdminErrorResponse("cannot_disable_self"))
        }
    }

    @PostMapping("/users/{id}/enable")
    @PreAuthorize("hasAuthority('identity:user-manage')")
    fun enableUser(
        authentication: Authentication,
        @PathVariable id: UUID,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (val result = enableUserService.enable(UserId(actorUserId), UserId(id), CorrelationContext.currentCorrelationId())) {
            is EnableUserResult.Enabled -> ResponseEntity.ok(result.user.toResponse())
            EnableUserResult.NotFound -> ResponseEntity.notFound().build()
            EnableUserResult.AlreadyActive -> ResponseEntity.status(HttpStatus.CONFLICT).body(IdentityAdminErrorResponse("already_active"))
        }
    }

    @PostMapping("/users/{id}/reset-password")
    @PreAuthorize("hasAuthority('identity:user-manage')")
    fun resetUserPassword(
        authentication: Authentication,
        @PathVariable id: UUID,
        @RequestBody request: ResetUserPasswordRequest,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                resetUserPasswordService.reset(
                    UserId(actorUserId),
                    UserId(id),
                    request.newPassword,
                    CorrelationContext.currentCorrelationId(),
                )
        ) {
            is ResetUserPasswordResult.Reset -> ResponseEntity.ok(result.user.toResponse())
            ResetUserPasswordResult.NotFound -> ResponseEntity.notFound().build()
        }
    }

    @PutMapping("/users/{id}/roles")
    @PreAuthorize("hasAuthority('identity:user-manage')")
    fun assignUserRoles(
        authentication: Authentication,
        @PathVariable id: UUID,
        @RequestBody request: AssignUserRolesRequest,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                assignUserRolesService.assign(
                    UserId(actorUserId),
                    UserId(id),
                    request.roleCodes.toSet(),
                    CorrelationContext.currentCorrelationId(),
                )
        ) {
            is AssignUserRolesResult.Assigned -> ResponseEntity.ok(result.user.toResponse())
            AssignUserRolesResult.NotFound -> ResponseEntity.notFound().build()
            is AssignUserRolesResult.UnknownRole ->
                ResponseEntity.badRequest().body(
                    IdentityAdminErrorResponse("unknown_role:${result.roleCode}"),
                )
            AssignUserRolesResult.CannotChangeOwnRoles ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(IdentityAdminErrorResponse("cannot_change_own_roles"))
        }
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('identity:role-view')")
    fun listRoles(): List<RoleResponse> = queryService.listRoles().map { it.toResponse() }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('identity:role-view')")
    fun listPermissions(): List<PermissionResponse> = queryService.listPermissions().map { it.toResponse() }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('identity:role-manage')")
    fun createRole(
        authentication: Authentication,
        @RequestBody request: CreateRoleRequest,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                createRoleService.create(
                    UserId(actorUserId),
                    request.code,
                    request.description,
                    request.permissionCodes.toSet(),
                    CorrelationContext.currentCorrelationId(),
                )
        ) {
            is CreateRoleResult.Created -> ResponseEntity.status(HttpStatus.CREATED).body(result.role.toResponse())
            CreateRoleResult.AlreadyExists ->
                ResponseEntity
                    .status(
                        HttpStatus.CONFLICT,
                    ).body(IdentityAdminErrorResponse("role_already_exists"))
            is CreateRoleResult.UnknownPermission ->
                ResponseEntity.badRequest().body(IdentityAdminErrorResponse("unknown_permission:${result.permissionCode}"))
        }
    }

    @PutMapping("/roles/{code}/permissions")
    @PreAuthorize("hasAuthority('identity:role-manage')")
    fun updateRolePermissions(
        authentication: Authentication,
        @PathVariable code: String,
        @RequestBody request: UpdateRolePermissionsRequest,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                updateRolePermissionsService.update(
                    UserId(actorUserId),
                    code,
                    request.permissionCodes.toSet(),
                    CorrelationContext.currentCorrelationId(),
                )
        ) {
            is UpdateRolePermissionsResult.Updated -> ResponseEntity.ok(result.role.toResponse())
            UpdateRolePermissionsResult.NotFound -> ResponseEntity.notFound().build()
            is UpdateRolePermissionsResult.UnknownPermission ->
                ResponseEntity.badRequest().body(IdentityAdminErrorResponse("unknown_permission:${result.permissionCode}"))
        }
    }
}
