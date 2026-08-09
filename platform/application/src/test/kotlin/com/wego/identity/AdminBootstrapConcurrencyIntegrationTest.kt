package com.wego.identity

import com.wego.identity.application.AdminBootstrapService
import com.wego.identity.domain.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Proves the `pg_advisory_xact_lock` in `JooqUserRepository.lockBootstrap`
 * actually prevents the check-then-create race under real concurrency. Its
 * own container/context: sharing one with another concurrency test lets one
 * test's seeded data (any user at all) make every bootstrap attempt in this
 * test refuse regardless of whether the lock works.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class AdminBootstrapConcurrencyIntegrationTest {
    @Autowired
    private lateinit var adminBootstrapService: AdminBootstrapService

    @Test
    fun `concurrent bootstrap attempts create exactly one admin`() {
        val attempts = 5
        val results = ConcurrentLinkedQueue<Result<User>>()

        runConcurrently(attempts) { index ->
            results +=
                runCatching {
                    adminBootstrapService.bootstrap("racer-$index@example.com", "a-very-long-password-$index")
                }
        }

        assertThat(results.count { it.isSuccess }).isEqualTo(1)
        assertThat(results.count { it.isFailure }).isEqualTo(attempts - 1)
    }

    private fun runConcurrently(
        count: Int,
        action: (Int) -> Unit,
    ) {
        val pool = Executors.newFixedThreadPool(count)
        val ready = CountDownLatch(count)
        val start = CountDownLatch(1)
        val done = CountDownLatch(count)

        repeat(count) { index ->
            pool.submit {
                ready.countDown()
                start.await()
                try {
                    action(index)
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
