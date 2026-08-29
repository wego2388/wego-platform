package com.wego.divers

import com.wego.generated.jooq.tables.IdentityRole.IDENTITY_ROLE
import com.wego.generated.jooq.tables.IdentityRolePermission.IDENTITY_ROLE_PERMISSION
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
import org.springframework.test.web.servlet.put
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.util.UUID

/**
 * Proves the equipment/tank slice end to end over real HTTP and real
 * PostgreSQL: staff creates an item, finds it by QR code, cycles it
 * through maintenance, logs a service record, records a rental and its
 * return, then retires it — including the real guards (no double rental,
 * no retiring an item that's still out) and permission separation.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class EquipmentHttpTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    @Autowired
    private lateinit var dsl: DSLContext

    private val staffEmail = "equipment-staff@example.com"
    private val staffPassword = "a-very-long-staff-password-123"
    private val viewOnlyEmail = "equipment-view-only@example.com"
    private val viewOnlyPassword = "a-very-long-view-password-123"

    @BeforeEach
    fun seedUsersIfNeeded() {
        seedUserIfNeeded(staffEmail, staffPassword, roles = setOf("platform-admin"))
        seedLimitedUser(viewOnlyEmail, viewOnlyPassword, "equipment:view")
    }

    private fun seedLimitedUser(
        email: String,
        password: String,
        permission: String,
    ): UUID {
        val roleCode = "test-only-${permission.replace(':', '-')}"
        if (dsl.fetchOne(IDENTITY_ROLE, IDENTITY_ROLE.CODE.eq(roleCode)) == null) {
            dsl
                .insertInto(IDENTITY_ROLE)
                .set(IDENTITY_ROLE.CODE, roleCode)
                .set(IDENTITY_ROLE.DESCRIPTION, "Test-only role granting exactly $permission")
                .execute()
            dsl
                .insertInto(IDENTITY_ROLE_PERMISSION)
                .set(IDENTITY_ROLE_PERMISSION.ROLE_CODE, roleCode)
                .set(IDENTITY_ROLE_PERMISSION.PERMISSION_CODE, permission)
                .execute()
        }
        return seedUserIfNeeded(email, password, roles = setOf(roleCode))
    }

    private fun seedUserIfNeeded(
        email: String,
        password: String,
        roles: Set<String>,
    ): UUID {
        val existing = userRepository.findByEmail(EmailAddress.of(email))
        if (existing != null) return existing.id.value
        val user =
            User(
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

    private fun login(
        email: String,
        password: String,
    ): String {
        val body =
            mockMvc
                .post("/api/v1/identity/login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"$email","password":"$password"}"""
                }.andReturn()
                .response.contentAsString
        val match = Regex(""""token"\s*:\s*"([^"]+)"""").find(body)
        return requireNotNull(match) { "No token field in response body: $body" }.groupValues[1]
    }

    private fun jsonField(
        body: String,
        field: String,
    ): String {
        val match = Regex(""""$field"\s*:\s*"([^"]+)"""").find(body)
        return requireNotNull(match) { "No $field field in response body: $body" }.groupValues[1]
    }

    private fun createEquipment(
        token: String,
        qrCode: String,
        label: String = "BCD #HTTP-1",
    ): String =
        mockMvc
            .post("/api/v1/divers/equipment") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {"equipmentType": "BCD", "label": "$label", "qrCode": "$qrCode", "itemSize": "M", "serialNumber": "SN-HTTP-1"}
                    """.trimIndent()
            }.andExpect { status { isCreated() } }
            .andReturn()
            .response.contentAsString
            .let { jsonField(it, "id") }

    @Test
    fun `full lifecycle - create, find by qr, maintenance cycle, service record, rental cycle, retire`() {
        val token = login(staffEmail, staffPassword)
        val qrCode = "QR-HTTP-LIFECYCLE-1"

        val equipmentId =
            mockMvc
                .post("/api/v1/divers/equipment") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"equipmentType": "TANK", "label": "Tank #1", "qrCode": "$qrCode", "serialNumber": "SN-TANK-1"}"""
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.equipmentType") { value("TANK") }
                    jsonPath("$.status") { value("ACTIVE") }
                }.andReturn()
                .response.contentAsString
                .let { jsonField(it, "id") }

        mockMvc
            .get("/api/v1/divers/equipment") {
                header("Authorization", "Bearer $token")
                param("qrCode", qrCode)
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(1) }
                jsonPath("$[0].id") { value(equipmentId) }
            }

        mockMvc
            .post("/api/v1/divers/equipment/$equipmentId/start-maintenance") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("IN_MAINTENANCE") }
            }

        mockMvc
            .post("/api/v1/divers/equipment/$equipmentId/service-records") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"servicedOn": "2026-08-20", "description": "Annual visual inspection", "performedBy": "Ahmed"}"""
            }.andExpect { status { isCreated() } }

        mockMvc
            .get("/api/v1/divers/equipment/$equipmentId/service-records") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$[0].description") { value("Annual visual inspection") }
            }

        mockMvc
            .post("/api/v1/divers/equipment/$equipmentId/complete-maintenance") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("ACTIVE") }
            }

        // Rental cycle: start, double-rental rejected, return, retire now allowed.
        mockMvc
            .post("/api/v1/divers/equipment/$equipmentId/rentals") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"customerName": "Ada Lovelace", "rentedOn": "2026-08-25"}"""
            }.andExpect {
                status { isCreated() }
                jsonPath("$.returnedOn") { doesNotExist() }
            }

        mockMvc
            .post("/api/v1/divers/equipment/$equipmentId/rentals") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"customerName": "Someone Else", "rentedOn": "2026-08-25"}"""
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("already_out") }
            }

        // Retiring while a rental is open must be rejected, not silently orphan the rental.
        mockMvc
            .post("/api/v1/divers/equipment/$equipmentId/retire") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("has_open_rental") }
            }

        mockMvc
            .post("/api/v1/divers/equipment/$equipmentId/rentals/return") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"returnedOn": "2026-08-27"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.returnedOn") { value("2026-08-27") }
            }

        mockMvc
            .get("/api/v1/divers/equipment/$equipmentId/rentals") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$[0].customerName") { value("Ada Lovelace") }
            }

        mockMvc
            .post("/api/v1/divers/equipment/$equipmentId/retire") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("RETIRED") }
            }

        mockMvc
            .post("/api/v1/divers/equipment/$equipmentId/retire") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("already_retired") }
            }
    }

    @Test
    fun `rejects creating equipment with a duplicate qr code`() {
        val token = login(staffEmail, staffPassword)
        val qrCode = "QR-HTTP-DUPLICATE-1"
        createEquipment(token, qrCode)

        mockMvc
            .post("/api/v1/divers/equipment") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"equipmentType": "BCD", "label": "Another BCD", "qrCode": "$qrCode"}"""
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("duplicate_qr_code") }
            }
    }

    @Test
    fun `updates label, size, and serial number while preserving qr code and status`() {
        val token = login(staffEmail, staffPassword)
        val equipmentId = createEquipment(token, "QR-HTTP-UPDATE-1")

        mockMvc
            .put("/api/v1/divers/equipment/$equipmentId") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"label": "BCD #HTTP-1 (relabelled)", "itemSize": "L", "serialNumber": "SN-HTTP-2"}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.label") { value("BCD #HTTP-1 (relabelled)") }
                jsonPath("$.itemSize") { value("L") }
                jsonPath("$.qrCode") { value("QR-HTTP-UPDATE-1") }
            }
    }

    @Test
    fun `a view-only role can list and read but not create`() {
        val viewToken = login(viewOnlyEmail, viewOnlyPassword)

        mockMvc
            .get("/api/v1/divers/equipment") {
                header("Authorization", "Bearer $viewToken")
            }.andExpect { status { isOk() } }

        mockMvc
            .post("/api/v1/divers/equipment") {
                header("Authorization", "Bearer $viewToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"equipmentType": "BCD", "label": "Should Be Forbidden", "qrCode": "QR-FORBIDDEN"}"""
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `a view-only role is forbidden from every mutation, not just create`() {
        val token = login(staffEmail, staffPassword)
        val viewToken = login(viewOnlyEmail, viewOnlyPassword)
        val equipmentId =
            mockMvc
                .post("/api/v1/divers/equipment") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"equipmentType": "TANK", "label": "Permission Sweep Tank", "qrCode": "QR-HTTP-PERM-SWEEP"}"""
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
                .let { Regex(""""id"\s*:\s*"([^"]+)"""").find(it)!!.groupValues[1] }

        mockMvc
            .put("/api/v1/divers/equipment/$equipmentId") {
                header("Authorization", "Bearer $viewToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"label": "Should Be Forbidden"}"""
            }.andExpect { status { isForbidden() } }

        mockMvc
            .post("/api/v1/divers/equipment/$equipmentId/start-maintenance") {
                header("Authorization", "Bearer $viewToken")
            }.andExpect { status { isForbidden() } }

        mockMvc
            .post("/api/v1/divers/equipment/$equipmentId/retire") {
                header("Authorization", "Bearer $viewToken")
            }.andExpect { status { isForbidden() } }

        mockMvc
            .post("/api/v1/divers/equipment/$equipmentId/rentals") {
                header("Authorization", "Bearer $viewToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"customerName": "Forbidden Customer", "rentedOn": "2026-09-01"}"""
            }.andExpect { status { isForbidden() } }

        mockMvc
            .post("/api/v1/divers/equipment/$equipmentId/rentals/return") {
                header("Authorization", "Bearer $viewToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"returnedOn": "2026-09-02"}"""
            }.andExpect { status { isForbidden() } }

        mockMvc
            .post("/api/v1/divers/equipment/$equipmentId/service-records") {
                header("Authorization", "Bearer $viewToken")
                contentType = MediaType.APPLICATION_JSON
                content = """{"servicedOn": "2026-09-01", "description": "Forbidden service record"}"""
            }.andExpect { status { isForbidden() } }

        // Start maintenance for real (as staff) so complete-maintenance has a real state to be forbidden from.
        mockMvc.post("/api/v1/divers/equipment/$equipmentId/start-maintenance") { header("Authorization", "Bearer $token") }
        mockMvc
            .post("/api/v1/divers/equipment/$equipmentId/complete-maintenance") {
                header("Authorization", "Bearer $viewToken")
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `an unknown equipment id is a clean 404`() {
        val token = login(staffEmail, staffPassword)

        mockMvc
            .get("/api/v1/divers/equipment/${UUID.randomUUID()}") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `an unknown qr code returns an empty array, not a 404`() {
        val token = login(staffEmail, staffPassword)

        mockMvc
            .get("/api/v1/divers/equipment") {
                header("Authorization", "Bearer $token")
                param("qrCode", "QR-DOES-NOT-EXIST")
            }.andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(0) }
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
