package com.wego.divers

import com.wego.divers.application.BookingRepository
import com.wego.divers.application.CreateBookingCommand
import com.wego.divers.application.CreateBookingResult
import com.wego.divers.application.CreateBookingService
import com.wego.divers.application.CreateOfferingCommand
import com.wego.divers.application.CreateOfferingService
import com.wego.divers.application.OfferingRepository
import com.wego.divers.application.TransactionRunner
import com.wego.divers.domain.Booking
import com.wego.divers.domain.BookingFingerprint
import com.wego.divers.domain.BookingId
import com.wego.divers.domain.BookingPricing
import com.wego.divers.domain.CustomerContact
import com.wego.divers.domain.Money
import com.wego.divers.domain.OfferingType
import com.wego.divers.domain.PricingBasis
import com.wego.generated.jooq.tables.DiversBooking.DIVERS_BOOKING
import com.wego.generated.jooq.tables.DiversBookingAuditEvent.DIVERS_BOOKING_AUDIT_EVENT
import com.wego.generated.jooq.tables.IdentityUser.IDENTITY_USER
import com.wego.generated.jooq.tables.IntegrationOutbox.INTEGRATION_OUTBOX
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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
 * Proves the `pg_advisory_xact_lock` in `JooqBookingRepository.lockIdempotencyKey`
 * — not the offering row lock, which cannot cover this case — is what
 * makes the same actor+key reused *concurrently* against two *different*
 * offerings resolve deterministically: exactly one `Created`, exactly one
 * `IdempotencyKeyConflict`, and never a raw unique-constraint 500. Also
 * proves a failure between the booking write and commit leaves zero
 * partial state — no orphaned booking, audit, or outbox row.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
class IdempotencyKeyConcurrencyIntegrationTest {
    @Autowired
    private lateinit var createOfferingService: CreateOfferingService

    @Autowired
    private lateinit var createBookingService: CreateBookingService

    @Autowired
    private lateinit var bookingRepository: BookingRepository

    @Autowired
    private lateinit var offeringRepository: OfferingRepository

    @Autowired
    private lateinit var transactionRunner: TransactionRunner

    @Autowired
    private lateinit var dsl: DSLContext

