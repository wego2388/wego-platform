package com.wego.accounting

import com.wego.accounting.domain.Account
import com.wego.accounting.domain.AccountId
import com.wego.accounting.domain.AccountType
import com.wego.accounting.domain.JournalLineDirection
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.time.Instant

class AccountTest {
    private val now = Instant.parse("2026-08-31T00:00:00Z")

    private fun create(accountType: AccountType = AccountType.ASSET): Account =
        Account.create(
            id = AccountId.generate(),
            code = "1000",
            name = "Cash on Hand",
            accountType = accountType,
            parentAccountId = null,
            description = null,
            createdByUserId = null,
            now = now,
        )

    @Test
    fun `rejects a blank code`() {
        assertThatIllegalArgumentException().isThrownBy {
            Account.create(
                id = AccountId.generate(),
                code = "   ",
                name = "Cash",
                accountType = AccountType.ASSET,
                parentAccountId = null,
                description = null,
                createdByUserId = null,
                now = now,
            )
        }
    }

    @Test
    fun `rejects a blank name`() {
        assertThatIllegalArgumentException().isThrownBy {
            Account.create(
                id = AccountId.generate(),
                code = "1000",
                name = "   ",
                accountType = AccountType.ASSET,
                parentAccountId = null,
                description = null,
                createdByUserId = null,
                now = now,
            )
        }
    }

    @Test
    fun `starts active`() {
        val account = create()
        assertThat(account.isActive).isTrue()
    }

    @Test
    fun `deactivate then reactivate round-trips isActive`() {
        val account = create()
        val deactivatedAt = Instant.parse("2026-09-01T00:00:00Z")

        account.deactivate(deactivatedAt)
        assertThat(account.isActive).isFalse()
        assertThat(account.updatedAt).isEqualTo(deactivatedAt)

        val reactivatedAt = Instant.parse("2026-09-02T00:00:00Z")
        account.reactivate(reactivatedAt)
        assertThat(account.isActive).isTrue()
        assertThat(account.updatedAt).isEqualTo(reactivatedAt)
    }

    @Test
    fun `an already-inactive account cannot be deactivated again`() {
        val account = create()
        account.deactivate(Instant.parse("2026-09-01T00:00:00Z"))

        assertThatIllegalArgumentException().isThrownBy { account.deactivate(Instant.parse("2026-09-02T00:00:00Z")) }
    }

    @Test
    fun `an already-active account cannot be reactivated`() {
        val account = create()
        assertThatIllegalArgumentException().isThrownBy { account.reactivate(Instant.parse("2026-09-02T00:00:00Z")) }
    }

    @Test
    fun `updating details preserves identity, code, and account type`() {
        val account = create()
        val updated = account.withUpdatedDetails("Petty Cash", "Small on-hand float", Instant.parse("2026-09-01T00:00:00Z"))

        assertThat(updated.id).isEqualTo(account.id)
        assertThat(updated.code).isEqualTo("1000")
        assertThat(updated.accountType).isEqualTo(AccountType.ASSET)
        assertThat(updated.name).isEqualTo("Petty Cash")
        assertThat(updated.description).isEqualTo("Small on-hand float")
    }

    @Test
    fun `normal balance is derived correctly for every account type`() {
        assertThat(AccountType.ASSET.normalBalance).isEqualTo(JournalLineDirection.DEBIT)
        assertThat(AccountType.EXPENSE.normalBalance).isEqualTo(JournalLineDirection.DEBIT)
        assertThat(AccountType.LIABILITY.normalBalance).isEqualTo(JournalLineDirection.CREDIT)
        assertThat(AccountType.EQUITY.normalBalance).isEqualTo(JournalLineDirection.CREDIT)
        assertThat(AccountType.REVENUE.normalBalance).isEqualTo(JournalLineDirection.CREDIT)
    }
}
