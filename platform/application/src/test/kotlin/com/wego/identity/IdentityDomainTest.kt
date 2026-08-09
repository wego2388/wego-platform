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
        // status is only mutable via package-internal transitions in later packets;
        // this test documents current behaviour via the constructor directly.
        val disabled =
            User(
                id = user.id,
                email = user.email,
                passwordHash = user.passwordHash,
                status = UserStatus.DISABLED,
                roles = user.roles,
                createdAt = user.createdAt,
                failedLoginCount = 0,
                lockedUntil = null,
            )
        assertThat(disabled.canAuthenticate(now)).isFalse()
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
