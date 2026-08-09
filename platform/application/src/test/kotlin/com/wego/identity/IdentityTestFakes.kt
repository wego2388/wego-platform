package com.wego.identity

import com.wego.identity.application.IdentityAuditRecorder
import com.wego.identity.application.LoginAttemptThrottle
import com.wego.identity.application.PasswordHasher
import com.wego.identity.application.SessionRepository
import com.wego.identity.application.SessionTokenGenerator
import com.wego.identity.application.ThrottleDecision
import com.wego.identity.application.TransactionRunner
import com.wego.identity.application.UserRepository
import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.HashedPassword
import com.wego.identity.domain.Session
import com.wego.identity.domain.SessionId
import com.wego.identity.domain.User
import com.wego.identity.domain.UserId
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Overrides the real, singleton-scoped [LoginAttemptThrottle] bean for
 * `@SpringBootTest` classes whose test methods legitimately make several or
 * concurrent login attempts against the same email — behavior the real
 * per-email throttle exists to reject, and would otherwise make these tests
 * flaky for reasons unrelated to what they're actually proving. The
 * throttle's own behavior is proven end to end, over real HTTP, in
 * `LoginRateLimitHttpTest`, which does not import this configuration.
 */
@TestConfiguration
class NoThrottleConfiguration {
    @Bean
    @Primary
    fun loginAttemptThrottle(): LoginAttemptThrottle = AlwaysAllowLoginAttemptThrottle()
}

/**
 * A `Clock` a test can advance explicitly, for scenarios where real elapsed
 * time matters (throttle backoff schedules) but real sleeps would make the
 * test slow and flaky. Shared by [com.wego.identity.InMemoryLoginAttemptThrottleTest]
 * and any test that paces a real throttle against a real clock.
 */
internal class MutableClock(
    private var current: Instant,
    private val zone: ZoneId = ZoneId.of("UTC"),
) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

    override fun instant(): Instant = current

    fun advance(duration: Duration) {
        current = current.plus(duration)
    }
}

internal class InMemoryUserRepository : UserRepository {
    val byId = mutableMapOf<UserId, User>()

    override fun findByEmail(email: EmailAddress): User? = byId.values.find { it.email == email }

    // No real locking semantics: fakes are single-threaded test doubles.
    // Row-locking behavior itself is proven against real PostgreSQL in
    // IdentityConcurrencyIntegrationTest, not here.
    override fun findByEmailForUpdate(email: EmailAddress): User? = findByEmail(email)

    override fun findById(id: UserId): User? = byId[id]

    override fun existsAny(): Boolean = byId.isNotEmpty()

    override fun lockBootstrap() = Unit

    override fun save(user: User) {
        byId[user.id] = user
    }
}

internal class NoOpTransactionRunner : TransactionRunner {
    override fun <T> runInTransaction(block: () -> T): T = block()
}

internal class AlwaysAllowLoginAttemptThrottle : LoginAttemptThrottle {
    val recordedFailures = mutableListOf<String>()
    val recordedSuccesses = mutableListOf<String>()

    override fun tryAcquire(key: String): ThrottleDecision = ThrottleDecision.Allowed

    override fun recordFailure(key: String) {
        recordedFailures += key
    }

    override fun recordSuccess(key: String) {
        recordedSuccesses += key
    }
}

internal class AlwaysRejectLoginAttemptThrottle(
    private val retryAfterSeconds: Long = 7,
) : LoginAttemptThrottle {
    override fun tryAcquire(key: String): ThrottleDecision = ThrottleDecision.Rejected(retryAfterSeconds)

    override fun recordFailure(key: String) = Unit

    override fun recordSuccess(key: String) = Unit
}

internal class InMemorySessionRepository : SessionRepository {
    val byId = mutableMapOf<SessionId, Session>()

    override fun save(session: Session) {
        byId[session.id] = session
    }

    override fun findActiveByTokenHash(
        tokenHash: String,
        asOf: Instant,
    ): Session? = byId.values.find { it.tokenHash == tokenHash && it.isActive(asOf) }

    override fun revoke(
        id: SessionId,
        asOf: Instant,
    ) {
        byId[id]?.revoke(asOf)
    }
}

/** Stores passwords in plaintext with a fixed prefix — test-only, never production. */
internal class FakePasswordHasher : PasswordHasher {
    override fun hash(rawPassword: String): HashedPassword = HashedPassword.of("hashed:$rawPassword")

    override fun matches(
        rawPassword: String,
        hashed: HashedPassword,
    ): Boolean = hashed.value == "hashed:$rawPassword"
}

internal class SequentialTokenGenerator : SessionTokenGenerator {
    private var counter = 0

    override fun generate(): String = "token-${counter++}"

    override fun hash(rawToken: String): String = "hash-of-$rawToken"
}

internal class RecordingAuditRecorder : IdentityAuditRecorder {
    val loginSuccesses = mutableListOf<UserId>()
    val loginFailures = mutableListOf<Pair<String, String>>()
    val logouts = mutableListOf<UserId>()
    val permissionDenials = mutableListOf<Pair<UserId, String>>()

    override fun recordLoginSuccess(
        userId: UserId,
        occurredAt: Instant,
        correlationId: UUID?,
    ) {
        loginSuccesses += userId
    }

    override fun recordLoginFailure(
        email: String,
        reason: String,
        occurredAt: Instant,
        correlationId: UUID?,
    ) {
        loginFailures += email to reason
    }

    override fun recordLogout(
        userId: UserId,
        occurredAt: Instant,
        correlationId: UUID?,
    ) {
        logouts += userId
    }

    override fun recordPermissionDenied(
        userId: UserId,
        permission: String,
        occurredAt: Instant,
        correlationId: UUID?,
    ) {
        permissionDenials += userId to permission
    }
}
