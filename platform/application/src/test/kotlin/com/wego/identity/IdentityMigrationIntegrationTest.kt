package com.wego.identity

import com.wego.generated.jooq.tables.IdentitySession.IDENTITY_SESSION
import com.wego.generated.jooq.tables.IdentityUser.IDENTITY_USER
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class IdentityMigrationIntegrationTest(
    @Autowired private val flyway: Flyway,
) {
    @Test
    fun `boot auto migrates the identity schema and generated jooq types honor its constraints`() {
        assertThat(flyway.info().applied().map { it.version.toString() }).containsExactly("1", "2", "3")

        postgres.createConnection("").use { connection ->
            val dsl = DSL.using(connection, SQLDialect.POSTGRES)
            val now = OffsetDateTime.of(2026, 8, 9, 0, 0, 0, 0, ZoneOffset.UTC)
            val userId = UUID.randomUUID()

            val inserted =
                dsl
                    .insertInto(IDENTITY_USER)
                    .set(IDENTITY_USER.ID, userId)
                    .set(IDENTITY_USER.EMAIL, "constraint-test@example.com")
                    .set(IDENTITY_USER.PASSWORD_HASH, "irrelevant-hash")
                    .set(IDENTITY_USER.CREATED_AT, now)
                    .execute()
            assertThat(inserted).isEqualTo(1)

            assertThatThrownBy {
                dsl
                    .insertInto(IDENTITY_USER)
                    .set(IDENTITY_USER.ID, UUID.randomUUID())
                    .set(IDENTITY_USER.EMAIL, "constraint-test@example.com")
                    .set(IDENTITY_USER.PASSWORD_HASH, "another-hash")
                    .set(IDENTITY_USER.CREATED_AT, now)
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("identity_user_email_unique")

            assertThatThrownBy {
                dsl
                    .insertInto(IDENTITY_SESSION)
                    .set(IDENTITY_SESSION.ID, UUID.randomUUID())
                    .set(IDENTITY_SESSION.USER_ID, userId)
                    .set(IDENTITY_SESSION.TOKEN_HASH, "a".repeat(64))
                    .set(IDENTITY_SESSION.ISSUED_AT, now)
                    .set(IDENTITY_SESSION.EXPIRES_AT, now.minusSeconds(1))
                    .execute()
            }.isInstanceOf(DataAccessException::class.java)
                .hasMessageContaining("identity_session_expires_after_issued")
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
