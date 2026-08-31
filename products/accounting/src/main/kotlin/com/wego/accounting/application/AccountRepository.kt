package com.wego.accounting.application

import com.wego.accounting.domain.Account
import com.wego.accounting.domain.AccountId
import com.wego.accounting.domain.AccountType

interface AccountRepository {
    fun findById(id: AccountId): Account?

    /** Row-locked read for a read-modify-write cycle — see EmployeeRepository.findByIdForUpdate (products/hr) for the established pattern this guards against: withUpdatedDetails carries isActive forward unchanged, so an unlocked read could race deactivate()/reactivate() and silently undo it. */
    fun findByIdForUpdate(id: AccountId): Account?

    fun findByCode(code: String): Account?

    fun findAll(
        accountType: AccountType?,
        activeOnly: Boolean,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<Account>

    fun save(account: Account)
}
