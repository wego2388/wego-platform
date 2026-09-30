package com.wego.toursoperator

import com.wego.generated.jooq.tables.IdentityRole.IDENTITY_ROLE
import com.wego.generated.jooq.tables.IdentityRolePermission.IDENTITY_ROLE_PERMISSION
import com.wego.generated.jooq.tables.ToursOperatorTour.TOURS_OPERATOR_TOUR
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
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * WEGO-016-UX0: localized tour content is edited as a DRAFT and reaches the
 * public site only after publishing; media reaches it only after rights
 * approval.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class ToursOperatorTourContentTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var passwordHasher: PasswordHasher

    @Autowired private lateinit var dsl: DSLContext

    private val adminEmail = "sts-content-admin@example.com"
    private val editorEmail = "sts-content-editor@example.com"
    private val password = "a-very-long-password-content-123"

    private lateinit var tourId: UUID
    private lateinit var slug: String

    @BeforeEach
    fun setUp() {
        seedUser(adminEmail, setOf("platform-admin"))
        // An editor may write drafts but not publish.
        val role = "sts-test-content-editor"
        if (dsl.fetchOne(IDENTITY_ROLE, IDENTITY_ROLE.CODE.eq(role)) == null) {
            dsl
                .insertInto(IDENTITY_ROLE)
                .set(IDENTITY_ROLE.CODE, role)
                .set(IDENTITY_ROLE.DESCRIPTION, "editor")
                .execute()
            listOf("tours-operator.tour:view", "tours-operator.tour:manage").forEach { permission ->
                dsl
                    .insertInto(IDENTITY_ROLE_PERMISSION)
                    .set(IDENTITY_ROLE_PERMISSION.ROLE_CODE, role)
                    .set(IDENTITY_ROLE_PERMISSION.PERMISSION_CODE, permission)
                    .execute()
            }
        }
        seedUser(editorEmail, setOf(role))

        tourId = UUID.randomUUID()
        slug = "content-${tourId.toString().take(8)}"
        dsl
            .insertInto(TOURS_OPERATOR_TOUR)
            .set(TOURS_OPERATOR_TOUR.ID, tourId)
            .set(TOURS_OPERATOR_TOUR.SLUG, slug)
            .set(TOURS_OPERATOR_TOUR.NAME_EN, "Legacy Name")
            .set(TOURS_OPERATOR_TOUR.CATEGORY, "SEA")
            .set(TOURS_OPERATOR_TOUR.DURATION_TEXT, "6 hours")
            .set(TOURS_OPERATOR_TOUR.PRICE_ADULT_CENTS, 4000L)
            .set(TOURS_OPERATOR_TOUR.CAPACITY, 20)
            .set(TOURS_OPERATOR_TOUR.AVAILABLE_TIME_SLOTS, "MORNING")
            .set(TOURS_OPERATOR_TOUR.SORT_ORDER, 1)
            .set(TOURS_OPERATOR_TOUR.IS_ACTIVE, true)
            .set(TOURS_OPERATOR_TOUR.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .execute()
    }

    private fun seedUser(
        email: String,
        roles: Set<String>,
    ) {
        if (userRepository.findByEmail(EmailAddress.of(email)) != null) return
        userRepository.save(
            User(
                id = UserId.generate(),
                email = EmailAddress.of(email),
                passwordHash = passwordHasher.hash(password),
                status = UserStatus.ACTIVE,
                roles = roles.map(RoleCode::of).toSet(),
                createdAt = Instant.now(),
                failedLoginCount = 0,
                lockedUntil = null,
            ),
        )
    }

    private fun token(email: String): String {
        val body =
            mockMvc
                .post("/api/v1/identity/login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"$email","password":"$password"}"""
                }.andReturn()
                .response.contentAsString
        return Regex(""""token"\s*:\s*"([^"]+)"""").find(body)!!.groupValues[1]
    }

    private fun contentJson(
        name: String,
        stops: String = "[]",
    ) = """
        {"name":"$name","shortDescription":"Short $name","description":"Long description of $name",
         "includes":["Hotel pickup","Lunch"],"excludes":["Tips"],"knowBeforeYouGo":["Bring a hat"],
         "meetingPoint":"Hotel lobby","stops":$stops}
        """.trimIndent()

    private fun saveDraft(
        locale: String,
        body: String,
        asEmail: String = editorEmail,
    ) = mockMvc.put("/api/v1/tours-operator/staff/tours/$tourId/content/$locale") {
        header("Authorization", "Bearer ${token(asEmail)}")
        contentType = MediaType.APPLICATION_JSON
        content = body
    }

    private fun staffJson(): String =
        mockMvc
            .get("/api/v1/tours-operator/staff/tours/$tourId/content") {
                header("Authorization", "Bearer ${token(adminEmail)}")
            }.andReturn()
            .response.contentAsString

    /** Revision of the current DRAFT of a locale, or of the facts when [locale] is null. */
    private fun draftRevision(locale: String?): String {
        val json = staffJson()
        val section =
            if (locale == null) {
                json.substringAfter("\"facts\"")
            } else {
                json.substringAfter("\"$locale\":[")
            }
        val draft = section.substringAfter("\"stage\":\"DRAFT\"")
        return Regex(""""revision"\s*:\s*"([0-9a-f]+)"""").find(draft)!!.groupValues[1]
    }

    private fun mediaRevision(mediaId: String): String {
        val item = staffJson().substringAfter("\"id\":\"$mediaId\"")
        return Regex(""""revision"\s*:\s*"([0-9a-f]+)"""").find(item)!!.groupValues[1]
    }

    /** Publishes (or approves) with the revision the publisher just reviewed. */
    private fun publish(
        path: String,
        revision: String? = null,
        asEmail: String = adminEmail,
    ) = mockMvc.post("/api/v1/tours-operator/staff/tours/$tourId/$path") {
        header("Authorization", "Bearer ${token(asEmail)}")
        if (!path.endsWith("unpublish")) {
            contentType = MediaType.APPLICATION_JSON
            val reviewed =
                revision ?: when {
                    path.startsWith("content/") -> draftRevision(path.split("/")[1])
                    path.startsWith("facts/") -> draftRevision(null)
                    else -> mediaRevision(path.split("/")[1])
                }
            content = """{"revision":"$reviewed"}"""
        }
    }

    private fun publicContent(locale: String) = mockMvc.get("/api/v1/tours-operator/tours/by-slug/content?slug=$slug&locale=$locale")

    @Test
    fun `a draft is never public, publishing makes it public, and English is the fallback`() {
        saveDraft("en", contentJson("Coral Reef Cruise")).andExpect { status { isNoContent() } }
        publicContent("en").andExpect {
            status { isOk() }
            jsonPath("$.servedLocale") { isEmpty() }
            jsonPath("$.name") { isEmpty() }
            jsonPath("$.media.length()") { value(0) }
        }

        publish("content/en/publish").andExpect { status { isNoContent() } }
        publicContent("en").andExpect {
            jsonPath("$.servedLocale") { value("en") }
            jsonPath("$.name") { value("Coral Reef Cruise") }
            jsonPath("$.includes[0]") { value("Hotel pickup") }
            jsonPath("$.meetingPoint") { value("Hotel lobby") }
        }
        // No Arabic published yet: English is served and reported as such.
        publicContent("ar").andExpect {
            jsonPath("$.requestedLocale") { value("ar") }
            jsonPath("$.servedLocale") { value("en") }
        }

        saveDraft("ar", contentJson("رحلة الشعاب المرجانية")).andExpect { status { isNoContent() } }
        publish("content/ar/publish").andExpect { status { isNoContent() } }
        publicContent("ar").andExpect {
            jsonPath("$.servedLocale") { value("ar") }
            jsonPath("$.name") { value("رحلة الشعاب المرجانية") }
        }
    }

    @Test
    fun `editing a published locale keeps the live text until it is published again`() {
        saveDraft("en", contentJson("First Title"))
        publish("content/en/publish")
        saveDraft("en", contentJson("Second Title")).andExpect { status { isNoContent() } }

        publicContent("en").andExpect { jsonPath("$.name") { value("First Title") } }
        publish("content/en/publish")
        publicContent("en").andExpect { jsonPath("$.name") { value("Second Title") } }

        publish("content/en/unpublish").andExpect { status { isNoContent() } }
        publicContent("en").andExpect { jsonPath("$.name") { isEmpty() } }
        // The draft survives unpublishing.
        mockMvc
            .get("/api/v1/tours-operator/staff/tours/$tourId/content") {
                header("Authorization", "Bearer ${token(editorEmail)}")
            }.andExpect {
                status { isOk() }
                jsonPath("$.content.en.length()") { value(1) }
                jsonPath("$.content.en[0].stage") { value("DRAFT") }
                jsonPath("$.content.en[0].document.name") { value("Second Title") }
            }
    }

    @Test
    fun `an editor can write drafts but only a publisher can publish`() {
        saveDraft("en", contentJson("Editor Draft")).andExpect { status { isNoContent() } }
        publish("content/en/publish", revision = "0", asEmail = editorEmail).andExpect { status { isForbidden() } }
        publish("content/en/unpublish", asEmail = editorEmail).andExpect { status { isForbidden() } }
        publish("facts/publish", revision = "0", asEmail = editorEmail).andExpect { status { isForbidden() } }
        publish("media/${UUID.randomUUID()}/approve", revision = "0", asEmail = editorEmail).andExpect { status { isForbidden() } }
        mockMvc.get("/api/v1/tours-operator/staff/tours/$tourId/content").andExpect { status { isUnauthorized() } }
        mockMvc
            .put("/api/v1/tours-operator/staff/tours/$tourId/content/en") {
                contentType = MediaType.APPLICATION_JSON
                content = contentJson("Anonymous")
            }.andExpect { status { isUnauthorized() } }
        publish("content/it/publish", revision = "0").andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("nothing_to_publish") }
        }
    }

    @Test
    fun `invalid content and unknown locales are rejected with the broken rule`() {
        saveDraft("en", contentJson("x".repeat(200))).andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("invalid_content") }
            jsonPath("$.detail") { value("name must be 1–120 characters") }
        }
        saveDraft("de", contentJson("German")).andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("unsupported_locale") }
        }
        publicContent("de").andExpect { status { isBadRequest() } }
        mockMvc
            .put("/api/v1/tours-operator/staff/tours/$tourId/media") {
                header("Authorization", "Bearer ${token(adminEmail)}")
                contentType = MediaType.APPLICATION_JSON
                content = """[{"path":"/media/tours/$slug/a.avif","width":1,"height":1,"alt":{"en":"A","EN":"B"}}]"""
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.detail") { value("alt has the same locale twice") }
            }
    }

    @Test
    fun `a draft edited after review cannot be published under the old review`() {
        saveDraft("en", contentJson("Reviewed Version"))
        val reviewed = draftRevision("en")
        saveDraft("en", contentJson("Sneaky Later Edit"))

        publish("content/en/publish", revision = reviewed).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("draft_changed") }
        }
        publicContent("en").andExpect { jsonPath("$.name") { isEmpty() } }

        publish("content/en/publish").andExpect { status { isNoContent() } }
        publicContent("en").andExpect { jsonPath("$.name") { value("Sneaky Later Edit") } }
    }

    @Test
    fun `stops appear only with published coordinates and published text`() {
        val stops = """[{"stopKey":"ras-mohammed","name":"Ras Mohammed","description":"Snorkel stop"}]"""
        saveDraft("en", contentJson("Boat Trip", stops))
        publish("content/en/publish")
        publicContent("en").andExpect { jsonPath("$.stops.length()") { value(0) } }

        mockMvc
            .put("/api/v1/tours-operator/staff/tours/$tourId/facts") {
                header("Authorization", "Bearer ${token(editorEmail)}")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    {"childrenAllowed":true,"minimumAge":6,"guideLanguages":["en","ru"],"hotelPickup":"INCLUDED",
                     "stops":[{"key":"ras-mohammed","kind":"STOP","latitude":27.73,"longitude":34.25},
                              {"key":"marina","kind":"MEETING_POINT","latitude":27.91,"longitude":34.33}]}
                    """.trimIndent()
            }.andExpect { status { isNoContent() } }
        publicContent("en").andExpect {
            jsonPath("$.facts") { isEmpty() }
            jsonPath("$.stops.length()") { value(0) }
        }

        publish("facts/publish").andExpect { status { isNoContent() } }
        publicContent("en").andExpect {
            jsonPath("$.facts.childrenAllowed") { value(true) }
            jsonPath("$.facts.hotelPickup") { value("INCLUDED") }
            // "marina" has coordinates but no published text, so it stays hidden.
            jsonPath("$.stops.length()") { value(1) }
            jsonPath("$.stops[0].name") { value("Ras Mohammed") }
            jsonPath("$.stops[0].latitude") { value(27.73) }
        }
    }

    @Test
    fun `media is public only after rights approval, and approval survives unchanged files`() {
        val adminToken = token(adminEmail)
        val mediaBody =
            """
            [{"path":"/media/tours/$slug/cover.avif","width":1600,"height":900,"isCover":true,
              "alt":{"en":"Boat on turquoise water","ar":"قارب على مياه فيروزية"}},
             {"path":"/media/tours/$slug/reef.avif","width":1200,"height":800,"alt":{"en":"Coral reef"}}]
            """.trimIndent()
        mockMvc
            .put("/api/v1/tours-operator/staff/tours/$tourId/media") {
                header("Authorization", "Bearer ${token(editorEmail)}")
                contentType = MediaType.APPLICATION_JSON
                content = mediaBody
            }.andExpect { status { isNoContent() } }
        publicContent("en").andExpect { jsonPath("$.media.length()") { value(0) } }

        val staff =
            mockMvc
                .get("/api/v1/tours-operator/staff/tours/$tourId/content") {
                    header("Authorization", "Bearer $adminToken")
                }.andReturn()
                .response.contentAsString
        val coverId =
            Regex(
                """"id"\s*:\s*"([0-9a-f-]{36})"[^}]*"path"\s*:\s*"/media/tours/$slug/cover.avif"""",
            ).find(staff)!!.groupValues[1]

        publish("media/$coverId/approve").andExpect { status { isNoContent() } }
        publicContent("ar").andExpect {
            jsonPath("$.media.length()") { value(1) }
            jsonPath("$.media[0].isCover") { value(true) }
            jsonPath("$.media[0].alt") { value("قارب على مياه فيروزية") }
        }

        // Changing the alt text of an approved image needs approval again.
        mockMvc
            .put("/api/v1/tours-operator/staff/tours/$tourId/media") {
                header("Authorization", "Bearer ${token(editorEmail)}")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    [{"id":"$coverId","path":"/media/tours/$slug/cover.avif","width":1600,"height":900,"isCover":true,
                      "alt":{"en":"Unreviewed caption","ar":"قارب على مياه فيروزية"}}]
                    """.trimIndent()
            }.andExpect { status { isNoContent() } }
        publicContent("en").andExpect { jsonPath("$.media.length()") { value(0) } }
        publish("media/$coverId/approve").andExpect { status { isNoContent() } }

        // Same id, file and alt keep approval (layout may change); a re-pointed file needs approval again.
        mockMvc
            .put("/api/v1/tours-operator/staff/tours/$tourId/media") {
                header("Authorization", "Bearer $adminToken")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    [{"id":"$coverId","path":"/media/tours/$slug/cover.avif","width":800,"height":450,"isCover":true,
                      "alt":{"en":"Unreviewed caption","ar":"قارب على مياه فيروزية"}}]
                    """.trimIndent()
            }.andExpect { status { isNoContent() } }
        publicContent("en").andExpect { jsonPath("$.media.length()") { value(1) } }

        mockMvc
            .put("/api/v1/tours-operator/staff/tours/$tourId/media") {
                header("Authorization", "Bearer $adminToken")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """
                    [{"id":"$coverId","path":"/media/tours/$slug/cover-v2.avif","width":1600,"height":900,"isCover":true,
                      "alt":{"en":"Unreviewed caption","ar":"قارب على مياه فيروزية"}}]
                    """.trimIndent()
            }.andExpect { status { isNoContent() } }
        publicContent("en").andExpect { jsonPath("$.media.length()") { value(0) } }
    }

    @Test
    fun `media rules reject two covers, bad paths and approval without English alt`() {
        val adminToken = token(adminEmail)

        fun putMedia(body: String) =
            mockMvc.put("/api/v1/tours-operator/staff/tours/$tourId/media") {
                header("Authorization", "Bearer $adminToken")
                contentType = MediaType.APPLICATION_JSON
                content = body
            }
        putMedia(
            """[{"path":"/media/tours/$slug/a.avif","width":1,"height":1,"isCover":true},
                {"path":"/media/tours/$slug/b.avif","width":1,"height":1,"isCover":true}]""",
        ).andExpect { jsonPath("$.error") { value("multiple_covers") } }
        val sameId = UUID.randomUUID()
        putMedia(
            """[{"id":"$sameId","path":"/media/tours/$slug/d.avif","width":1,"height":1},
                {"id":"$sameId","path":"/media/tours/$slug/e.avif","width":1,"height":1}]""",
        ).andExpect {
            status { isBadRequest() }
            jsonPath("$.error") { value("duplicate_media_id") }
        }
        putMedia("""[{"path":"https://evil.example/x.jpg","width":1,"height":1}]""")
            .andExpect { jsonPath("$.error") { value("invalid_content") } }

        putMedia("""[{"path":"/media/tours/$slug/c.avif","width":1,"height":1,"alt":{"it":"Barca"}}]""")
            .andExpect { status { isNoContent() } }
        val staff =
            mockMvc
                .get("/api/v1/tours-operator/staff/tours/$tourId/content") {
                    header("Authorization", "Bearer $adminToken")
                }.andReturn()
                .response.contentAsString
        val mediaId = Regex(""""id"\s*:\s*"([0-9a-f-]{36})"""").find(staff.substringAfter("\"media\""))!!.groupValues[1]
        publish("media/$mediaId/approve").andExpect { jsonPath("$.error") { value("english_alt_required") } }
    }

    @Test
    fun `public tour lists carry the published localized name with English fallback`() {
        saveDraft("en", contentJson("Coral Reef Cruise"))
        publish("content/en/publish")
        saveDraft("ru", contentJson("Круиз к рифам"))
        publish("content/ru/publish")

        mockMvc.get("/api/v1/tours-operator/tours/by-slug?slug=$slug&locale=ru").andExpect {
            jsonPath("$.localized.locale") { value("ru") }
            jsonPath("$.localized.name") { value("Круиз к рифам") }
        }
        mockMvc.get("/api/v1/tours-operator/tours/by-slug?slug=$slug&locale=it").andExpect {
            jsonPath("$.localized.locale") { value("en") }
            jsonPath("$.localized.name") { value("Coral Reef Cruise") }
        }
        mockMvc.get("/api/v1/tours-operator/tours/by-slug?slug=$slug").andExpect {
            jsonPath("$.localized") { isEmpty() }
            jsonPath("$.nameEn") { value("Legacy Name") }
        }
        mockMvc.get("/api/v1/tours-operator/tours?locale=ru&size=100").andExpect {
            status { isOk() }
            jsonPath("$[?(@.slug == '$slug')].localized.name") { value("Круиз к рифам") }
        }
    }

    @Test
    fun `inactive or unknown tours have no public content`() {
        mockMvc.get("/api/v1/tours-operator/tours/by-slug/content?slug=no-such-tour&locale=en").andExpect { status { isNotFound() } }
        dsl
            .update(TOURS_OPERATOR_TOUR)
            .set(TOURS_OPERATOR_TOUR.IS_ACTIVE, false)
            .where(TOURS_OPERATOR_TOUR.ID.eq(tourId))
            .execute()
        publicContent("en").andExpect { status { isNotFound() } }
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("wego_test_content")
                .withUsername("wego_test")
                .withPassword("wego_test")

        @DynamicPropertySource
        @JvmStatic
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.enabled") { true }
            registry.add("tours-operator.paymob.mock-enabled") { true }
        }
    }
}
