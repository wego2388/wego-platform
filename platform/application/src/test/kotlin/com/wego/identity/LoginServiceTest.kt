package com.wego.identity

import com.wego.identity.application.LoginService
import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.User
import com.wego.identity.infrastructure.InMemoryLoginAttemptThrottle
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class LoginServiceTest {
    private lateinit var userRepository: InMemoryUserRepository
    private lateinit var sessionRepository: InMemorySessionRepository
    private lateinit var passwordHasher: FakePasswordHasher
    private lateinit var auditRecorder: RecordingAuditRecorder
    private lateinit var throttle: AlwaysAllowLoginAttemptThrottle
    private lateinit var service: LoginService

    private val fixedInstant = Instant.parse("2026-08-09T00:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    @BeforeEach
    fun setUp() {
        userRepository = InMemoryUserRepository()
        sessionRepository = InMemorySessionRepository()
        passwordHasher = FakePasswordHasher()
        auditRecorder = RecordingAuditRecorder()
        throttle = AlwaysAllowLoginAttemptThrottle()
        service =
            LoginService(
                userRepository = userRepository,
                sessionRepository = sessionRepository,
                passwordHasher = passwordHasher,
                sessionTokenGenerator = SequentialTokenGenerator(),
                auditRecorder = auditRecorder,
                transactionRunner = NoOpTransactionRunner(),
                loginAttemptThrottle = throttle,
                clock = clock,
                maxFailedAttempts = 3,
                lockoutDuration = Duration.ofMinutes(15),
            )
    }

    private fun seedUser(
        email: String = "admin@example.com",
        password: String = "correct-password",
    ): User {
        val user =
            User.bootstrap(
                email = EmailAddress.of(email),
                passwordHash = passwordHasher.hash(password),
                role = RoleCode.of("platform-admin"),
                now = fixedInstant,
            )
        userRepository.save(user)
        return user
    }

    @Test
    fun `unknown email fails without revealing whether the account exists`() {
        val result = service.login("nobody@example.com", "irrelevant", correlationId = null)

        assertThat(result.success).isFalse()
        assertThat(result.failureReason).isEqualTo(LoginService.FAILURE_REASON)
        assertThat(auditRecorder.loginFailures).containsExactly("nobody@example.com" to "UNKNOWN_EMAIL")
    }

    @Test
    fun `an over-length email is bounded before being audited, not left to fail the write`() {
        val hugeEmail = "a".repeat(500) + "@example.com"

        val result = service.login(hugeEmail, "irrelevant", correlationId = null)

        assertThat(result.success).isFalse()
        assertThat(result.failureReason).isEqualTo(LoginService.FAILURE_REASON)
        assertThat(
            auditRecorder.loginFailures
                .single()
                .first.length,
        ).isLessThanOrEqualTo(320)
    }

    @Test
    fun `wrong password fails and is audited with the specific reason`() {
        seedUser()

        val result = service.login("admin@example.com", "wrong-password", correlationId = null)

        assertThat(result.success).isFalse()
        assertThat(auditRecorder.loginFailures).containsExactly("admin@example.com" to "WRONG_PASSWORD")
        // The throttle must learn about the failure so a repeat attempt against
        // this account costs more than the first one did.
        assertThat(throttle.recordedFailures).containsExactly("admin@example.com")
        assertThat(throttle.recordedSuccesses).isEmpty()
    }

    @Test
    fun `correct credentials succeed and issue an active session`() {
        seedUser()

        val result = service.login("admin@example.com", "correct-password", correlationId = null)

        assertThat(result.success).isTrue()
        assertThat(result.rawToken).isNotBlank()
        assertThat(result.session!!.isActive(fixedInstant)).isTrue()
        assertThat(auditRecorder.loginSuccesses).hasSize(1)
        assertThat(throttle.recordedSuccesses).containsExactly("admin@example.com")
        assertThat(throttle.recordedFailures).isEmpty()
    }

    @Test
    fun `account locks after the configured number of consecutive failures`() {
        seedUser()

        repeat(3) { service.login("admin@example.com", "wrong-password", correlationId = null) }
        val lockedOutResult = service.login("admin@example.com", "correct-password", correlationId = null)

        assertThat(lockedOutResult.success).isFalse()
        assertThat(auditRecorder.loginFailures.last()).isEqualTo("admin@example.com" to "ACCOUNT_LOCKED_OR_DISABLED")
    }

    @Test
    fun `a successful login resets a prior failure count`() {
        seedUser()

        repeat(2) { service.login("admin@example.com", "wrong-password", correlationId = null) }
        val result = service.login("admin@example.com", "correct-password", correlationId = null)

        assertThat(result.success).isTrue()
        assertThat(userRepository.findByEmail(EmailAddress.of("admin@example.com"))!!.failedLoginCount).isZero()
    }

    @Test
    fun `a throttled attempt fails fast without touching the repository or the audit log`() {
        val throttledService =
            LoginService(
                userRepository = userRepository,
                sessionRepository = sessionRepository,
                passwordHasher = passwordHasher,
                sessionTokenGenerator = SequentialTokenGenerator(),
                auditRecorder = auditRecorder,
                transactionRunner = NoOpTransactionRunner(),
                loginAttemptThrottle = AlwaysRejectLoginAttemptThrottle(),
                clock = clock,
                maxFailedAttempts = 3,
                lockoutDuration = Duration.ofMinutes(15),
            )
        seedUser()

        val result = throttledService.login("admin@example.com", "correct-password", correlationId = null)

        assertThat(result.success).isFalse()
        assertThat(result.failureReason).isEqualTo(LoginService.RATE_LIMITED_REASON)
        assertThat(result.retryAfterSeconds).isEqualTo(7L)
        assertThat(auditRecorder.loginFailures).isEmpty()
        assertThat(auditRecorder.loginSuccesses).isEmpty()
    }

    @Test
    fun `pacing exactly at the real throttle's own advertised retry-after cannot lock the account before the lockout duration elapses`() {
        // The real throttle (production defaults), a real LoginService, and a
        // real seeded account — not the isolated throttle-only progression
        // test. A caller obeying (or racing right up against) the throttle's
        // own signals is the fastest a well-behaved-but-malicious client can
        // possibly go; if even that can't force a lock before the lockout
        // itself would expire, forcing a lockout is no longer cheaper than
        // just waiting one out.
        val mutableClock = MutableClock(fixedInstant)
        val realThrottle = InMemoryLoginAttemptThrottle(clock = mutableClock)
        val lockoutDuration = Duration.ofMinutes(15)
        val email = "targeted-lockout-target@example.com"
        val serviceWithRealThrottle =
            LoginService(
                userRepository = userRepository,
                sessionRepository = sessionRepository,
                passwordHasher = passwordHasher,
                sessionTokenGenerator = SequentialTokenGenerator(),
                auditRecorder = auditRecorder,
                transactionRunner = NoOpTransactionRunner(),
                loginAttemptThrottle = realThrottle,
                clock = mutableClock,
                maxFailedAttempts = 5,
                lockoutDuration = lockoutDuration,
            )
        seedUser(email = email, password = "correct-password")

        var elapsed = Duration.ZERO
        var locked = false
        var iterations = 0
        while (!locked) {
            iterations++
            check(iterations <= 50) { "did not reach a lock within a sane number of iterations" }

            val result = serviceWithRealThrottle.login(email, "wrong-password", correlationId = null)
            if (result.failureReason == LoginService.RATE_LIMITED_REASON) {
                val wait = Duration.ofSeconds(requireNotNull(result.retryAfterSeconds))
                mutableClock.advance(wait)
                elapsed += wait
                continue
            }

            locked = userRepository.findByEmail(EmailAddress.of(email))!!.isLocked(mutableClock.instant())
        }

        // The exact figure documented in InMemoryLoginAttemptThrottle's own
        // class doc comment and in SECURITY_MODEL.md — asserted precisely,
        // not just "greater than the lockout duration", so a future change
        // to the backoff schedule that silently drifts the real number away
        // from what's documented fails this test rather than passing
        // unnoticed under a loose bound. Attempts land at t=0, +2m, +6m,
        // +14m, +29m; the throttle-imposed waits between them are
        // 2m + 4m + 8m + 15m (the last capped at maxInterval=15m rather
        // than the uncapped 16m), summing to exactly 29 minutes before the
        // fifth (locking) attempt.
        assertThat(elapsed).isEqualTo(Duration.ofMinutes(29))
        assertThat(elapsed).isGreaterThan(lockoutDuration)
    }
}
