package com.wego.toursoperator

import com.wego.generated.jooq.tables.IdentityRole.IDENTITY_ROLE
import com.wego.generated.jooq.tables.IdentityRolePermission.IDENTITY_ROLE_PERMISSION
import com.wego.generated.jooq.tables.ToursOperatorTour.TOURS_OPERATOR_TOUR
import com.wego.generated.jooq.tables.ToursOperatorTourSlot.TOURS_OPERATOR_TOUR_SLOT
import com.wego.identity.application.PasswordHasher
import com.wego.identity.application.UserRepository
import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.User
import com.wego.identity.domain.UserId
import com.wego.identity.domain.UserStatus
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

/**
 * C5 — Permission matrix tests for the catalog (tours + slots) staff API.
 *
 * Covers:
 *   1. Unauthenticated → 401 on every staff endpoint.
 *   2. Authenticated but missing permission → 403.
 *   3. tour:view can read staff endpoints but cannot mutate.
 *   4. tour:manage can mutate tours but NOT slots.
 *   5. slot:manage can mutate slots but NOT tours.
 *   6. platform-admin can do everything.
 *
 * The handoff noted that the no-permission create proof was present; this
 * file completes the full matrix for both tours and slots.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class TourCatalogPermissionMatrixTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var passwordHasher: PasswordHasher
    @Autowired private lateinit var dsl: DSLContext

    private val adminEmail    = "perm-admin@example.com"
    private val adminPassword = "perm-admin-very-long-password-123"
    private val noPermEmail   = "perm-noperm@example.com"
    private val noPermPassword = "perm-noperm-very-long-password-123"

    @BeforeEach
    fun seedUsers() {
        seedIfAbsent(adminEmail, adminPassword, roles = setOf("platform-admin"))
        seedIfAbsent(noPermEmail, noPermPassword, roles = emptySet())
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun seedIfAbsent(email: String, password: String, roles: Set<String>): UUID {
        val existing = userRepository.findByEmail(EmailAddress.of(email))
        if (existing != null) return existing.id.value
        val user = User(
            id = UserId.generate(),
            email = EmailAddress.of(email),
            passwordHash = passwordHasher.hash(password),
            status = UserStatus.ACTIVE,
            roles = roles.map(RoleCode::of).toSet(),
            createdAt = Instant.now(),
            failedLoginCount = 0,
            lockedUntil = null,
        )
        userRepository.save(user)
        return user.id.value
    }

    private fun seedLimitedUser(email: String, password: String, permission: String): UUID {
        val roleCode = "perm-test-${permission.replace(':', '-').replace('.', '-')}"
        if (dsl.fetchOne(IDENTITY_ROLE, IDENTITY_ROLE.CODE.eq(roleCode)) == null) {
            dsl.insertInto(IDENTITY_ROLE)
                .set(IDENTITY_ROLE.CODE, roleCode)
                .set(IDENTITY_ROLE.DESCRIPTION, "Test-only role for $permission")
                .execute()
            dsl.insertInto(IDENTITY_ROLE_PERMISSION)
                .set(IDENTITY_ROLE_PERMISSION.ROLE_CODE, roleCode)
                .set(IDENTITY_ROLE_PERMISSION.PERMISSION_CODE, permission)
                .execute()
        }
        return seedIfAbsent(email, password, roles = setOf(roleCode))
    }

    private fun login(email: String, password: String): String {
        val body = mockMvc.post("/api/v1/identity/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"email":"$email","password":"$password"}"""
        }.andReturn().response.contentAsString
        return requireNotNull(Regex(""""token"\s*:\s*"([^"]+)"""").find(body)) {
            "No token in login response: $body"
        }.groupValues[1]
    }

    private fun seedTour(): UUID {
        val id = UUID.randomUUID()
        dsl.insertInto(TOURS_OPERATOR_TOUR)
            .set(TOURS_OPERATOR_TOUR.ID, id)
            .set(TOURS_OPERATOR_TOUR.SLUG, "perm-tour-${id.toString().take(8)}")
            .set(TOURS_OPERATOR_TOUR.CATEGORY, "DESERT")
            .set(TOURS_OPERATOR_TOUR.DURATION_TEXT, "4 hours")
            .set(TOURS_OPERATOR_TOUR.PRICE_ADULT_CENTS, 3500L)
            .set(TOURS_OPERATOR_TOUR.CAPACITY, 10)
            .set(TOURS_OPERATOR_TOUR.AVAILABLE_TIME_SLOTS, "MORNING")
            .set(TOURS_OPERATOR_TOUR.SORT_ORDER, 1)
            .set(TOURS_OPERATOR_TOUR.IS_ACTIVE, true)
            .set(TOURS_OPERATOR_TOUR.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .execute()
        return id
    }

    private fun seedSlot(tourId: UUID): UUID {
        val id = UUID.randomUUID()
        dsl.insertInto(TOURS_OPERATOR_TOUR_SLOT)
            .set(TOURS_OPERATOR_TOUR_SLOT.ID, id)
            .set(TOURS_OPERATOR_TOUR_SLOT.TOUR_ID, tourId)
            .set(TOURS_OPERATOR_TOUR_SLOT.DATE, LocalDate.of(2029, 1, 10))
            .set(TOURS_OPERATOR_TOUR_SLOT.TIME_SLOT, "MORNING")
            .set(TOURS_OPERATOR_TOUR_SLOT.CAPACITY, 10)
            .set(TOURS_OPERATOR_TOUR_SLOT.BOOKED_COUNT, 0)
            .set(TOURS_OPERATOR_TOUR_SLOT.IS_BLOCKED, false)
            .set(TOURS_OPERATOR_TOUR_SLOT.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .execute()
        return id
    }

    private fun createTourPayload(slug: String = "perm-new-tour-${UUID.randomUUID().toString().take(8)}") = """
        {
          "slug": "$slug",
          "category": "DESERT",
          "durationText": "3 hours",
          "priceAdultCents": 2500,
          "capacity": 10,
          "availableTimeSlots": ["MORNING"],
          "sortOrder": 50
        }
    """.trimIndent()

    // ── 1. Unauthenticated → 401 on all staff endpoints ──────────────────────

    @Test
    fun `unauthenticated GET staff tours returns 401`() {
        mockMvc.get("/api/v1/tours-operator/staff/tours")
            .andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `unauthenticated POST staff tours returns 401`() {
        mockMvc.post("/api/v1/tours-operator/staff/tours") {
            contentType = MediaType.APPLICATION_JSON
            content = createTourPayload()
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `unauthenticated PATCH activate returns 401`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/v1/tours-operator/staff/tours/${UUID.randomUUID()}/activate")
        ).andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized
        )
    }

    @Test
    fun `unauthenticated POST staff slots returns 401`() {
        mockMvc.post("/api/v1/tours-operator/staff/tours/${UUID.randomUUID()}/slots") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"date":"2029-03-01","timeSlot":"MORNING","capacity":5}"""
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `unauthenticated PATCH block slot returns 401`() {
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/v1/tours-operator/staff/tours/${UUID.randomUUID()}/slots/${UUID.randomUUID()}/block")
        ).andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized
        )
    }

    // ── 2. Authenticated without any permission → 403 ────────────────────────

    @Test
    fun `authenticated without permission GET staff tours returns 403`() {
        val token = login(noPermEmail, noPermPassword)
        mockMvc.get("/api/v1/tours-operator/staff/tours") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `authenticated without permission POST staff tours returns 403`() {
        val token = login(noPermEmail, noPermPassword)
        mockMvc.post("/api/v1/tours-operator/staff/tours") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = createTourPayload()
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `authenticated without permission POST staff slots returns 403`() {
        val tourId = seedTour()
        val token = login(noPermEmail, noPermPassword)
        mockMvc.post("/api/v1/tours-operator/staff/tours/$tourId/slots") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = """{"date":"2029-03-01","timeSlot":"MORNING","capacity":5}"""
        }.andExpect { status { isForbidden() } }
    }

    // ── 3. tour:view can read but NOT mutate ──────────────────────────────────

    @Test
    fun `tour-view can list staff tours`() {
        seedLimitedUser("perm-view@example.com", "perm-view-long-password-456", "tours-operator.tour:view")
        val token = login("perm-view@example.com", "perm-view-long-password-456")
        mockMvc.get("/api/v1/tours-operator/staff/tours") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isOk() } }
    }

    @Test
    fun `tour-view cannot create a tour (403)`() {
        seedLimitedUser("perm-view2@example.com", "perm-view2-long-password-456", "tours-operator.tour:view")
        val token = login("perm-view2@example.com", "perm-view2-long-password-456")
        mockMvc.post("/api/v1/tours-operator/staff/tours") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = createTourPayload()
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `tour-view cannot activate a tour (403)`() {
        val tourId = seedTour()
        seedLimitedUser("perm-view3@example.com", "perm-view3-long-password-456", "tours-operator.tour:view")
        val token = login("perm-view3@example.com", "perm-view3-long-password-456")
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/v1/tours-operator/staff/tours/$tourId/activate")
                .header("Authorization", "Bearer $token")
        ).andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden
        )
    }

    @Test
    fun `tour-view cannot block a slot (403)`() {
        val tourId = seedTour()
        val slotId = seedSlot(tourId)
        seedLimitedUser("perm-view4@example.com", "perm-view4-long-password-456", "tours-operator.tour:view")
        val token = login("perm-view4@example.com", "perm-view4-long-password-456")
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/v1/tours-operator/staff/tours/$tourId/slots/$slotId/block")
                .header("Authorization", "Bearer $token")
        ).andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden
        )
    }

    // ── 4. tour:manage can mutate tours but NOT slots ─────────────────────────

    @Test
    fun `tour-manage can create a tour`() {
        seedLimitedUser("perm-manage@example.com", "perm-manage-long-password-456", "tours-operator.tour:manage")
        val token = login("perm-manage@example.com", "perm-manage-long-password-456")
        mockMvc.post("/api/v1/tours-operator/staff/tours") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = createTourPayload()
        }.andExpect { status { isCreated() } }
    }

    @Test
    fun `tour-manage cannot block a slot (403)`() {
        val tourId = seedTour()
        val slotId = seedSlot(tourId)
        seedLimitedUser("perm-manage2@example.com", "perm-manage2-long-password-456", "tours-operator.tour:manage")
        val token = login("perm-manage2@example.com", "perm-manage2-long-password-456")
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/v1/tours-operator/staff/tours/$tourId/slots/$slotId/block")
                .header("Authorization", "Bearer $token")
        ).andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden
        )
    }

    // ── 5. slot:manage can mutate slots but NOT tours ─────────────────────────

    @Test
    fun `slot-manage can block a slot`() {
        val tourId = seedTour()
        val slotId = seedSlot(tourId)
        seedLimitedUser("perm-slot@example.com", "perm-slot-long-password-456", "tours-operator.slot:manage")
        val token = login("perm-slot@example.com", "perm-slot-long-password-456")
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/v1/tours-operator/staff/tours/$tourId/slots/$slotId/block")
                .header("Authorization", "Bearer $token")
        ).andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNoContent
        )
    }

    @Test
    fun `slot-manage cannot create a tour (403)`() {
        seedLimitedUser("perm-slot2@example.com", "perm-slot2-long-password-456", "tours-operator.slot:manage")
        val token = login("perm-slot2@example.com", "perm-slot2-long-password-456")
        mockMvc.post("/api/v1/tours-operator/staff/tours") {
            header("Authorization", "Bearer $token")
            contentType = MediaType.APPLICATION_JSON
            content = createTourPayload()
        }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `slot-manage cannot activate a tour (403)`() {
        val tourId = seedTour()
        seedLimitedUser("perm-slot3@example.com", "perm-slot3-long-password-456", "tours-operator.slot:manage")
        val token = login("perm-slot3@example.com", "perm-slot3-long-password-456")
        mockMvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/v1/tours-operator/staff/tours/$tourId/activate")
                .header("Authorization", "Bearer $token")
        ).andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden
        )
    }

    // ── Testcontainers setup ──────────────────────────────────────────────────

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("wego_test_perms")
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
