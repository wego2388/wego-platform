package com.wego.divers

import com.wego.divers.application.CreateBoatCharterCommand
import com.wego.divers.application.CreateBoatCharterService
import com.wego.divers.application.CreateOfferingCommand
import com.wego.divers.application.CreateOfferingService
import com.wego.divers.application.LinkOfferingToCharterCommand
import com.wego.divers.application.LinkOfferingToCharterService
import com.wego.divers.application.UpdateBoatCharterCommand
import com.wego.divers.application.UpdateBoatCharterService
import com.wego.divers.domain.CharterType
import com.wego.divers.domain.Money
import com.wego.divers.domain.OfferingType
import com.wego.divers.domain.PricingBasis
import com.wego.generated.jooq.tables.DiversBoatCharter.DIVERS_BOAT_CHARTER
import com.wego.generated.jooq.tables.DiversOfferingBoatCharter.DIVERS_OFFERING_BOAT_CHARTER
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
