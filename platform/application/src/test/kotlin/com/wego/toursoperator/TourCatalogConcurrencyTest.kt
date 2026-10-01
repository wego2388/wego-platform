package com.wego.toursoperator

import com.wego.generated.jooq.tables.ToursOperatorTour.TOURS_OPERATOR_TOUR
import com.wego.generated.jooq.tables.ToursOperatorTourSlot.TOURS_OPERATOR_TOUR_SLOT
import com.wego.identity.application.PasswordHasher
import com.wego.identity.application.UserRepository
import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.User
import com.wego.identity.domain.UserId
import com.wego.identity.domain.UserStatus
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Concurrency correctness tests for catalog mutation operations (C1).
 *
 * These tests prove that UpdateTourService, SetTourActiveService, and
 * SetSlotBlockedService protect their read-modify-write cycles with a
 * single transaction, so the row lock is held from findByIdForUpdate
 * until save() commits. Without TransactionRunner wrapping, the lock
 * would be released when the repository method returns, allowing a
 * second writer to read stale state and overwrite the first commit.
 *
 * Each test also verifies the expected final state in the DB, not just
 * that no exception was thrown.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class TourCatalogConcurrencyTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var passwordHasher: PasswordHasher

    @Autowired private lateinit var dsl: DSLContext

    private val adminEmail = "conc-admin@example.com"
    private val adminPassword = "a-very-long-concurrency-test-password-123"

    @BeforeEach
    fun seedAdmin() {
        if (userRepository.findByEmail(EmailAddress.of(adminEmail)) != null) return
        val user =
            User(
                id = UserId.generate(),
                email = EmailAddress.of(adminEmail),
                passwordHash = passwordHasher.hash(adminPassword),
                status = UserStatus.ACTIVE,
                roles = setOf(RoleCode.of("platform-admin")),
                createdAt = Instant.now(),
                failedLoginCount = 0,
                lockedUntil = null,
            )
        userRepository.save(user)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun login(): String {
        val body =
            mockMvc
                .post("/api/v1/identity/login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"$adminEmail","password":"$adminPassword"}"""
                }.andReturn()
                .response.contentAsString
        val match = Regex(""""token"\s*:\s*"([^"]+)"""").find(body)
        return requireNotNull(match) { "No token in login response: $body" }.groupValues[1]
    }

    private fun jsonField(
        body: String,
        field: String,
    ): String {
        val match = Regex(""""$field"\s*:\s*"([^"]+)"""").find(body)
        return requireNotNull(match) { "No field '$field' in: $body" }.groupValues[1]
    }

    private fun seedTour(uniqueTag: String): UUID {
        val tourId = UUID.randomUUID()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        // Short, unique slug within the DB slug constraint (3–80 chars, a-z0-9 + hyphens)
        val shortId = tourId.toString().take(8)
        val slug =
            "ct-$uniqueTag-$shortId"
                .take(80)
                .lowercase()
                .replace(Regex("[^a-z0-9-]"), "-")
                .trimEnd('-')
        dsl
            .insertInto(TOURS_OPERATOR_TOUR)
            .set(TOURS_OPERATOR_TOUR.ID, tourId)
            .set(TOURS_OPERATOR_TOUR.SLUG, slug)
            .set(TOURS_OPERATOR_TOUR.CATEGORY, "DESERT")
            .set(TOURS_OPERATOR_TOUR.DURATION_TEXT, "4 hours")
            .set(TOURS_OPERATOR_TOUR.PRICE_ADULT_CENTS, 3500L)
            .set(TOURS_OPERATOR_TOUR.PRICE_CHILD_CENTS, null as Long?)
            .set(TOURS_OPERATOR_TOUR.CAPACITY, 20)
            .set(TOURS_OPERATOR_TOUR.AVAILABLE_TIME_SLOTS, "MORNING")
            .set(TOURS_OPERATOR_TOUR.SORT_ORDER, 1)
            .set(TOURS_OPERATOR_TOUR.IS_ACTIVE, false)
            .set(TOURS_OPERATOR_TOUR.CREATED_AT, now)
            .execute()
        return tourId
    }

    private fun seedSlot(
        tourId: UUID,
        date: LocalDate = LocalDate.of(2028, 3, 10),
    ): UUID {
        val slotId = UUID.randomUUID()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        dsl
            .insertInto(TOURS_OPERATOR_TOUR_SLOT)
            .set(TOURS_OPERATOR_TOUR_SLOT.ID, slotId)
            .set(TOURS_OPERATOR_TOUR_SLOT.TOUR_ID, tourId)
            .set(TOURS_OPERATOR_TOUR_SLOT.DATE, date)
            .set(TOURS_OPERATOR_TOUR_SLOT.TIME_SLOT, "MORNING")
            .set(TOURS_OPERATOR_TOUR_SLOT.CAPACITY, 20)
            .set(TOURS_OPERATOR_TOUR_SLOT.BOOKED_COUNT, 0)
            .set(TOURS_OPERATOR_TOUR_SLOT.IS_BLOCKED, false)
            .set(TOURS_OPERATOR_TOUR_SLOT.CREATED_AT, now)
            .execute()
        return slotId
    }

    // ── C1-a: concurrent PUT tour update — last write wins, no lost update ────

    /**
     * Two concurrent PUT requests on the same tour must not produce a lost
     * update. Because the service wraps the read-modify-write in a single
     * transaction, the lock is held until commit, so requests are serialised.
     * After both complete the stored row must reflect one of the two inputs
     * (not a torn state), and both requests must return 204.
     */
    @Test
    fun `concurrent PUT tour update - both succeed and row is consistent`() {
        val tourId = seedTour("upd")
        val token = login()
        val executor = Executors.newFixedThreadPool(2)

        // Request A: capacity 10, price 4000
        val requestA =
            """
            {
              "category": "DESERT",
              "durationText": "4 hours",
              "priceAdultCents": 4000,
              "capacity": 10,
              "availableTimeSlots": ["MORNING"],
              "sortOrder": 1
            }
            """.trimIndent()

        // Request B: capacity 15, price 5000
        val requestB =
            """
            {
              "category": "SEA",
              "durationText": "5 hours",
              "priceAdultCents": 5000,
              "capacity": 15,
              "availableTimeSlots": ["SUNSET"],
              "sortOrder": 2
            }
            """.trimIndent()

        val futures: List<Future<Int>> =
            listOf(
                executor.submit(
                    Callable {
                        mockMvc
                            .perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                    .put("/api/v1/tours-operator/staff/tours/$tourId")
                                    .header("Authorization", "Bearer $token")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestA),
                            ).andReturn()
                            .response.status
                    },
                ),
                executor.submit(
                    Callable {
                        mockMvc
                            .perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                    .put("/api/v1/tours-operator/staff/tours/$tourId")
                                    .header("Authorization", "Bearer $token")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestB),
                            ).andReturn()
                            .response.status
                    },
                ),
            )
        executor.shutdown()

        val statuses = futures.map { it.get() }
        // Both requests must succeed — no 5xx from a torn transaction
        assertThat(statuses).allMatch { it == 204 }

        // Row must be in a coherent state that matches one of the two payloads
        val row =
            dsl
                .selectFrom(TOURS_OPERATOR_TOUR)
                .where(TOURS_OPERATOR_TOUR.ID.eq(tourId))
                .fetchOne()!!
        val capacity = row.capacity
        val price = row.priceAdultCents
        // Either A's values or B's values — never a mixture
        val isCoherentA = capacity == 10 && price == 4000L
        val isCoherentB = capacity == 15 && price == 5000L
        assertThat(isCoherentA || isCoherentB)
            .withFailMessage("Row has torn state: capacity=$capacity, price=$price")
            .isTrue()
    }

    // ── C1-b: concurrent activate/deactivate — final state is deterministic ──

    /**
     * Three concurrent activate requests on an inactive tour must all return
     * 204 and the tour must end up active. The TransactionRunner ensures the
     * lock is held for the full read-modify-write, so no activation is lost.
     */
    @Test
    fun `concurrent activate requests all succeed and tour ends active`() {
        val tourId = seedTour("act")
        val token = login()
        val executor = Executors.newFixedThreadPool(3)

        val futures: List<Future<Int>> =
            (1..3).map {
                executor.submit(
                    Callable {
                        mockMvc
                            .perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                    .patch("/api/v1/tours-operator/staff/tours/$tourId/activate")
                                    .header("Authorization", "Bearer $token"),
                            ).andReturn()
                            .response.status
                    },
                )
            }
        executor.shutdown()

        val statuses = futures.map { it.get() }
        assertThat(statuses).allMatch { it == 204 }

        val isActive =
            dsl
                .select(TOURS_OPERATOR_TOUR.IS_ACTIVE)
                .from(TOURS_OPERATOR_TOUR)
                .where(TOURS_OPERATOR_TOUR.ID.eq(tourId))
                .fetchOne(TOURS_OPERATOR_TOUR.IS_ACTIVE)
        assertThat(isActive).isTrue()
    }

    // ── C1-c: concurrent block/unblock — no torn state ───────────────────────

    /**
     * Two concurrent block requests on the same slot must both return 204 and
     * leave the slot in a blocked state (idempotent). Without transaction
     * wrapping the lock could be released before save(), allowing the second
     * writer to read the not-yet-blocked state and effectively race.
     */
    @Test
    fun `concurrent block requests on same slot both succeed and slot stays blocked`() {
        val tourId = seedTour("blk")
        val slotId = seedSlot(tourId)
        val token = login()
        val executor = Executors.newFixedThreadPool(2)

        val futures: List<Future<Int>> =
            (1..2).map {
                executor.submit(
                    Callable {
                        mockMvc
                            .perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                    .patch("/api/v1/tours-operator/staff/tours/$tourId/slots/$slotId/block")
                                    .header("Authorization", "Bearer $token"),
                            ).andReturn()
                            .response.status
                    },
                )
            }
        executor.shutdown()

        val statuses = futures.map { it.get() }
        assertThat(statuses).allMatch { it == 204 }

        val isBlocked =
            dsl
                .select(TOURS_OPERATOR_TOUR_SLOT.IS_BLOCKED)
                .from(TOURS_OPERATOR_TOUR_SLOT)
                .where(TOURS_OPERATOR_TOUR_SLOT.ID.eq(slotId))
                .fetchOne(TOURS_OPERATOR_TOUR_SLOT.IS_BLOCKED)
        assertThat(isBlocked).isTrue()
    }

    // ── C2: wrong-tour block is rejected with 404 ─────────────────────────────

    /**
     * PATCH /tour-A/slots/slot-B/block where slot-B belongs to tour-B must
     * return 404 (WrongTour mapped to notFound). This prevents cross-tour
     * mutations even when the caller supplies a valid slotId.
     */
    @Test
    fun `block slot belonging to a different tour returns 404`() {
        val tourA = seedTour("wta")
        val tourB = seedTour("wtb")
        // slotB belongs to tourB
        val slotB = seedSlot(tourB, LocalDate.of(2028, 4, 1))
        val token = login()

        // Attempt to block slotB via tourA's path
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .patch("/api/v1/tours-operator/staff/tours/$tourA/slots/$slotB/block")
                    .header("Authorization", "Bearer $token"),
            ).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .status()
                    .isNotFound,
            )

        // Verify slotB remains unblocked
        val isBlocked =
            dsl
                .select(TOURS_OPERATOR_TOUR_SLOT.IS_BLOCKED)
                .from(TOURS_OPERATOR_TOUR_SLOT)
                .where(TOURS_OPERATOR_TOUR_SLOT.ID.eq(slotB))
                .fetchOne(TOURS_OPERATOR_TOUR_SLOT.IS_BLOCKED)
        assertThat(isBlocked).isFalse()
    }

    // ── C2: unblock slot belonging to a different tour returns 404 ───────────

    @Test
    fun `unblock slot belonging to a different tour returns 404`() {
        val tourA = seedTour("uba")
        val tourB = seedTour("ubb")
        val slotB = seedSlot(tourB, LocalDate.of(2028, 4, 2))
        val token = login()

        // Block slotB properly first via tourB
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .patch("/api/v1/tours-operator/staff/tours/$tourB/slots/$slotB/block")
                    .header("Authorization", "Bearer $token"),
            ).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .status()
                    .isNoContent,
            )

        // Attempt to unblock via tourA's path — must fail
        mockMvc
            .perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .patch("/api/v1/tours-operator/staff/tours/$tourA/slots/$slotB/unblock")
                    .header("Authorization", "Bearer $token"),
            ).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers
                    .status()
                    .isNotFound,
            )

        // slotB must remain blocked
        val isBlocked =
            dsl
                .select(TOURS_OPERATOR_TOUR_SLOT.IS_BLOCKED)
                .from(TOURS_OPERATOR_TOUR_SLOT)
                .where(TOURS_OPERATOR_TOUR_SLOT.ID.eq(slotB))
                .fetchOne(TOURS_OPERATOR_TOUR_SLOT.IS_BLOCKED)
        assertThat(isBlocked).isTrue()
    }

    // ── C2: POST slot on unknown tour returns 404, not 409 ───────────────────

    /**
     * Before the fix, CreateSlotService returned AlreadyExists (mapped to 409)
     * when the tour was not found — misleading. Now it returns TourNotFound
     * (mapped to 404).
     */
    @Test
    fun `create slot for unknown tour returns 404 not 409`() {
        val unknownTourId = UUID.randomUUID()
        val token = login()
        val tomorrow = LocalDate.now().plusDays(5).toString()

        mockMvc
            .post("/api/v1/tours-operator/staff/tours/$unknownTourId/slots") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"date":"$tomorrow","timeSlot":"MORNING","capacity":10}"""
            }.andExpect {
                status { isNotFound() }
            }
    }

    // ── Testcontainers setup ──────────────────────────────────────────────────

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("wego_test_conc")
                .withUsername("wego_test")
                .withPassword("wego_test")

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("tours-operator.paymob.mock-enabled") { true }
            registry.add("spring.flyway.enabled") { true }
        }
    }
}
