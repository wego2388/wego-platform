package com.wego.divers

import com.wego.divers.application.AdvanceEnrollmentStageService
import com.wego.divers.application.CreateDiverCommand
import com.wego.divers.application.CreateDiverService
import com.wego.divers.application.CreateOfferingCommand
import com.wego.divers.application.CreateOfferingService
import com.wego.divers.application.EnrollDiverInCourseCommand
import com.wego.divers.application.EnrollDiverInCourseResult
import com.wego.divers.application.EnrollDiverInCourseService
import com.wego.divers.domain.Money
import com.wego.divers.domain.OfferingType
import com.wego.divers.domain.PricingBasis
import com.wego.generated.jooq.tables.DiversCourseEnrollment.DIVERS_COURSE_ENROLLMENT
import com.wego.generated.jooq.tables.DiversCourseEnrollmentAuditEvent.DIVERS_COURSE_ENROLLMENT_AUDIT_EVENT
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

    private fun runConcurrently(actions: List<() -> Any?>) {
        val pool = Executors.newFixedThreadPool(actions.size)
        val ready = CountDownLatch(actions.size)
        val start = CountDownLatch(1)
        val done = CountDownLatch(actions.size)

        actions.forEach { action ->
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
