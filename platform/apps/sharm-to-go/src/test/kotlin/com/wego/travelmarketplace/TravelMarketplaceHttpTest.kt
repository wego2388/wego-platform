package com.wego.travelmarketplace

import com.wego.generated.jooq.tables.IdentityRole.IDENTITY_ROLE
import com.wego.generated.jooq.tables.IdentityRolePermission.IDENTITY_ROLE_PERMISSION
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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.util.UUID

/**
 * Proves the Packet 1A catalog slice end to end over real HTTP and real
 * PostgreSQL — category/provider/service CRUD, the full
 * `DRAFT -> REVIEW -> APPROVED -> PUBLISHED` lifecycle (plus suspend/
 * republish/archive), the `SERVICE_CONTENT_TEMPLATE.md` publish gate, staff
 * permission separation, and the unauthenticated public catalog's narrower
 * shape — matching `DiverHttpTest`'s standard for WEGO-011.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class TravelMarketplaceHttpTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var passwordHasher: PasswordHasher

    @Autowired
    private lateinit var dsl: DSLContext

    private val staffEmail = "marketplace-staff@example.com"
    private val staffPassword = "a-very-long-staff-password-123"
    private val viewOnlyEmail = "marketplace-view-only@example.com"
    private val viewOnlyPassword = "a-very-long-view-password-123"
    private val noPermissionEmail = "marketplace-no-permissions@example.com"
    private val noPermissionPassword = "a-very-long-plain-password-123"

    @BeforeEach
    fun seedUsersIfNeeded() {
        seedUserIfNeeded(staffEmail, staffPassword, roles = setOf("platform-admin"))
        seedLimitedUser(viewOnlyEmail, viewOnlyPassword, "service:view")
        seedUserIfNeeded(noPermissionEmail, noPermissionPassword, roles = emptySet())
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

    private fun jsonIntField(
        body: String,
        field: String,
    ): Int {
        val match = Regex(""""$field"\s*:\s*(\d+)""").find(body)
        return requireNotNull(match) { "No $field field in response body: $body" }.groupValues[1].toInt()
    }

    private fun createCategory(
        token: String,
        code: String = "sea-adventures-${UUID.randomUUID().toString().take(8)}",
    ): String {
        val body =
            mockMvc
                .post("/api/v1/travel-marketplace/categories") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
                        {
                          "code": "$code",
                          "name": {"en": "Sea adventures", "ar": "مغامرات بحرية"},
                          "description": null,
                          "displayOrder": 0
                        }
                        """.trimIndent()
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        return jsonField(body, "id")
    }

    private fun createProvider(token: String): String {
        val body =
            mockMvc
                .post("/api/v1/travel-marketplace/providers") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"name": "Blue Horizon Diving", "contactEmail": "ops@example.com", "contactPhone": null}"""
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        return jsonField(body, "id")
    }

    private fun createServiceRequest(
        categoryId: String,
        name: String = "Desert Safari",
        fulfilmentModel: String = "DIRECT",
        providerId: String? = null,
        withOptions: Boolean = true,
        withMedia: Boolean = true,
        expectedVersion: Int? = null,
    ): String {
        val providerJson = if (providerId != null) "\"$providerId\"" else "null"
        val optionsJson =
            if (withOptions) {
                """[{"id": null, "label": {"en": "2-hour", "ar": "ساعتين"}, "durationMinutes": 120, "maxParticipants": 10, "priceAmount": 50.00, "priceCurrency": "EGP", "priceBasis": "PER_PERSON"}]"""
            } else {
                "[]"
            }
        val mediaJson =
            if (withMedia) {
                """[{"id": null, "assetReference": "asset-001", "rightsEvidence": "Owner-supplied, rights confirmed 2026-08-01", "locale": "en"}]"""
            } else {
                "[]"
            }
        return """
            {
              "categoryId": "$categoryId",
              "name": {"en": "$name", "ar": "رحلة سفاري"},
              "description": {"en": "An evening desert safari.", "ar": "رحلة سفاري مسائية."},
              "fulfilmentModel": "$fulfilmentModel",
              "providerId": $providerJson,
              "confirmationType": "INSTANT",
              "cancellationPolicy": {"en": "Free cancellation 24h ahead.", "ar": "إلغاء مجاني قبل 24 ساعة."},
              "pickupInfo": null,
              "inclusions": null,
              "exclusions": null,
              "options": $optionsJson,
              "media": $mediaJson,
              "expectedVersion": ${expectedVersion ?: "null"}
            }
            """.trimIndent()
    }

    @Test
    fun `full service lifecycle - draft through published, suspend, republish, archive`() {
        val token = login(staffEmail, staffPassword)
        val categoryId = createCategory(token)

        val createBody =
            mockMvc
                .post("/api/v1/travel-marketplace/services") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = createServiceRequest(categoryId)
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.status") { value("DRAFT") }
                }.andReturn()
                .response.contentAsString
        val serviceId = jsonField(createBody, "id")

        mockMvc
            .post("/api/v1/travel-marketplace/services/$serviceId/submit-for-review") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("REVIEW") }
            }

        mockMvc
            .post("/api/v1/travel-marketplace/services/$serviceId/approve") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("APPROVED") }
            }

        mockMvc
            .post("/api/v1/travel-marketplace/services/$serviceId/publish") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("PUBLISHED") }
            }

        // Now visible on the real public, unauthenticated catalog.
        val publicBody =
            mockMvc
                .get("/api/v1/travel-marketplace/public/services/$serviceId")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.name.en") { value("Desert Safari") }
                    jsonPath("$.operatedBy") { doesNotExist() }
                }.andReturn()
                .response.contentAsString

        // Plain BigDecimal serialization silently sent this as an unquoted
        // JSON number (dropping trailing zeros a client needs for exact
        // currency display) despite the contract declaring it a string —
        // caught live against a real running instance, not a same-JVM
        // round trip. Assert the raw wire format, not just the parsed value.
        assertThat(publicBody).contains(""""priceAmount":"50.00"""")

        mockMvc
            .post("/api/v1/travel-marketplace/services/$serviceId/suspend") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("SUSPENDED") }
            }

        // No longer visible publicly once suspended.
        mockMvc.get("/api/v1/travel-marketplace/public/services/$serviceId").andExpect { status { isNotFound() } }

        mockMvc
            .post("/api/v1/travel-marketplace/services/$serviceId/publish") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("PUBLISHED") }
            }

        mockMvc
            .post("/api/v1/travel-marketplace/services/$serviceId/archive") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("ARCHIVED") }
            }

        // Archiving again is a conflict, not a silent success.
        mockMvc
            .post("/api/v1/travel-marketplace/services/$serviceId/archive") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("already_archived") }
            }
    }

    @Test
    fun `publishing is refused without a bookable option, per the content template's closing rule`() {
        val token = login(staffEmail, staffPassword)
        val categoryId = createCategory(token)
        val createBody =
            mockMvc
                .post("/api/v1/travel-marketplace/services") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = createServiceRequest(categoryId, withOptions = false)
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        val serviceId = jsonField(createBody, "id")

        mockMvc.post("/api/v1/travel-marketplace/services/$serviceId/submit-for-review") { header("Authorization", "Bearer $token") }
        mockMvc.post("/api/v1/travel-marketplace/services/$serviceId/approve") { header("Authorization", "Bearer $token") }

        mockMvc
            .post("/api/v1/travel-marketplace/services/$serviceId/publish") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("missing_publishable_option") }
            }
    }

    @Test
    fun `publishing is refused without rights-cleared media`() {
        val token = login(staffEmail, staffPassword)
        val categoryId = createCategory(token)
        val createBody =
            mockMvc
                .post("/api/v1/travel-marketplace/services") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = createServiceRequest(categoryId, withMedia = false)
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        val serviceId = jsonField(createBody, "id")

        mockMvc.post("/api/v1/travel-marketplace/services/$serviceId/submit-for-review") { header("Authorization", "Bearer $token") }
        mockMvc.post("/api/v1/travel-marketplace/services/$serviceId/approve") { header("Authorization", "Bearer $token") }

        mockMvc
            .post("/api/v1/travel-marketplace/services/$serviceId/publish") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("missing_rights_cleared_media") }
            }
    }

    @Test
    fun `a PARTNER service without a provider is a clean 400`() {
        val token = login(staffEmail, staffPassword)
        val categoryId = createCategory(token)

        mockMvc
            .post("/api/v1/travel-marketplace/services") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = createServiceRequest(categoryId, fulfilmentModel = "PARTNER", providerId = null)
            }.andExpect { status { is4xxClientError() } }
    }

    @Test
    fun `a PARTNER service shows the provider name as operatedBy on the public catalog, never its contact details`() {
        val token = login(staffEmail, staffPassword)
        val categoryId = createCategory(token)
        val providerId = createProvider(token)

        val createBody =
            mockMvc
                .post("/api/v1/travel-marketplace/services") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = createServiceRequest(categoryId, fulfilmentModel = "PARTNER", providerId = providerId)
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        val serviceId = jsonField(createBody, "id")

        mockMvc.post("/api/v1/travel-marketplace/services/$serviceId/submit-for-review") { header("Authorization", "Bearer $token") }
        mockMvc.post("/api/v1/travel-marketplace/services/$serviceId/approve") { header("Authorization", "Bearer $token") }
        mockMvc.post("/api/v1/travel-marketplace/services/$serviceId/publish") { header("Authorization", "Bearer $token") }

        val publicBody =
            mockMvc
                .get("/api/v1/travel-marketplace/public/services/$serviceId")
                .andExpect {
                    status { isOk() }
                    jsonPath("$.operatedBy") { value("Blue Horizon Diving") }
                }.andReturn()
                .response.contentAsString
        assertThat(publicBody).doesNotContain("ops@example.com")
        assertThat(publicBody).doesNotContain("rightsEvidence")
        assertThat(publicBody).doesNotContain("Owner-supplied")
    }

    @Test
    fun `the public catalog never lists a draft, review, approved, suspended, or archived service`() {
        val token = login(staffEmail, staffPassword)
        val categoryId = createCategory(token)
        val createBody =
            mockMvc
                .post("/api/v1/travel-marketplace/services") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = createServiceRequest(categoryId, name = "Never Published Service")
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        val serviceId = jsonField(createBody, "id")

        mockMvc.get("/api/v1/travel-marketplace/public/services/$serviceId").andExpect { status { isNotFound() } }

        val listBody =
            mockMvc
                .get("/api/v1/travel-marketplace/public/services")
                .andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        assertThat(listBody).doesNotContain("Never Published Service")
    }

    @Test
    fun `category and provider CRUD - create, list, get, update, archive`() {
        val token = login(staffEmail, staffPassword)
        val categoryId = createCategory(token, code = "family-activities")

        mockMvc
            .get("/api/v1/travel-marketplace/categories/$categoryId") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.code") { value("family-activities") }
                jsonPath("$.version") { value(1) }
            }

        // A duplicate code is a clean 409, never a raw 500 from the unique constraint.
        mockMvc
            .post("/api/v1/travel-marketplace/categories") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"code": "family-activities", "name": {"en": "Family", "ar": "عائلي"}, "description": null, "displayOrder": 1}"""
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("duplicate_code") }
            }

        mockMvc
            .put("/api/v1/travel-marketplace/categories/$categoryId") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {"code": "family-activities", "name": {"en": "Family Fun", "ar": "عائلي ممتع"},
                     "description": null, "displayOrder": 1, "expectedVersion": 1}
                    """.trimIndent()
            }.andExpect {
                status { isOk() }
                jsonPath("$.name.en") { value("Family Fun") }
                jsonPath("$.version") { value(2) }
            }

        // A stale expectedVersion is a clean 409, never a silent overwrite of the update above.
        mockMvc
            .put("/api/v1/travel-marketplace/categories/$categoryId") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {"code": "family-activities", "name": {"en": "Stale Edit", "ar": "تعديل قديم"},
                     "description": null, "displayOrder": 1, "expectedVersion": 1}
                    """.trimIndent()
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("version_conflict") }
                jsonPath("$.currentVersion") { value(2) }
            }

        mockMvc
            .delete("/api/v1/travel-marketplace/categories/$categoryId") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("ARCHIVED") }
            }

        val providerId = createProvider(token)
        mockMvc
            .get("/api/v1/travel-marketplace/providers/$providerId") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { value("Blue Horizon Diving") }
                jsonPath("$.version") { value(1) }
            }

        mockMvc
            .put("/api/v1/travel-marketplace/providers/$providerId") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"name": "Blue Horizon Diving Co", "contactEmail": "ops@example.com", "contactPhone": null, "expectedVersion": 1}"""
            }.andExpect {
                status { isOk() }
                jsonPath("$.name") { value("Blue Horizon Diving Co") }
                jsonPath("$.version") { value(2) }
            }

        // A stale expectedVersion is a clean 409, never a silent overwrite of the update above.
        mockMvc
            .put("/api/v1/travel-marketplace/providers/$providerId") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"name": "Stale Edit", "contactEmail": "ops@example.com", "contactPhone": null, "expectedVersion": 1}"""
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("version_conflict") }
                jsonPath("$.currentVersion") { value(2) }
            }

        mockMvc
            .delete("/api/v1/travel-marketplace/providers/$providerId") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.status") { value("ARCHIVED") }
            }
    }

    @Test
    fun `updating a category or provider without sending expectedVersion is a clean 400, never an unconditional overwrite`() {
        val token = login(staffEmail, staffPassword)
        val categoryId = createCategory(token, code = "no-version-category")
        val providerId = createProvider(token)

        mockMvc
            .put("/api/v1/travel-marketplace/categories/$categoryId") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"code": "no-version-category", "name": {"en": "X", "ar": "س"}, "description": null, "displayOrder": 0}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("expected_version_required") }
            }

        mockMvc
            .put("/api/v1/travel-marketplace/providers/$providerId") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"name": "X", "contactEmail": "ops@example.com", "contactPhone": null}"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("expected_version_required") }
            }
    }

    @Test
    fun `a view-only role can list but not create a service`() {
        val viewToken = login(viewOnlyEmail, viewOnlyPassword)

        mockMvc
            .get("/api/v1/travel-marketplace/services") {
                header("Authorization", "Bearer $viewToken")
            }.andExpect { status { isOk() } }

        mockMvc
            .post("/api/v1/travel-marketplace/services") {
                header("Authorization", "Bearer $viewToken")
                contentType = MediaType.APPLICATION_JSON
                content = createServiceRequest(UUID.randomUUID().toString())
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `a user with no marketplace permissions is denied entirely`() {
        val token = login(noPermissionEmail, noPermissionPassword)

        mockMvc
            .get("/api/v1/travel-marketplace/services") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `an unknown service id is a clean 404 on the staff endpoint`() {
        val token = login(staffEmail, staffPassword)

        mockMvc
            .get("/api/v1/travel-marketplace/services/${UUID.randomUUID()}") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `creating a service against an unknown category is a clean 400, never a raw 500`() {
        val token = login(staffEmail, staffPassword)

        mockMvc
            .post("/api/v1/travel-marketplace/services") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = createServiceRequest(UUID.randomUUID().toString())
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("category_not_found") }
            }
    }

    // The next four tests cover real gaps an independent Tier 1 review found
    // live against a running instance (see the WEGO-010-A board's Packet 0R/1A
    // remediation entry) — each one reproduces the exact scenario that review
    // used, not a paraphrase of it.

    @Test
    fun `provider list rejects an out-of-range page or size as a clean 400, never a raw 409`() {
        val token = login(staffEmail, staffPassword)

        mockMvc
            .get("/api/v1/travel-marketplace/providers") {
                header("Authorization", "Bearer $token")
                param("page", "-1")
            }.andExpect { status { isBadRequest() } }

        mockMvc
            .get("/api/v1/travel-marketplace/providers") {
                header("Authorization", "Bearer $token")
                param("size", "500")
            }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `a category update attempting to change the immutable code is a clean 409, not a silent no-op 200`() {
        val token = login(staffEmail, staffPassword)
        val categoryId = createCategory(token, code = "immutable-code-test")

        mockMvc
            .put("/api/v1/travel-marketplace/categories/$categoryId") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {"code": "a-different-code", "name": {"en": "Renamed", "ar": "معاد تسميته"},
                     "description": null, "displayOrder": 0, "expectedVersion": 1}
                    """.trimIndent()
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("code_immutable") }
            }

        mockMvc
            .get("/api/v1/travel-marketplace/categories/$categoryId") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.code") { value("immutable-code-test") }
            }
    }

    @Test
    fun `updating a PUBLISHED service to have no options or media is rejected, not a silent 200 that empties a live listing`() {
        val token = login(staffEmail, staffPassword)
        val categoryId = createCategory(token)
        val createBody =
            mockMvc
                .post("/api/v1/travel-marketplace/services") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = createServiceRequest(categoryId, name = "Still Live Service")
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        val serviceId = jsonField(createBody, "id")
        val version = jsonIntField(createBody, "version")
        mockMvc.post("/api/v1/travel-marketplace/services/$serviceId/submit-for-review") { header("Authorization", "Bearer $token") }
        mockMvc.post("/api/v1/travel-marketplace/services/$serviceId/approve") { header("Authorization", "Bearer $token") }
        mockMvc.post("/api/v1/travel-marketplace/services/$serviceId/publish") { header("Authorization", "Bearer $token") }

        mockMvc
            .put("/api/v1/travel-marketplace/services/$serviceId") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    createServiceRequest(
                        categoryId,
                        name = "Still Live Service",
                        withOptions = false,
                        withMedia = false,
                        expectedVersion = version,
                    )
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("would_invalidate_published_content") }
            }

        // The rejected attempt must not have partially applied — the service
        // stays fully intact and fully published on the real public catalog.
        mockMvc
            .get("/api/v1/travel-marketplace/public/services/$serviceId")
            .andExpect {
                status { isOk() }
                jsonPath("$.options.length()") { value(1) }
                jsonPath("$.media.length()") { value(1) }
            }
    }

    @Test
    fun `updating a service without sending expectedVersion is a clean 400, never an unconditional overwrite`() {
        val token = login(staffEmail, staffPassword)
        val categoryId = createCategory(token)
        val createBody =
            mockMvc
                .post("/api/v1/travel-marketplace/services") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = createServiceRequest(categoryId)
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        val serviceId = jsonField(createBody, "id")

        mockMvc
            .put("/api/v1/travel-marketplace/services/$serviceId") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = createServiceRequest(categoryId, name = "Updated Without Version")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("expected_version_required") }
            }
    }

    @Test
    fun `a stale expectedVersion is a clean 409, not a silent overwrite of a concurrent edit`() {
        val token = login(staffEmail, staffPassword)
        val categoryId = createCategory(token)
        val createBody =
            mockMvc
                .post("/api/v1/travel-marketplace/services") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = createServiceRequest(categoryId)
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        val serviceId = jsonField(createBody, "id")
        val originalVersion = jsonIntField(createBody, "version")

        // Simulates staff member A's successful save — the one B's stale
        // edit form never saw.
        val firstUpdateBody =
            mockMvc
                .put("/api/v1/travel-marketplace/services/$serviceId") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = createServiceRequest(categoryId, name = "Edited By Staff A", expectedVersion = originalVersion)
                }.andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString
        val bumpedVersion = jsonIntField(firstUpdateBody, "version")
        assertThat(bumpedVersion).isEqualTo(originalVersion + 1)

        // Staff member B's edit form was loaded before A saved — it still
        // carries the original (now stale) version. This must be rejected,
        // not silently applied over A's change.
        mockMvc
            .put("/api/v1/travel-marketplace/services/$serviceId") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = createServiceRequest(categoryId, name = "Edited By Staff B (stale)", expectedVersion = originalVersion)
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("version_conflict") }
                jsonPath("$.currentVersion") { value(bumpedVersion) }
            }

        // A's change survived; B's never applied.
        mockMvc
            .get("/api/v1/travel-marketplace/services/$serviceId") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isOk() }
                jsonPath("$.name.en") { value("Edited By Staff A") }
                jsonPath("$.version") { value(bumpedVersion) }
            }

        // B reloads the now-current version and retries — the real recovery
        // path a stale edit is expected to take.
        mockMvc
            .put("/api/v1/travel-marketplace/services/$serviceId") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = createServiceRequest(categoryId, name = "Edited By Staff B (retried)", expectedVersion = bumpedVersion)
            }.andExpect {
                status { isOk() }
                jsonPath("$.name.en") { value("Edited By Staff B (retried)") }
                jsonPath("$.version") { value(bumpedVersion + 1) }
            }
    }

    @Test
    fun `an unassigned currency code is a clean 400, never accepted as if it were EGP`() {
        val token = login(staffEmail, staffPassword)
        val categoryId = createCategory(token)

        mockMvc
            .post("/api/v1/travel-marketplace/services") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    createServiceRequest(categoryId).replace("\"priceCurrency\": \"EGP\"", "\"priceCurrency\": \"ZZZ\"")
            }.andExpect { status { isBadRequest() } }
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("wego_sharm_to_go_test")
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
