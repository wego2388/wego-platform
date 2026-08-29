package com.wego.divers

import com.wego.divers.application.CreateBookingCommand
import com.wego.divers.application.CreateBookingResult
import com.wego.divers.application.CreateBookingService
import com.wego.divers.application.CreateOfferingCommand
import com.wego.divers.application.CreateOfferingService
import com.wego.divers.domain.BookingStatus
import com.wego.divers.domain.CustomerContact
import com.wego.divers.domain.Money
import com.wego.divers.domain.OfferingType
import com.wego.divers.domain.PricingBasis
import com.wego.generated.jooq.tables.DiversBooking.DIVERS_BOOKING
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
 * Proves the `SELECT ... FOR UPDATE` row lock in
 * `JooqOfferingRepository.findByIdForUpdate` actually prevents overselling
 * under real concurrency — the Tier 1 invariant of this packet. An offering
 * with `capacity = 5` receives 10 concurrent booking attempts of
 * `partySize = 1` each, each with its own idempotency key (this proves the
 * capacity check itself, not idempotent-replay collapsing); exactly 5 must
 * succeed and 5 must be rejected as `CAPACITY_EXCEEDED` — no fewer (lost
 * capacity) and no more (oversold).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class BookingCapacityConcurrencyIntegrationTest {
    @Autowired
    private lateinit var createOfferingService: CreateOfferingService

    @Autowired
    private lateinit var createBookingService: CreateBookingService

    @Autowired
    private lateinit var dsl: DSLContext

    @Test
    fun `exactly capacity bookings succeed under concurrent creation attempts against the same offering`() {
        val offering =
            createOfferingService.create(
                CreateOfferingCommand(
                    offeringType = OfferingType.DIVE_TRIP,
                    title = "Concurrency Test Trip",
                    description = null,
                    startsOn = LocalDate.parse("2026-09-01"),
                    endsOn = null,
                    capacity = 5,
                    pricingBasis = PricingBasis.PER_PARTICIPANT,
                    unitPrice = Money(BigDecimal("45.00"), "EUR"),
                    createdByUserId = null,
                    correlationId = null,
                ),
            )
        val actorId = UUID.randomUUID()
        dsl
            .insertInto(IDENTITY_USER)
            .set(IDENTITY_USER.ID, actorId)
            .set(IDENTITY_USER.EMAIL, "capacity-concurrency-test@example.com")
            .set(IDENTITY_USER.PASSWORD_HASH, "irrelevant-hash")
            .set(IDENTITY_USER.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .execute()
        val attempts = 10

        val results =
            runConcurrently(attempts) { index ->
                createBookingService.create(
                    CreateBookingCommand(
                        offeringId = offering.id,
                        partySize = 1,
                        customer = CustomerContact("Customer $index", "customer$index@example.com", null),
                        idempotencyKey = "concurrency-key-$index",
                        createdByUserId = actorId,
                        correlationId = null,
                    ),
                )
            }

        val created = results.count { it is CreateBookingResult.Created }
        val capacityExceeded = results.count { it is CreateBookingResult.CapacityExceeded }
        assertThat(created).isEqualTo(5)
        assertThat(capacityExceeded).isEqualTo(5)

        val confirmedRowCount =
            dsl
                .selectCount()
                .from(DIVERS_BOOKING)
                .where(DIVERS_BOOKING.OFFERING_ID.eq(offering.id.value))
                .and(DIVERS_BOOKING.STATUS.eq(BookingStatus.CONFIRMED.name))
                .fetchOne(0, Int::class.java)
        assertThat(confirmedRowCount).isEqualTo(5)
    }

    private fun <T> runConcurrently(
        count: Int,
        action: (Int) -> T,
    ): List<T> {
        val pool = Executors.newFixedThreadPool(count)
        val ready = CountDownLatch(count)
        val start = CountDownLatch(1)
        val done = CountDownLatch(count)
        val results = java.util.Collections.synchronizedList(mutableListOf<T>())

        repeat(count) { index ->
            pool.submit {
                ready.countDown()
                start.await()
                try {
                    results += action(index)
                } finally {
                    done.countDown()
                }
            }
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue()
        start.countDown()
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue()
        pool.shutdown()
        return results
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
