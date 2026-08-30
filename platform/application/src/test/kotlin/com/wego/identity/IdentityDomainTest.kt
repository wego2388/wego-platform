package com.wego.identity

import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.HashedPassword
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.Session
import com.wego.identity.domain.SessionId
import com.wego.identity.domain.User
import com.wego.identity.domain.UserId
import com.wego.identity.domain.UserStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class EmailAddressTest {
    @Test
    fun `normalizes case and surrounding whitespace`() {
        assertThat(EmailAddress.of("  Admin@Example.COM  ").value).isEqualTo("admin@example.com")
    }

    @Test
    fun `rejects addresses without an at sign`() {
        assertThatIllegalArgumentException().isThrownBy { EmailAddress.of("not-an-email") }
    }

    @Test
    fun `rejects blank input`() {
        assertThatIllegalArgumentException().isThrownBy { EmailAddress.of("   ") }
    }
}

class RoleCodeTest {
    @Test
    fun `accepts lowercase hyphenated codes`() {
        assertThat(RoleCode.of("platform-admin").value).isEqualTo("platform-admin")
    }

    @Test
    fun `rejects uppercase or underscore codes`() {
        assertThatIllegalArgumentException().isThrownBy { RoleCode.of("Platform_Admin") }
    }
}

class UserTest {
    private val now = Instant.parse("2026-08-09T00:00:00Z")

    private fun bootstrapUser(): User =
        User.bootstrap(
            email = EmailAddress.of("admin@example.com"),
            passwordHash = HashedPassword.of("hash"),
            role = RoleCode.of("platform-admin"),
            now = now,
        )

    @Test
    fun `a freshly bootstrapped user can authenticate`() {
        assertThat(bootstrapUser().canAuthenticate(now)).isTrue()
    }

    @Test
    fun `a disabled user cannot authenticate even without lockout`() {
        val user = bootstrapUser()
        user.disable()
        assertThat(user.canAuthenticate(now)).isFalse()
    }

    @Test
    fun `disabling an already-disabled account is rejected`() {
        val user = bootstrapUser()
        user.disable()
        assertThatIllegalArgumentException().isThrownBy { user.disable() }
    }

    @Test
    fun `enabling a disabled account restores authentication`() {
        val user = bootstrapUser()
        user.disable()
        user.enable()
        assertThat(user.status).isEqualTo(UserStatus.ACTIVE)
        assertThat(user.canAuthenticate(now)).isTrue()
    }

    @Test
    fun `enabling an already-active account is rejected`() {
        val user = bootstrapUser()
        assertThatIllegalArgumentException().isThrownBy { user.enable() }
    }

    @Test
    fun `re-enabling does not clear a real lockout still in effect`() {
        val user = bootstrapUser()
        repeat(5) { user.registerFailedLogin(now, maxAttempts = 5, lockoutDuration = Duration.ofMinutes(15)) }
        assertThat(user.isLocked(now)).isTrue()

        user.disable()
        user.enable()

        assertThat(user.isLocked(now)).isTrue()
        assertThat(user.canAuthenticate(now)).isFalse()
    }

    @Test
    fun `changing the password replaces the stored hash`() {
        val user = bootstrapUser()
        val newHash = HashedPassword.of("a-different-hash")
        user.changePassword(newHash)
        assertThat(user.passwordHash).isEqualTo(newHash)
    }

    @Test
    fun `assigning roles replaces the full role set`() {
        val user = bootstrapUser()
        user.assignRoles(setOf(RoleCode.of("front-desk"), RoleCode.of("accountant")))
        assertThat(user.roles).containsExactlyInAnyOrder(RoleCode.of("front-desk"), RoleCode.of("accountant"))
    }

    @Test
    fun `assigning an empty role set is rejected`() {
        val user = bootstrapUser()
        assertThatIllegalArgumentException().isThrownBy { user.assignRoles(emptySet()) }
    }

    @Test
    fun `create rejects an empty role set`() {
        assertThatIllegalArgumentException().isThrownBy {
            User.create(EmailAddress.of("new@example.com"), HashedPassword.of("hash"), emptySet(), now)
        }
    }

    @Test
    fun `create builds a real active user with the given roles`() {
        val user =
            User.create(
                EmailAddress.of("new@example.com"),
                HashedPassword.of("hash"),
                setOf(RoleCode.of("front-desk")),
                now,
            )
        assertThat(user.status).isEqualTo(UserStatus.ACTIVE)
        assertThat(user.roles).containsExactly(RoleCode.of("front-desk"))
        assertThat(user.canAuthenticate(now)).isTrue()
    }

    @Test
    fun `locks after reaching the failure threshold and blocks authentication until it expires`() {
        val user = bootstrapUser()
        repeat(4) { user.registerFailedLogin(now, maxAttempts = 5, lockoutDuration = Duration.ofMinutes(15)) }
        assertThat(user.canAuthenticate(now)).isTrue()

        user.registerFailedLogin(now, maxAttempts = 5, lockoutDuration = Duration.ofMinutes(15))
        assertThat(user.isLocked(now)).isTrue()
        assertThat(user.canAuthenticate(now)).isFalse()

        val afterLockoutWindow = now.plus(Duration.ofMinutes(16))
        assertThat(user.isLocked(afterLockoutWindow)).isFalse()
        assertThat(user.canAuthenticate(afterLockoutWindow)).isTrue()
    }

    @Test
    fun `a successful login clears failed attempts and any lock`() {
        val user = bootstrapUser()
        repeat(5) { user.registerFailedLogin(now, maxAttempts = 5, lockoutDuration = Duration.ofMinutes(15)) }
        assertThat(user.isLocked(now)).isTrue()

        user.registerSuccessfulLogin()

        assertThat(user.failedLoginCount).isZero()
        assertThat(user.isLocked(now)).isFalse()
    }
}

class SessionTest {
    private val now = Instant.parse("2026-08-09T00:00:00Z")

    private fun activeSession(): Session =
        Session(
            id = SessionId.generate(),
            userId = UserId.generate(),
            tokenHash = "a".repeat(64),
            issuedAt = now,
            expiresAt = now.plus(Duration.ofHours(12)),
            revokedAt = null,
        )

    @Test
    fun `rejects a session that would expire before it is issued`() {
        assertThatIllegalArgumentException().isThrownBy {
            Session(
                id = SessionId.generate(),
                userId = UserId.generate(),
                tokenHash = "a".repeat(64),
                issuedAt = now,
                expiresAt = now.minusSeconds(1),
                revokedAt = null,
            )
        }
    }

    @Test
    fun `is active while unrevoked and unexpired`() {
        assertThat(activeSession().isActive(now)).isTrue()
    }

    @Test
    fun `is inactive once expired`() {
        val session = activeSession()
        assertThat(session.isActive(now.plus(Duration.ofHours(13)))).isFalse()
    }

    @Test
    fun `revoking is idempotent and keeps the first revocation instant`() {
        val session = activeSession()
        val firstRevocation = now.plusSeconds(30)
        session.revoke(firstRevocation)
        session.revoke(firstRevocation.plusSeconds(60))

        assertThat(session.revokedAt).isEqualTo(firstRevocation)
        assertThat(session.isActive(now)).isFalse()
    }
}
