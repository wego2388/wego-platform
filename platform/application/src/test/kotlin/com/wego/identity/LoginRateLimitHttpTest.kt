package com.wego.identity

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * Proves the real, wired-by-default [com.wego.identity.infrastructure.InMemoryLoginAttemptThrottle]
 * bean actually rejects a second rapid attempt against the same account over
 * real HTTP — the application-level control that closes the gap an
 * IP-only edge limiter can't: an attacker spreading attempts across many
 * source addresses, or pacing them under the edge limiter's threshold, is
 * still caught here because the key is the target account, not the caller's
 * address. Deliberately does not import `NoThrottleConfiguration` — every
 * other `@SpringBootTest` in this module does, specifically so this is the
 * one place the real throttle bean runs.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class LoginRateLimitHttpTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    private fun login(
        email: String,
        password: String,
    ) = mockMvc
        .post("/api/v1/identity/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"$password"}"""
        }

    @Test
    fun `a second rapid attempt against the same account is rate-limited, not the first`() {
        val email = "rate-limit-target@example.com"

        login(email, "first-guess").andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("invalid_credentials") }
        }

        login(email, "second-guess").andExpect {
            status { isEqualTo(429) }
            header { string("Retry-After", org.hamcrest.Matchers.matchesPattern("[0-9]+")) }
            jsonPath("$.error") { value("rate_limited") }
        }
    }

    @Test
    fun `attempts against different accounts are not throttled by each other`() {
        login("rate-limit-a@example.com", "whatever").andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("invalid_credentials") }
        }

        login("rate-limit-b@example.com", "whatever").andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("invalid_credentials") }
        }
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
