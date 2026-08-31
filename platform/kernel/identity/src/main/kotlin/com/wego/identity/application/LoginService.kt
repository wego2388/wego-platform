package com.wego.identity.application
import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.HashedPassword
import com.wego.identity.domain.Session
import com.wego.identity.domain.SessionId
import com.wego.transaction.TransactionRunner
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Failure reasons are intentionally identical for "unknown email", "wrong
 * password", and "locked/disabled account" (see [FAILURE_REASON]) so a caller
 * cannot enumerate registered emails from the login response. The distinct
 * cause is still recorded server-side via [IdentityAuditRecorder].
 */
class LoginService(
    private val userRepository: UserRepository,
    private val sessionRepository: SessionRepository,
    private val passwordHasher: PasswordHasher,
    private val sessionTokenGenerator: SessionTokenGenerator,
    private val auditRecorder: IdentityAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val loginAttemptThrottle: LoginAttemptThrottle,
    private val clock: Clock,
    private val maxFailedAttempts: Int = 5,
    private val lockoutDuration: Duration = Duration.ofMinutes(15),
    private val sessionDuration: Duration = Duration.ofHours(12),
) {
    fun login(
        rawEmail: String,
        rawPassword: String,
        correlationId: UUID?,
    ): LoginResult {
        // Bounded independently of EmailAddress's own validation: an
        // over-length value must never reach the varchar(320) audit
        // column, which would turn a routine bad-input 401 into an
        // unhandled 500 from the failed INSERT.
        val boundedRawEmail = rawEmail.take(MAX_AUDIT_EMAIL_LENGTH)
        val throttleKey = boundedRawEmail.trim().lowercase()

        // Keyed by the account, not the caller's address — an edge-level,
        // per-IP limiter (see infrastructure/nginx/nginx.conf) can't stop
        // an attacker who paces requests under its rate or spreads them
        // across many source addresses; this closes that gap by throttling
        // the account itself, before any DB/bcrypt work happens at all.
        val throttleDecision = loginAttemptThrottle.tryAcquire(throttleKey)
        if (throttleDecision is ThrottleDecision.Rejected) {
            return LoginResult.failure(RATE_LIMITED_REASON, retryAfterSeconds = throttleDecision.retryAfterSeconds)
        }

        val result =
            transactionRunner.runInTransaction {
                val now = Instant.now(clock)
                val email = runCatching { EmailAddress.of(rawEmail) }.getOrNull()

                if (email == null) {
                    // Still pay bcrypt's cost so this branch isn't distinguishable
                    // by response time from a branch that does a real check.
                    passwordHasher.matches(rawPassword, DUMMY_HASH)
                    auditRecorder.recordLoginFailure(boundedRawEmail, "INVALID_EMAIL_FORMAT", now, correlationId)
                    return@runInTransaction LoginResult.failure(FAILURE_REASON)
                }

                // FOR UPDATE: two concurrent attempts against the same account
                // must serialize, or both can read the same failure count and
                // each write back count+1, losing an increment toward lockout.
                val user = userRepository.findByEmailForUpdate(email)
                if (user == null) {
                    passwordHasher.matches(rawPassword, DUMMY_HASH)
                    auditRecorder.recordLoginFailure(email.value, "UNKNOWN_EMAIL", now, correlationId)
                    return@runInTransaction LoginResult.failure(FAILURE_REASON)
                }

                if (!user.canAuthenticate(now)) {
                    passwordHasher.matches(rawPassword, DUMMY_HASH)
                    auditRecorder.recordLoginFailure(email.value, "ACCOUNT_LOCKED_OR_DISABLED", now, correlationId)
                    return@runInTransaction LoginResult.failure(FAILURE_REASON)
                }

                if (!passwordHasher.matches(rawPassword, user.passwordHash)) {
                    user.registerFailedLogin(now, maxFailedAttempts, lockoutDuration)
                    userRepository.save(user)
                    auditRecorder.recordLoginFailure(email.value, "WRONG_PASSWORD", now, correlationId)
                    return@runInTransaction LoginResult.failure(FAILURE_REASON)
                }

                user.registerSuccessfulLogin()
                userRepository.save(user)

                val rawToken = sessionTokenGenerator.generate()
                val session =
                    Session(
                        id = SessionId.generate(),
                        userId = user.id,
                        tokenHash = sessionTokenGenerator.hash(rawToken),
                        issuedAt = now,
                        expiresAt = now.plus(sessionDuration),
                        revokedAt = null,
                    )
                sessionRepository.save(session)
                auditRecorder.recordLoginSuccess(user.id, now, correlationId)

                LoginResult.success(rawToken, session, user)
            }

        // Recorded after the real outcome is known, not at tryAcquire time —
        // a success must reset the backoff (registerSuccessfulLogin already
        // resets the DB-side failure count the same way) and a failure must
        // escalate it, or every attempt would cost the same as the first and
        // an attacker who simply waits out each interval could still reach
        // the account's own lockout threshold on a predictable schedule.
        if (result.success) {
            loginAttemptThrottle.recordSuccess(throttleKey)
        } else {
            loginAttemptThrottle.recordFailure(throttleKey)
        }
        return result
    }

    companion object {
        const val FAILURE_REASON = "invalid_credentials"
        const val RATE_LIMITED_REASON = "rate_limited"
        private const val MAX_AUDIT_EMAIL_LENGTH = 320

        // A syntactically valid but well-known example bcrypt hash (from
        // jBCrypt's own reference material) that no real password will ever
        // match. Comparing against it costs roughly the same CPU time as a
        // real password check, so "unknown email" and "wrong password"
        // aren't distinguishable by response time alone.
        private val DUMMY_HASH =
            HashedPassword.of("{bcrypt}\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy")
    }
}