    @Test
    fun `the same actor and key reused concurrently across two different offerings yields one Created and one Conflict, never a 500`() {
        val firstOffering =
            createOfferingService.create(
                CreateOfferingCommand(
                    offeringType = OfferingType.DIVE_TRIP,
                    title = "Idempotency Concurrency Trip A",
                    description = null,
                    startsOn = LocalDate.parse("2026-09-10"),
                    endsOn = null,
                    capacity = null,
                    pricingBasis = PricingBasis.PER_PARTICIPANT,
                    unitPrice = Money(BigDecimal("45.00"), "EUR"),
                    createdByUserId = null,
                    correlationId = null,
                ),
            )
        val secondOffering =
            createOfferingService.create(
                CreateOfferingCommand(
                    offeringType = OfferingType.DIVE_TRIP,
                    title = "Idempotency Concurrency Trip B",
                    description = null,
                    startsOn = LocalDate.parse("2026-09-11"),
                    endsOn = null,
                    capacity = null,
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
            .set(IDENTITY_USER.EMAIL, "idempotency-concurrency-test@example.com")
            .set(IDENTITY_USER.PASSWORD_HASH, "irrelevant-hash")
            .set(IDENTITY_USER.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .execute()

        val sharedKey = "shared-idempotency-key-across-offerings"
        val attempts = 10

        val results =
            runConcurrently(attempts) { index ->
                val offeringId = if (index % 2 == 0) firstOffering.id else secondOffering.id
                runCatching {
                    createBookingService.create(
                        CreateBookingCommand(
                            offeringId = offeringId,
                            partySize = 1,
                            customer = CustomerContact("Shared Key Customer", "shared-key-customer@example.com", null),
                            idempotencyKey = sharedKey,
                            createdByUserId = actorId,
                            correlationId = null,
                        ),
                    )
                }
            }

        val unexpectedExceptions = results.mapNotNull { it.exceptionOrNull() }
        assertThat(unexpectedExceptions).isEmpty()

        // Every request shares the same actor+key, but only half share a
        // canonical fingerprint with whichever offering wins the race (same
        // offeringId + partySize + customer): those collapse into one
        // Created plus (attempts/2 - 1) genuine idempotent Replayed results
        // for that same offering. The other half — same key, but a
        // different offeringId, so a different fingerprint — must all be
        // rejected as IdempotencyKeyConflict, never silently applied and
        // never a raw 500.
        val outcomes = results.map { it.getOrThrow() }
        val created = outcomes.filterIsInstance<CreateBookingResult.Created>()
        val replayed = outcomes.filterIsInstance<CreateBookingResult.Replayed>()
        val conflicts = outcomes.count { it == CreateBookingResult.IdempotencyKeyConflict }
        assertThat(created).hasSize(1)
        assertThat(replayed).hasSize(attempts / 2 - 1)
        assertThat(conflicts).isEqualTo(attempts / 2)
        assertThat(replayed.map { it.booking.id }.distinct()).containsExactly(created.single().booking.id)

        val bookingRowCount =
            dsl
                .selectCount()
                .from(DIVERS_BOOKING)
                .where(DIVERS_BOOKING.CREATED_BY_USER_ID.eq(actorId))
                .and(DIVERS_BOOKING.IDEMPOTENCY_KEY.eq(sharedKey))
                .fetchOne(0, Int::class.java)
        assertThat(bookingRowCount).isEqualTo(1)
    }

    @Test
    fun `a failure after the booking write but before commit leaves no partial state — no booking, audit, or outbox row`() {
        val offering =
            createOfferingService.create(
                CreateOfferingCommand(
                    offeringType = OfferingType.DIVE_TRIP,
                    title = "Idempotency Rollback Trip",
                    description = null,
                    startsOn = LocalDate.parse("2026-09-12"),
                    endsOn = null,
                    capacity = null,
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
            .set(IDENTITY_USER.EMAIL, "idempotency-rollback-test@example.com")
            .set(IDENTITY_USER.PASSWORD_HASH, "irrelevant-hash")
            .set(IDENTITY_USER.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .execute()

        val bookingId = BookingId.generate()
        val idempotencyKey = "rollback-test-key"
        val customer = CustomerContact("Rollback Customer", "rollback-customer@example.com", null)
        val fingerprint = BookingFingerprint.of(offering.id, 1, customer)

        assertThatThrownBy {
            transactionRunner.runInTransaction<Unit> {
                bookingRepository.lockIdempotencyKey(actorId, idempotencyKey)
                val locked =
                    offeringRepository.findByIdForUpdate(offering.id)
                        ?: error("offering must exist")
                val booking =
                    Booking.confirm(
                        id = bookingId,
                        offeringId = locked.id,
                        partySize = 1,
                        customer = customer,
                        pricing = BookingPricing.forOffering(locked, 1),
                        idempotencyKey = idempotencyKey,
                        idempotencyFingerprint = fingerprint,
                        createdByUserId = actorId,
                        now = java.time.Instant.now(),
                    )
                bookingRepository.save(booking)
                throw RuntimeException("Simulated failure after booking write, before commit")
            }
        }.isInstanceOf(RuntimeException::class.java)

        val bookingRowCount =
            dsl
                .selectCount()
                .from(DIVERS_BOOKING)
                .where(DIVERS_BOOKING.ID.eq(bookingId.value))
                .fetchOne(0, Int::class.java)
        val auditRowCount =
            dsl
                .selectCount()
                .from(DIVERS_BOOKING_AUDIT_EVENT)
                .where(DIVERS_BOOKING_AUDIT_EVENT.BOOKING_ID.eq(bookingId.value))
                .fetchOne(0, Int::class.java)
        val outboxRowCount =
            dsl
                .selectCount()
                .from(INTEGRATION_OUTBOX)
                .where(INTEGRATION_OUTBOX.AGGREGATE_ID.eq(bookingId.value.toString()))
                .fetchOne(0, Int::class.java)

        assertThat(bookingRowCount).isEqualTo(0)
        assertThat(auditRowCount).isEqualTo(0)
        assertThat(outboxRowCount).isEqualTo(0)
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
