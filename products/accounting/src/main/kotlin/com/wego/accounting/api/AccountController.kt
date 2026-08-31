package com.wego.accounting.api

import com.wego.accounting.application.AccountQueryService
import com.wego.accounting.application.CreateAccountResult
import com.wego.accounting.application.CreateAccountService
import com.wego.accounting.application.DeactivateAccountResult
import com.wego.accounting.application.DeactivateAccountService
import com.wego.accounting.application.ReactivateAccountResult
import com.wego.accounting.application.ReactivateAccountService
import com.wego.accounting.application.UpdateAccountResult
import com.wego.accounting.application.UpdateAccountService
import com.wego.accounting.domain.AccountId
import com.wego.accounting.domain.AccountType
import com.wego.identity.AuthenticatedUser
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Validated
@RestController
@RequestMapping("/api/v1/accounting/accounts")
class AccountController(
    private val createAccountService: CreateAccountService,
    private val updateAccountService: UpdateAccountService,
    private val deactivateAccountService: DeactivateAccountService,
    private val reactivateAccountService: ReactivateAccountService,
    private val accountQueryService: AccountQueryService,
) {
    @PostMapping
    @PreAuthorize("hasAuthority('accounting:coa-manage')")
    fun create(
        @Valid @RequestBody request: CreateAccountRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                createAccountService.create(
                    code = request.code,
                    name = request.name,
                    accountType = request.accountType,
                    parentAccountId = request.parentAccountId?.let(::AccountId),
                    description = request.description,
                    createdByUserId = actorUserId,
                )
        ) {
            is CreateAccountResult.Created -> ResponseEntity.status(HttpStatus.CREATED).body(result.account.toResponse())
            CreateAccountResult.CodeAlreadyInUse ->
                ResponseEntity
                    .status(
                        HttpStatus.CONFLICT,
                    ).body(AccountErrorResponse("code_already_in_use"))
            CreateAccountResult.ParentAccountNotFound -> ResponseEntity.badRequest().body(AccountErrorResponse("parent_account_not_found"))
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('accounting:coa-view')")
    fun list(
        @RequestParam(required = false) accountType: AccountType?,
        @RequestParam(defaultValue = "true") activeOnly: Boolean,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) size: Int,
    ): List<AccountResponse> = accountQueryService.list(accountType, activeOnly, search, page, size).map { it.toResponse() }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('accounting:coa-view')")
    fun get(
        @PathVariable id: UUID,
    ): ResponseEntity<AccountResponse> {
        val account = accountQueryService.findById(AccountId(id)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(account.toResponse())
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('accounting:coa-manage')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateAccountRequest,
    ): ResponseEntity<Any> =
        when (val result = updateAccountService.update(AccountId(id), request.name, request.description)) {
            is UpdateAccountResult.Updated -> ResponseEntity.ok(result.account.toResponse())
            UpdateAccountResult.NotFound -> ResponseEntity.notFound().build()
        }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('accounting:coa-manage')")
    fun deactivate(
        @PathVariable id: UUID,
    ): ResponseEntity<Any> =
        when (val result = deactivateAccountService.deactivate(AccountId(id))) {
            is DeactivateAccountResult.Deactivated -> ResponseEntity.ok(result.account.toResponse())
            DeactivateAccountResult.NotFound -> ResponseEntity.notFound().build()
            DeactivateAccountResult.AlreadyInactive ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(AccountErrorResponse("already_inactive"))
        }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('accounting:coa-manage')")
    fun reactivate(
        @PathVariable id: UUID,
    ): ResponseEntity<Any> =
        when (val result = reactivateAccountService.reactivate(AccountId(id))) {
            is ReactivateAccountResult.Reactivated -> ResponseEntity.ok(result.account.toResponse())
            ReactivateAccountResult.NotFound -> ResponseEntity.notFound().build()
            ReactivateAccountResult.AlreadyActive ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(AccountErrorResponse("already_active"))
        }
}
