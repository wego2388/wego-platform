package com.wego.divers

import com.wego.divers.application.CreateBoatCharterCommand
import com.wego.divers.application.CreateBoatCharterService
import com.wego.divers.application.CreateOfferingCommand
import com.wego.divers.application.CreateOfferingService
import com.wego.divers.application.EndCharterService
import com.wego.divers.application.LinkOfferingToCharterCommand
import com.wego.divers.application.LinkOfferingToCharterService
import com.wego.divers.application.UpdateBoatCharterCommand
import com.wego.divers.application.UpdateBoatCharterService
import com.wego.divers.domain.CharterType
import com.wego.divers.domain.Money
import com.wego.divers.domain.OfferingType
import com.wego.divers.domain.PricingBasis
import com.wego.generated.jooq.tables.DiversBoatCharter.DIVERS_BOAT_CHARTER
import com.wego.generated.jooq.tables.DiversBoatCharterAuditEvent.DIVERS_BOAT_CHARTER_AUDIT_EVENT
import com.wego.generated.jooq.tables.DiversOfferingBoatCharter.DIVERS_OFFERING_BOAT_CHARTER
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
 * Proves the `SELECT ... FOR UPDATE` charter-row lock added to
 * `LinkOfferingToCharterService` and `UpdateBoatCharterService` closes the
 * real race independent Tier 1 review found: a link and a capacity
 * reduction racing, ending with a linked offering claiming more seats than
 * the charter is licensed for. Real invariant checked across many
 * independent trials: whenever a link persists, the charter's final
 * licensed capacity must still be at least the linked offering's capacity.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class BoatCharterCapacityConcurrencyIntegrationTest {
    @Autowired
    private lateinit var createOfferingService: CreateOfferingService

    @Autowired
    private lateinit var createBoatCharterService: CreateBoatCharterService

    @Autowired
    private lateinit var linkService: LinkOfferingToCharterService

    @Autowired
    private lateinit var updateCharterService: UpdateBoatCharterService

    @Autowired
    private lateinit var endCharterService: EndCharterService

    @Autowired
    private lateinit var dsl: DSLContext

    @Test
    fun `a capacity reduction never leaves a linked offering over the licensed capacity`() {
        val trials = 30
        val pairs =
            (1..trials).map { index ->
                val charter =
                    createBoatCharterService.create(
                        CreateBoatCharterCommand(
                            boatName = "Race Boat $index",
                            charterType = CharterType.DAILY,
                            licensedCapacity = 50,
                            startsOn = LocalDate.parse("2026-09-01"),
                            endsOn = null,
                            notes = null,
                            createdByUserId = null,
                            correlationId = null,
                        ),
                    )
                val offering =
                    createOfferingService.create(
                        CreateOfferingCommand(
                            offeringType = OfferingType.DIVE_TRIP,
                            title = "Race Trip $index",
                            description = null,
                            startsOn = LocalDate.parse("2026-09-01"),
                            endsOn = null,
                            capacity = 45,
                            pricingBasis = PricingBasis.PER_PARTICIPANT,
                            unitPrice = Money(BigDecimal("45.00"), "EUR"),
                            createdByUserId = null,
                            correlationId = null,
                        ),
                    )
                charter.id to offering.id
            }

        runConcurrently(
            pairs.flatMap { (charterId, offeringId) ->
                listOf(
                    {
                        linkService.link(LinkOfferingToCharterCommand(offeringId, charterId))
                        Unit
                    },
                    {
                        updateCharterService.update(
                            UpdateBoatCharterCommand(
                                charterId = charterId,
                                boatName = "Race Boat (reduced)",
                                licensedCapacity = 40,
                                startsOn = LocalDate.parse("2026-09-01"),
                                endsOn = null,
                                notes = null,
                            ),
                        )
                        Unit
                    },
                )
            },
        )

        for ((charterId, offeringId) in pairs) {
            val linkExists =
                dsl.fetchExists(
                    dsl
                        .selectFrom(DIVERS_OFFERING_BOAT_CHARTER)
                        .where(DIVERS_OFFERING_BOAT_CHARTER.OFFERING_ID.eq(offeringId.value))
                        .and(DIVERS_OFFERING_BOAT_CHARTER.BOAT_CHARTER_ID.eq(charterId.value)),
                )
            if (!linkExists) continue

            val finalCapacity =
                dsl
                    .select(DIVERS_BOAT_CHARTER.LICENSED_CAPACITY)
                    .from(DIVERS_BOAT_CHARTER)
                    .where(DIVERS_BOAT_CHARTER.ID.eq(charterId.value))
                    .fetchOne(0, Int::class.java)!!
            assertThat(finalCapacity)
                .withFailMessage("Charter $charterId ended with capacity $finalCapacity below its linked offering's 45-seat capacity")
                .isGreaterThanOrEqualTo(45)
        }
    }

    @Test
    fun `concurrent end attempts never both succeed`() {
        // Same structural class of bug independent Tier 1 review found in Diver archive (finding 3):
        // EndCharterService used an unlocked check-then-set before this fix. Proven the same way —
        // many concurrent end() calls against a fresh charter, asserting the audit trail never
        // disagrees with the persisted row.
        val trials = 20
        val charterIds =
            (1..trials).map { index ->
                createBoatCharterService
                    .create(
                        CreateBoatCharterCommand(
                            boatName = "End-Race Boat $index",
                            charterType = CharterType.DAILY,
                            licensedCapacity = 40,
                            startsOn = LocalDate.parse("2026-09-01"),
                            endsOn = null,
                            notes = null,
                            createdByUserId = null,
                            correlationId = null,
                        ),
                    ).id
            }
        val actorId = UUID.randomUUID()
        dsl
            .insertInto(IDENTITY_USER)
            .set(IDENTITY_USER.ID, actorId)
            .set(IDENTITY_USER.EMAIL, "end-charter-concurrency-test@example.com")
            .set(IDENTITY_USER.PASSWORD_HASH, "irrelevant-hash")
            .set(IDENTITY_USER.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .execute()

        runConcurrently(
            charterIds.flatMap { charterId ->
                (1..3).map {
                    {
                        endCharterService.end(charterId, actorId, null)
                        Unit
                    }
                }
            },
        )

        for (charterId in charterIds) {
            val endedEventCount =
                dsl
                    .selectCount()
                    .from(DIVERS_BOAT_CHARTER_AUDIT_EVENT)
                    .where(DIVERS_BOAT_CHARTER_AUDIT_EVENT.BOAT_CHARTER_ID.eq(charterId.value))
                    .and(DIVERS_BOAT_CHARTER_AUDIT_EVENT.EVENT_TYPE.eq("CHARTER_ENDED"))
                    .fetchOne(0, Int::class.java)
            val status =
                dsl
                    .select(DIVERS_BOAT_CHARTER.STATUS)
                    .from(DIVERS_BOAT_CHARTER)
                    .where(DIVERS_BOAT_CHARTER.ID.eq(charterId.value))
                    .fetchOne(0, String::class.java)
            assertThat(endedEventCount)
                .withFailMessage(
                    "Charter $charterId recorded $endedEventCount CHARTER_ENDED events from 3 concurrent end() calls — expected exactly 1",
                ).isEqualTo(1)
            assertThat(status).isEqualTo("ENDED")
        }
    }

    private fun runConcurrently(actions: List<() -> Any?>) {
        val pool = Executors.newFixedThreadPool(minOf(actions.size, 64))
        val ready = CountDownLatch(actions.size)
        val start = CountDownLatch(1)
        val done = CountDownLatch(actions.size)

        actions.forEach { action ->
            pool.submit {
                ready.countDown()
                start.await()
                try {
                    action()
                } catch (_: Exception) {
                    // A rejected outcome under real contention is expected.
                } finally {
                    done.countDown()
                }
            }
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue()
        start.countDown()
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue()
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
