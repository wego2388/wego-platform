package com.wego.divers

import com.wego.divers.application.AdvanceEnrollmentStageService
import com.wego.divers.application.ArchiveDiverService
import com.wego.divers.application.CloseOfferingService
import com.wego.divers.application.CreateDiverCommand
import com.wego.divers.application.CreateDiverService
import com.wego.divers.application.CreateOfferingCommand
import com.wego.divers.application.CreateOfferingService
import com.wego.divers.application.EnrollDiverInCourseCommand
import com.wego.divers.application.EnrollDiverInCourseResult
import com.wego.divers.application.EnrollDiverInCourseService
import com.wego.divers.domain.DiverId
import com.wego.divers.domain.Money
import com.wego.divers.domain.OfferingType
import com.wego.divers.domain.PricingBasis
import com.wego.generated.jooq.tables.DiversCourseEnrollment.DIVERS_COURSE_ENROLLMENT
import com.wego.generated.jooq.tables.DiversCourseEnrollmentAuditEvent.DIVERS_COURSE_ENROLLMENT_AUDIT_EVENT
import com.wego.generated.jooq.tables.DiversDiver.DIVERS_DIVER
import com.wego.generated.jooq.tables.DiversOffering.DIVERS_OFFERING
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
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Proves the `SELECT ... FOR UPDATE` row lock added to
 * `CourseEnrollmentRepository.findByIdForUpdate` closes the real
 * lost-update race independent Tier 1 review found: concurrent advances
 * overwriting one another while each still appends its own audit event.
 * Fires 3 concurrent `advance()` calls at a fresh LEAD enrollment; with the
 * lock in place every call must genuinely move the enrollment one further
 * real step (LEAD -> THEORY -> POOL -> OPEN_WATER), never collapse into
 * fewer real transitions than successful calls.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class CourseEnrollmentAdvanceConcurrencyIntegrationTest {
    @Autowired
    private lateinit var createDiverService: CreateDiverService

    @Autowired
    private lateinit var createOfferingService: CreateOfferingService

    @Autowired
    private lateinit var enrollService: EnrollDiverInCourseService

    @Autowired
    private lateinit var advanceService: AdvanceEnrollmentStageService

    @Autowired
    private lateinit var archiveDiverService: ArchiveDiverService

    @Autowired
    private lateinit var closeOfferingService: CloseOfferingService

    @Autowired
    private lateinit var dsl: DSLContext

    @Test
    fun `concurrent advances never lose an update`() {
        val diver =
            createDiverService.create(
                CreateDiverCommand(
                    fullName = "Concurrency Test Student",
                    nationality = null,
                    primaryLanguage = null,
                    email = "concurrency-test-student@example.com",
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
        val offering =
            createOfferingService.create(
                CreateOfferingCommand(
                    offeringType = OfferingType.COURSE,
                    title = "Concurrency Test Course",
                    description = null,
                    startsOn = LocalDate.parse("2026-09-01"),
                    endsOn = null,
                    capacity = null,
                    pricingBasis = PricingBasis.FLAT,
                    unitPrice = Money(BigDecimal("350.00"), "EUR"),
                    createdByUserId = null,
                    correlationId = null,
                ),
            )
        val enrollment =
            (
                enrollService.enroll(
                    EnrollDiverInCourseCommand(diver.id, offering.id, null, null),
                ) as EnrollDiverInCourseResult.Enrolled
            ).enrollment

        runConcurrently(
            (1..3).map {
                {
                    advanceService.advance(enrollment.id, null, null)
                    Unit
                }
            },
        )

        val advancedEventCount =
            dsl
                .selectCount()
                .from(DIVERS_COURSE_ENROLLMENT_AUDIT_EVENT)
                .where(DIVERS_COURSE_ENROLLMENT_AUDIT_EVENT.ENROLLMENT_ID.eq(enrollment.id.value))
                .and(DIVERS_COURSE_ENROLLMENT_AUDIT_EVENT.EVENT_TYPE.eq("STAGE_ADVANCED"))
                .fetchOne(0, Int::class.java)
        assertThat(advancedEventCount).isEqualTo(3)

        val finalStage =
            dsl
                .select(DIVERS_COURSE_ENROLLMENT.STAGE)
                .from(DIVERS_COURSE_ENROLLMENT)
                .where(DIVERS_COURSE_ENROLLMENT.ID.eq(enrollment.id.value))
                .fetchOne(0, String::class.java)
        assertThat(finalStage).isEqualTo("OPEN_WATER")
    }

    @Test
    fun `an enrollment can never be created after the diver has already been archived`() {
        // Real gap independent Tier 1 re-review found: EnrollDiverInCourseService's diver/offering
        // active-state checks were unlocked reads, so a concurrent archive could commit while an
        // in-flight enroll() still used its own stale "still active" read. Now both findByIdForUpdate.
        // Real invariant proven here (temporal, not just final-state): whichever transaction actually
        // committed second must have observed the first one's result — a real, successful enrollment can
        // never have been created strictly after the diver's own archive timestamp.
        val trials = 30
        val diverIds =
            (1..trials).map { index ->
                createDiverService
                    .create(
                        CreateDiverCommand(
                            fullName = "Enroll-Archive Race Diver $index",
                            nationality = null,
                            primaryLanguage = null,
                            email = "enroll-archive-race-$index@example.com",
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
                    ).id
            }
        val offering =
            createOfferingService.create(
                CreateOfferingCommand(
                    offeringType = OfferingType.COURSE,
                    title = "Enroll-Archive Race Course",
                    description = null,
                    startsOn = LocalDate.parse("2026-09-01"),
                    endsOn = null,
                    capacity = null,
                    pricingBasis = PricingBasis.FLAT,
                    unitPrice = Money(BigDecimal("350.00"), "EUR"),
                    createdByUserId = null,
                    correlationId = null,
                ),
            )
        val actorId = UUID.randomUUID()
        dsl
            .insertInto(IDENTITY_USER)
            .set(IDENTITY_USER.ID, actorId)
            .set(IDENTITY_USER.EMAIL, "enroll-archive-race-actor@example.com")
            .set(IDENTITY_USER.PASSWORD_HASH, "irrelevant-hash")
            .set(IDENTITY_USER.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .execute()

        runConcurrently(
            diverIds.flatMap { diverId ->
                listOf(
                    {
                        archiveDiverService.archive(diverId, actorId, null)
                        Unit
                    },
                    {
                        enrollService.enroll(EnrollDiverInCourseCommand(diverId, offering.id, null, null))
                        Unit
                    },
                )
            },
        )

        for (diverId in diverIds) {
            val archivedAt =
                dsl.select(DIVERS_DIVER.ARCHIVED_AT).from(DIVERS_DIVER).where(DIVERS_DIVER.ID.eq(diverId.value)).fetchOne(
                    0,
                    OffsetDateTime::class.java,
                )!!
            val enrollmentCreatedAt =
                dsl
                    .select(DIVERS_COURSE_ENROLLMENT.CREATED_AT)
                    .from(DIVERS_COURSE_ENROLLMENT)
                    .where(DIVERS_COURSE_ENROLLMENT.DIVER_ID.eq(diverId.value))
                    .fetchOne(0, OffsetDateTime::class.java)
            if (enrollmentCreatedAt == null) continue
            assertThat(enrollmentCreatedAt)
                .withFailMessage(
                    "Diver $diverId got a real enrollment created at $enrollmentCreatedAt, strictly after its own archive at $archivedAt",
                ).isBeforeOrEqualTo(archivedAt)
        }
    }

    @Test
    fun `an enrollment can never be created after the course offering has already been closed`() {
        val trials = 30
        val offeringIds =
            (1..trials).map { index ->
                createOfferingService
                    .create(
                        CreateOfferingCommand(
                            offeringType = OfferingType.COURSE,
                            title = "Enroll-Close Race Course $index",
                            description = null,
                            startsOn = LocalDate.parse("2026-09-01"),
                            endsOn = null,
                            capacity = null,
                            pricingBasis = PricingBasis.FLAT,
                            unitPrice = Money(BigDecimal("350.00"), "EUR"),
                            createdByUserId = null,
                            correlationId = null,
                        ),
                    ).id
            }
        val diver =
            createDiverService.create(
                CreateDiverCommand(
                    fullName = "Enroll-Close Race Diver",
                    nationality = null,
                    primaryLanguage = null,
                    email = "enroll-close-race@example.com",
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
            .set(IDENTITY_USER.EMAIL, "enroll-close-race-actor@example.com")
            .set(IDENTITY_USER.PASSWORD_HASH, "irrelevant-hash")
            .set(IDENTITY_USER.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .execute()

        runConcurrently(
            offeringIds.flatMap { offeringId ->
                listOf(
                    {
                        closeOfferingService.close(offeringId, actorId, "Race test", null)
                        Unit
                    },
                    {
                        enrollService.enroll(EnrollDiverInCourseCommand(DiverId(diver.id.value), offeringId, null, null))
                        Unit
                    },
                )
            },
        )

        for (offeringId in offeringIds) {
            val closedAt =
                dsl.select(DIVERS_OFFERING.CLOSED_AT).from(DIVERS_OFFERING).where(DIVERS_OFFERING.ID.eq(offeringId.value)).fetchOne(
                    0,
                    OffsetDateTime::class.java,
                )!!
            val enrollmentCreatedAt =
                dsl
                    .select(DIVERS_COURSE_ENROLLMENT.CREATED_AT)
                    .from(DIVERS_COURSE_ENROLLMENT)
                    .where(DIVERS_COURSE_ENROLLMENT.OFFERING_ID.eq(offeringId.value))
                    .fetchOne(0, OffsetDateTime::class.java)
            if (enrollmentCreatedAt == null) continue
            assertThat(enrollmentCreatedAt)
                .withFailMessage(
                    "Offering $offeringId got a real enrollment created at $enrollmentCreatedAt, strictly after its own close at $closedAt",
                ).isBeforeOrEqualTo(closedAt)
        }
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
