package com.wego.accounting.application

import com.wego.accounting.domain.Account
import com.wego.accounting.domain.AccountId
import com.wego.accounting.domain.AccountType

class AccountQueryService(
    private val accountRepository: AccountRepository,
) {
    fun findById(id: AccountId): Account? = accountRepository.findById(id)

    fun list(
        accountType: AccountType?,
        activeOnly: Boolean,
        search: String?,
        page: Int = 0,
        size: Int = Pagination.DEFAULT_PAGE_SIZE,
    ): List<Account> =
        accountRepository.findAll(
            accountType,
            activeOnly,
            search,
            limit = Pagination.boundedSize(size),
            offset = Pagination.offsetFor(page, size),
        )
}
