package com.wego.identity

import com.wego.identity.application.LoginService
import com.wego.identity.application.PasswordHasher
import com.wego.identity.application.UserRepository
import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Proves the `SELECT ... FOR UPDATE` row lock in
 * `JooqUserRepository.findByEmailForUpdate` actually prevents a lost update
 * under real concurrency — a race an in-memory fake or a single-threaded
 * test can't exercise meaningfully. Its own container/context: sharing one
 * with another concurrency test lets one test's seeded data change the
 * other's starting state.
 *
 * Fires many concurrent attempts against one email on purpose, to exercise
 * the row lock — exactly what the real per-email `LoginAttemptThrottle`
 * exists to reject in production, so it's overridden here via
 * [NoThrottleConfiguration]. The throttle's own behavior is proven
 * separately, end to end over real HTTP, in [LoginRateLimitHttpTest].
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@Import(NoThrottleConfiguration::class)
class LoginLockoutConcurrencyIntegrationTest {
    @Autowired
    private lateinit var loginService: LoginService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    @Test
    fun `concurrent wrong-password attempts against one account do not lose failure counts`() {
        val email = "concurrency-lockout@example.com"
        val user =
            User.bootstrap(
                email = EmailAddress.of(email),
                passwordHash = passwordHasher.hash("the-real-password-123"),
                role = RoleCode.of("platform-admin"),
                now = Instant.now(),
            )
        userRepository.save(user)

        // More attempts than the default lockout threshold (5): once the
        // account locks, later attempts correctly take the already-locked
        // branch instead of incrementing further, so the count should land
        // exactly on the threshold — not below it (a lost update) and not
        // above it (the lock check itself racing).
        val attempts = 8
        runConcurrently(attempts) {
            loginService.login(email, "definitely-wrong", correlationId = null)
        }

        val reloaded = userRepository.findByEmail(EmailAddress.of(email))
        assertThat(reloaded!!.failedLoginCount).isEqualTo(DEFAULT_MAX_FAILED_ATTEMPTS)
        assertThat(reloaded.isLocked(Instant.now().plusSeconds(1))).isTrue()
    }

    private fun runConcurrently(
        count: Int,
        action: () -> Unit,
    ) {
        val pool = Executors.newFixedThreadPool(count)
        val ready = CountDownLatch(count)
        val start = CountDownLatch(1)
        val done = CountDownLatch(count)

        repeat(count) {
            pool.submit {
                ready.countDown()
                start.await()
                try {
                    action()
                } finally {
                    done.countDown()
                }
            }
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
        start.countDown()
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue()
        pool.shutdown()
    }

    companion object {
        // Must match LoginService's default maxFailedAttempts — the
        // Spring-wired bean under test doesn't override it.
        private const val DEFAULT_MAX_FAILED_ATTEMPTS = 5

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("wego_test")
                .withUsername("wego_test")
                .withPassword("wego_test")

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.enabled") { true }
        }
    }
}
