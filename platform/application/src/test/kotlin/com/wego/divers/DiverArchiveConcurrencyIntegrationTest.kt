package com.wego.divers

import com.wego.divers.application.ArchiveDiverService
import com.wego.divers.application.CreateDiverCommand
import com.wego.divers.application.CreateDiverService
import com.wego.divers.application.UpdateDiverCommand
import com.wego.divers.application.UpdateDiverService
import com.wego.generated.jooq.tables.DiversDiver.DIVERS_DIVER
import com.wego.generated.jooq.tables.DiversDiverAuditEvent.DIVERS_DIVER_AUDIT_EVENT
import com.wego.generated.jooq.tables.IdentityUser.IDENTITY_USER
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Proves the `SELECT ... FOR UPDATE` row lock added to
 * `DiverRepository.findByIdForUpdate` — and now used by both
 * `UpdateDiverService` and `ArchiveDiverService` — actually prevents the
 * real regression independent Tier 1 review found: an unlocked concurrent
 * update reversing a terminal archive while leaving the `DIVER_ARCHIVED`
 * audit event behind. Fires many concurrent update/archive pairs at the
 * same diver and asserts the persisted row and its own audit trail can
 * never disagree — if `DIVER_ARCHIVED` was recorded, the diver must be
 * ARCHIVED with a real `archived_at`, never left ACTIVE.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class DiverArchiveConcurrencyIntegrationTest {
    @Autowired
    private lateinit var createDiverService: CreateDiverService

    @Autowired
    private lateinit var updateDiverService: UpdateDiverService

    @Autowired
    private lateinit var archiveDiverService: ArchiveDiverService

    @Autowired
    private lateinit var dsl: DSLContext

    @Test
    fun `a concurrent update never reverses a terminal archive`() {
        val diver =
            createDiverService.create(
                CreateDiverCommand(
                    fullName = "Concurrency Test Diver",
                    nationality = null,
                    primaryLanguage = null,
                    email = "concurrency-test-diver@example.com",
                    phone = null,
                    emergencyContactName = null,
                    emergencyContactPhone = null,
                    medicalNotes = null,
                    totalLoggedDives = 0,
                    maxDepthMeters = null,
                    lastDiveOn = null,
                    bcdSize = null,
                    finSize = null,
                    wetsuitSize = null,
                    certifications = emptyList(),
                    createdByUserId = null,
                    correlationId = null,
                ),
            )
        val actorId = UUID.randomUUID()
        dsl
            .insertInto(IDENTITY_USER)
            .set(IDENTITY_USER.ID, actorId)
            .set(IDENTITY_USER.EMAIL, "diver-archive-concurrency-test@example.com")
            .set(IDENTITY_USER.PASSWORD_HASH, "irrelevant-hash")
            .set(IDENTITY_USER.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .execute()

        val actions: List<() -> Unit> =
            listOf({
                archiveDiverService.archive(diver.id, actorId, null)
                Unit
            }) +
                (1..20).map { index ->
                    {
                        updateDiverService.update(
                            UpdateDiverCommand(
                                diverId = diver.id,
                                fullName = "Updated Name $index",
                                nationality = null,
                                primaryLanguage = null,
                                email = "concurrency-test-diver@example.com",
                                phone = null,
                                emergencyContactName = null,
                                emergencyContactPhone = null,
                                medicalNotes = null,
                                totalLoggedDives = index,
                                maxDepthMeters = null,
                                lastDiveOn = null,
                                bcdSize = null,
                                finSize = null,
                                wetsuitSize = null,
                                certifications = emptyList(),
                                actorUserId = actorId,
                                correlationId = null,
                            ),
                        )
                        Unit
                    }
                }

        runConcurrently(actions)

        val archivedEventCount =
            dsl
                .selectCount()
                .from(DIVERS_DIVER_AUDIT_EVENT)
                .where(DIVERS_DIVER_AUDIT_EVENT.DIVER_ID.eq(diver.id.value))
                .and(DIVERS_DIVER_AUDIT_EVENT.EVENT_TYPE.eq("DIVER_ARCHIVED"))
                .fetchOne(0, Int::class.java)
        assertThat(archivedEventCount).isEqualTo(1)

        val row = dsl.selectFrom(DIVERS_DIVER).where(DIVERS_DIVER.ID.eq(diver.id.value)).fetchOne()!!
        assertThat(row.status).isEqualTo("ARCHIVED")
        assertThat(row.archivedAt).isNotNull()
    }

    private fun runConcurrently(actions: List<() -> Any?>) {
        val pool = Executors.newFixedThreadPool(actions.size)
        val ready = CountDownLatch(actions.size)
        val start = CountDownLatch(1)
        val done = CountDownLatch(actions.size)
        val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())

        actions.forEach { action ->
            pool.submit {
                ready.countDown()
                start.await()
                try {
                    action()
                } catch (error: Throwable) {
                    errors += error
                } finally {
                    done.countDown()
                }
            }
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
        start.countDown()
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue()
        pool.shutdown()
        assertThat(errors).withFailMessage { "Unexpected exceptions under concurrency: ${errors.map { it.toString() }}" }.isEmpty()
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
