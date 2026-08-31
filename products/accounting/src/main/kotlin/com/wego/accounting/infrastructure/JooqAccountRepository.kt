package com.wego.accounting.infrastructure

import com.wego.accounting.application.AccountRepository
import com.wego.accounting.domain.Account
import com.wego.accounting.domain.AccountId
import com.wego.accounting.domain.AccountType
import com.wego.generated.jooq.tables.AccountingAccount.ACCOUNTING_ACCOUNT
import com.wego.generated.jooq.tables.records.AccountingAccountRecord
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqAccountRepository(
    private val dsl: DSLContext,
) : AccountRepository {
    @Transactional(readOnly = true)
    override fun findById(id: AccountId): Account? {
        val record = dsl.selectFrom(ACCOUNTING_ACCOUNT).where(ACCOUNTING_ACCOUNT.ID.eq(id.value)).fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional
    override fun findByIdForUpdate(id: AccountId): Account? {
        val record =
            dsl
                .selectFrom(ACCOUNTING_ACCOUNT)
                .where(ACCOUNTING_ACCOUNT.ID.eq(id.value))
                .forUpdate()
                .fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional(readOnly = true)
    override fun findByCode(code: String): Account? {
        val record = dsl.selectFrom(ACCOUNTING_ACCOUNT).where(ACCOUNTING_ACCOUNT.CODE.eq(code)).fetchOne() ?: return null
        return toDomain(record)
    }

    @Transactional(readOnly = true)
    override fun findAll(
        accountType: AccountType?,
        activeOnly: Boolean,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<Account> {
        var condition = DSL.noCondition()
        if (accountType != null) condition = condition.and(ACCOUNTING_ACCOUNT.ACCOUNT_TYPE.eq(accountType.name))
        if (activeOnly) condition = condition.and(ACCOUNTING_ACCOUNT.IS_ACTIVE.isTrue)
        if (!search.isNullOrBlank()) {
            val trimmed = search.trim()
            condition =
                condition.and(ACCOUNTING_ACCOUNT.NAME.containsIgnoreCase(trimmed).or(ACCOUNTING_ACCOUNT.CODE.containsIgnoreCase(trimmed)))
        }
        return dsl
            .selectFrom(ACCOUNTING_ACCOUNT)
            .where(condition)
            .orderBy(ACCOUNTING_ACCOUNT.CODE)
            .limit(limit)
            .offset(offset)
            .fetch()
            .map(::toDomain)
    }

    @Transactional
    override fun save(account: Account) {
        dsl
            .insertInto(ACCOUNTING_ACCOUNT)
            .set(ACCOUNTING_ACCOUNT.ID, account.id.value)
            .set(ACCOUNTING_ACCOUNT.CODE, account.code)
            .set(ACCOUNTING_ACCOUNT.NAME, account.name)
            .set(ACCOUNTING_ACCOUNT.ACCOUNT_TYPE, account.accountType.name)
            .set(ACCOUNTING_ACCOUNT.PARENT_ACCOUNT_ID, account.parentAccountId?.value)
            .set(ACCOUNTING_ACCOUNT.DESCRIPTION, account.description)
            .set(ACCOUNTING_ACCOUNT.IS_ACTIVE, account.isActive)
            .set(ACCOUNTING_ACCOUNT.CREATED_BY_USER_ID, account.createdByUserId)
            .set(ACCOUNTING_ACCOUNT.CREATED_AT, toOffset(account.createdAt))
            .set(ACCOUNTING_ACCOUNT.UPDATED_AT, toOffset(account.updatedAt))
            .onConflict(ACCOUNTING_ACCOUNT.ID)
            .doUpdate()
            .set(ACCOUNTING_ACCOUNT.NAME, account.name)
            .set(ACCOUNTING_ACCOUNT.DESCRIPTION, account.description)
            .set(ACCOUNTING_ACCOUNT.IS_ACTIVE, account.isActive)
            .set(ACCOUNTING_ACCOUNT.UPDATED_AT, toOffset(account.updatedAt))
            .execute()
    }

    private fun toDomain(record: AccountingAccountRecord): Account =
        Account(
            id = AccountId(record.id),
            code = record.code,
            name = record.name,
            accountType = AccountType.valueOf(record.accountType),
            parentAccountId = record.parentAccountId?.let(::AccountId),
            description = record.description,
            isActive = record.isActive,
            createdByUserId = record.createdByUserId,
            createdAt = record.createdAt.toInstant(),
            updatedAt = record.updatedAt.toInstant(),
        )

    private fun toOffset(instant: Instant): OffsetDateTime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC)
}
