package com.wego.identity

import com.wego.identity.application.IdentityAuditRecorder
import com.wego.identity.application.LoginService
import com.wego.identity.application.PasswordHasher
import com.wego.identity.application.UserRepository
import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.User
import com.wego.identity.domain.UserId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.util.UUID

/**
 * Proves `LoginService.login()` is atomic: before this packet's fix, the
 * user-state update and the audit write each committed in their own
 * independent transaction, so a failure in the second step left the first
 * one's mutation permanently persisted. This test forces the audit step to
 * fail and asserts the user mutation is rolled back with it.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@Import(LoginAtomicityIntegrationTest.FailingAuditRecorderConfiguration::class)
class LoginAtomicityIntegrationTest {
    @Autowired
    private lateinit var loginService: LoginService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    @Test
    fun `a failure recording the audit event rolls back the user's failed-login counter too`() {
        val email = "atomicity@example.com"
        val user =
            User.bootstrap(
                email = EmailAddress.of(email),
                passwordHash = passwordHasher.hash("the-real-password-123"),
                role = RoleCode.of("platform-admin"),
                now = Instant.now(),
            )
        userRepository.save(user)

        assertThatThrownBy { loginService.login(email, "wrong-password", correlationId = null) }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("Simulated audit failure")

        val reloaded = userRepository.findByEmail(EmailAddress.of(email))
        assertThat(reloaded!!.failedLoginCount)
            .describedAs("the failed-login increment must roll back with the audit write, not persist independently")
            .isEqualTo(0)
    }

    @TestConfiguration
    class FailingAuditRecorderConfiguration {
        @Bean
        @Primary
        fun failingAuditRecorder(): IdentityAuditRecorder =
            object : IdentityAuditRecorder {
                override fun recordLoginSuccess(
                    userId: UserId,
                    occurredAt: Instant,
                    correlationId: UUID?,
                ) = Unit

                override fun recordLoginFailure(
                    email: String,
                    reason: String,
                    occurredAt: Instant,
                    correlationId: UUID?,
                ): Unit = throw RuntimeException("Simulated audit failure")

                override fun recordLogout(
                    userId: UserId,
                    occurredAt: Instant,
                    correlationId: UUID?,
                ) = Unit

                override fun recordPermissionDenied(
                    userId: UserId,
                    permission: String,
                    occurredAt: Instant,
                    correlationId: UUID?,
                ) = Unit
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
