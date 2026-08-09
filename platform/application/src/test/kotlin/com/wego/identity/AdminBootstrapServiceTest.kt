package com.wego.identity

import com.wego.identity.application.AdminBootstrapService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AdminBootstrapServiceTest {
    private val fixedInstant = Instant.parse("2026-08-09T00:00:00Z")
    private val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

    private fun newService(userRepository: InMemoryUserRepository = InMemoryUserRepository()) =
        AdminBootstrapService(userRepository, FakePasswordHasher(), NoOpTransactionRunner(), clock)

    @Test
    fun `creates the first admin with the requested email and default role`() {
        val service = newService()

        val user = service.bootstrap("admin@example.com", "a-very-long-password")

        assertThat(user.email.value).isEqualTo("admin@example.com")
        assertThat(user.roles.map { it.value }).containsExactly("platform-admin")
    }

    @Test
    fun `refuses when any user already exists`() {
        val userRepository = InMemoryUserRepository()
        val service = newService(userRepository)
        service.bootstrap("first@example.com", "a-very-long-password")

        assertThatIllegalStateException()
            .isThrownBy { service.bootstrap("second@example.com", "another-long-password") }
    }

    @Test
    fun `refuses a password shorter than the minimum length`() {
        val service = newService()

        assertThatIllegalArgumentException()
            .isThrownBy { service.bootstrap("admin@example.com", "short") }
    }
}
