package com.wego.toursoperator

import com.wego.generated.jooq.tables.IdentityRole.IDENTITY_ROLE
import com.wego.generated.jooq.tables.IdentityRolePermission.IDENTITY_ROLE_PERMISSION
import com.wego.generated.jooq.tables.ToursOperatorAsset.TOURS_OPERATOR_ASSET
import com.wego.generated.jooq.tables.ToursOperatorTour.TOURS_OPERATOR_TOUR
import com.wego.identity.application.PasswordHasher
import com.wego.identity.application.UserRepository
import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.User
import com.wego.identity.domain.UserId
import com.wego.identity.domain.UserStatus
import com.wego.toursoperator.application.AssetRepository
import com.wego.toursoperator.application.AssetStorage
import com.wego.toursoperator.application.AssetStorageException
import com.wego.toursoperator.application.ImageProcessor
import com.wego.toursoperator.application.MediaUploadService
import com.wego.toursoperator.application.TourContentRepository
import com.wego.toursoperator.application.TourRepository
import com.wego.toursoperator.application.TransactionRunner
import com.wego.toursoperator.application.UploadResult
import com.wego.toursoperator.domain.ContentLocale
import com.wego.toursoperator.domain.TourId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import tools.jackson.databind.ObjectMapper
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

/** Real Flyway/PostgreSQL, real bearer sessions and a private temporary filesystem. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["tours-operator.paymob.mock-enabled=true"])
@AutoConfigureMockMvc
class ToursOperatorMediaHttpTest {
    @Autowired private lateinit var mvc: MockMvc

    @Autowired private lateinit var dsl: DSLContext

    @Autowired private lateinit var users: UserRepository

    @Autowired private lateinit var passwords: PasswordHasher

    @Autowired private lateinit var tours: TourRepository

    @Autowired private lateinit var contents: TourContentRepository

    @Autowired private lateinit var assets: AssetRepository

    @Autowired private lateinit var storage: AssetStorage

    @Autowired private lateinit var processor: ImageProcessor

    @Autowired private lateinit var transactions: TransactionRunner

    @LocalServerPort private var serverPort: Int = 0
    private val json = ObjectMapper()
    private lateinit var id: UUID
    private lateinit var slug: String
    private lateinit var token: String
    private val password = "media-test-long-password-123!"

    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer("postgres:18.4-alpine").withDatabaseName("safari_media_test")
        private val volume = Files.createTempDirectory("safari-media-integration-")

        @DynamicPropertySource @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.enabled") { true }
            registry.add("tours-operator.media.volume-path") { volume.toString() }
        }
    }

    @BeforeEach fun setup() {
        seedUser("media-admin@example.test", setOf("platform-admin"))
        val role = "media-viewer"
        if (!dsl.fetchExists(IDENTITY_ROLE, IDENTITY_ROLE.CODE.eq(role))) {
            dsl
                .insertInto(IDENTITY_ROLE)
                .set(IDENTITY_ROLE.CODE, role)
                .set(IDENTITY_ROLE.DESCRIPTION, "Test viewer")
                .execute()
            dsl
                .insertInto(IDENTITY_ROLE_PERMISSION)
                .set(IDENTITY_ROLE_PERMISSION.ROLE_CODE, role)
                .set(IDENTITY_ROLE_PERMISSION.PERMISSION_CODE, "tours-operator.tour:view")
                .execute()
        }
        seedUser("media-viewer@example.test", setOf(role))
        token = login("media-admin@example.test")
        id = UUID.randomUUID()
        slug = "media-${id.toString().take(8)}"
        dsl
            .insertInto(TOURS_OPERATOR_TOUR)
            .set(TOURS_OPERATOR_TOUR.ID, id)
            .set(TOURS_OPERATOR_TOUR.SLUG, slug)
            .set(TOURS_OPERATOR_TOUR.NAME_EN, "Synthetic media test")
            .set(TOURS_OPERATOR_TOUR.CATEGORY, "SEA")
            .set(TOURS_OPERATOR_TOUR.DURATION_TEXT, "Test duration")
            .set(TOURS_OPERATOR_TOUR.PRICE_ADULT_CENTS, 100L)
            .set(TOURS_OPERATOR_TOUR.CAPACITY, 1)
            .set(TOURS_OPERATOR_TOUR.AVAILABLE_TIME_SLOTS, "MORNING")
            .set(TOURS_OPERATOR_TOUR.SORT_ORDER, 1)
            .set(TOURS_OPERATOR_TOUR.IS_ACTIVE, true)
            .set(TOURS_OPERATOR_TOUR.CREATED_AT, OffsetDateTime.now())
            .execute()
    }

    private fun seedUser(
        email: String,
        roles: Set<String>,
    ) {
        if (users.findByEmail(EmailAddress.of(email)) != null) return
        users.save(
            User(
                UserId.generate(),
                EmailAddress.of(email),
                passwords.hash(password),
                UserStatus.ACTIVE,
                roles.map(RoleCode::of).toSet(),
                Instant.now(),
                0,
                null,
            ),
        )
    }

    private fun login(email: String): String =
        json
            .readTree(
                mvc
                    .post("/api/v1/identity/login") {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"email":"$email","password":"$password"}"""
                    }.andExpect { status { isOk() } }
                    .andReturn()
                    .response.contentAsString,
            )["token"]
            .asText()

    private fun image(width: Int = 800): ByteArray =
        ByteArrayOutputStream()
            .also {
                ImageIO.write(BufferedImage(width, 600, BufferedImage.TYPE_INT_RGB), "JPEG", it)
            }.toByteArray()

    private fun upload(
        bytes: ByteArray = image(),
        params: Map<String, String> = emptyMap(),
        auth: String? = token,
        url: String = "/api/v1/tours-operator/staff/tours/$id/media/upload",
    ): org.springframework.test.web.servlet.MvcResult {
        val request =
            multipart(
                url,
            ).file(MockMultipartFile("file", "../../untrusted.jpg", "application/octet-stream", bytes)).param("altEn", "Synthetic image")
        if (!params.containsKey("requestId")) request.param("requestId", UUID.randomUUID().toString())
        params.forEach { (key, value) -> request.param(key, value) }
        auth?.let { request.header("Authorization", "Bearer $it") }
        return mvc.perform(request).andReturn()
    }

    private fun staff() =
        json.readTree(
            mvc
                .get("/api/v1/tours-operator/staff/tours/$id/content") {
                    header("Authorization", "Bearer $token")
                }.andExpect { status { isOk() } }
                .andReturn()
                .response.contentAsString,
        )

    private fun media() = staff()["media"][0]

    private fun approve() {
        val media = media()
        mvc
            .post("/api/v1/tours-operator/staff/tours/$id/media/${media["id"].asText()}/approve") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"revision":"${media["revision"].asText()}"}"""
            }.andExpect { status { isNoContent() } }
    }

    @Test fun `migration seeds five category covers`() {
        mvc
            .get("/api/v1/tours-operator/staff/categories/media") { header("Authorization", "Bearer $token") }
            .andExpect {
                status { isOk() }
                jsonPath("$.length()") { value(5) }
            }
    }

    @Test fun `unauthorized and viewer uploads reject without any assets`() {
        val count = dsl.fetchCount(TOURS_OPERATOR_ASSET)
        assertThat(upload(auth = null).response.status).isEqualTo(401)
        assertThat(upload(auth = login("media-viewer@example.test")).response.status).isEqualTo(403)
        assertThat(dsl.fetchCount(TOURS_OPERATOR_ASSET)).isEqualTo(count)
    }

    @Test fun `real upload is draft private preview decodes while guessed public bytes are missing`() {
        val result = upload()
        assertThat(result.response.status).isEqualTo(200)
        val assetId = json.readTree(result.response.contentAsString)["assetId"].asText()
        val path = media()["path"].asText()
        mvc.get(path).andExpect {
            status { isNotFound() }
            header { string("Cache-Control", "no-store, private") }
        }
        mvc.get("/api/v1/tours-operator/staff/media/preview/$assetId").andExpect { status { isUnauthorized() } }
        val preview =
            mvc
                .get("/api/v1/tours-operator/staff/media/preview/$assetId?variant=w360") {
                    header("Authorization", "Bearer $token")
                }.andExpect {
                    status { isOk() }
                    header { string("Cache-Control", "no-store, private") }
                }.andReturn()
        assertThat(ImageIO.read(ByteArrayInputStream(preview.response.contentAsByteArray)).width).isEqualTo(360)
        assertThat(media()["rightsStatus"].asText()).isEqualTo("DRAFT")
        mvc.get("/api/v1/tours-operator/tours/by-slug?slug=$slug&locale=en").andExpect {
            status { isOk() }
            jsonPath("$.cover") { isEmpty() }
        }
    }

    @Test fun `upload permission and manage permission are both required on both owners`() {
        assertThat(upload().response.status).isEqualTo(200)
        val assetId = media()["path"].asText().substringAfterLast('/').substringBefore('.')
        val before = dsl.fetchCount(TOURS_OPERATOR_ASSET)
        val files = Files.walk(volume).use { paths -> paths.filter(Files::isRegularFile).count() }
        for (permissions in listOf(setOf("tours-operator.media:upload"), setOf("tours-operator.tour:manage"), emptySet())) {
            val role = "media-test-${UUID.randomUUID()}"
            dsl
                .insertInto(
                    IDENTITY_ROLE,
                ).set(IDENTITY_ROLE.CODE, role)
                .set(IDENTITY_ROLE.DESCRIPTION, "Permission boundary fixture")
                .execute()
            permissions.forEach { permission ->
                dsl
                    .insertInto(IDENTITY_ROLE_PERMISSION)
                    .set(IDENTITY_ROLE_PERMISSION.ROLE_CODE, role)
                    .set(IDENTITY_ROLE_PERMISSION.PERMISSION_CODE, permission)
                    .execute()
            }
            val email = "$role@example.test"
            seedUser(email, setOf(role))
            val bearer = login(email)
            assertThat(upload(auth = bearer).response.status).isEqualTo(403)
            assertThat(
                upload(
                    auth = bearer,
                    params = mapOf("revision" to "unseen"),
                    url = "/api/v1/tours-operator/staff/categories/SEA/media/upload",
                ).response.status,
            ).isEqualTo(403)
            mvc
                .get("/api/v1/tours-operator/staff/media/preview/$assetId") { header("Authorization", "Bearer $bearer") }
                .andExpect { status { isForbidden() } }
        }
        assertThat(dsl.fetchCount(TOURS_OPERATOR_ASSET)).isEqualTo(before)
        assertThat(Files.walk(volume).use { paths -> paths.filter(Files::isRegularFile).count() }).isEqualTo(files)
    }

    @Test fun `approved bytes match exact URL and active owner with no cache and unknown variants fail closed`() {
        assertThat(upload().response.status).isEqualTo(200)
        approve()
        val path = media()["path"].asText()
        val response =
            mvc
                .get(path)
                .andExpect {
                    status { isOk() }
                    header { string("Cache-Control", "no-store, private") }
                }.andReturn()
                .response
        assertThat(ImageIO.read(ByteArrayInputStream(response.contentAsByteArray)).height).isEqualTo(600)
        mvc.get("/api/v1/tours-operator/tours/by-slug?slug=$slug&locale=ar").andExpect {
            status { isOk() }
            jsonPath("$.cover.path") { value(path) }
            jsonPath("$.cover.width") { value(800) }
            jsonPath("$.cover.alt") { value("Synthetic image") }
        }
        mvc.get(path.replace(slug, "wrong-tour")).andExpect { status { isNotFound() } }
        mvc.get(path.replace(".jpg", ".png")).andExpect { status { isNotFound() } }
        mvc.get("$path?v=unknown").andExpect { status { isNotFound() } }
        dsl
            .update(TOURS_OPERATOR_TOUR)
            .set(TOURS_OPERATOR_TOUR.IS_ACTIVE, false)
            .where(TOURS_OPERATOR_TOUR.ID.eq(id))
            .execute()
        mvc.get(path).andExpect { status { isNotFound() } }
    }

    @Test fun `replacement requires reviewed revision resets approval and unlinks old asset`() {
        assertThat(upload().response.status).isEqualTo(200)
        approve()
        val previous = media()
        val params = mapOf("mediaId" to previous["id"].asText(), "revision" to "stale")
        assertThat(upload(params = params).response.status).isEqualTo(409)
        assertThat(upload(params = params + ("revision" to previous["revision"].asText())).response.status).isEqualTo(200)
        mvc.get(previous["path"].asText()).andExpect { status { isNotFound() } }
        mvc.get(media()["path"].asText()).andExpect { status { isNotFound() } }
        assertThat(media()["rightsStatus"].asText()).isEqualTo("DRAFT")
    }

    @Test fun `managed dimensions and cross tour links cannot be forged through existing media API`() {
        assertThat(upload().response.status).isEqualTo(200)
        val m = media()
        val body =
            """
            [{"id":"${m["id"].asText()}","path":"${m["path"].asText()}",
              "width":1,"height":1,"isCover":true,"alt":{"en":"Synthetic"}}]
            """.trimIndent()
        mvc
            .put("/api/v1/tours-operator/staff/tours/$id/media?revision=${staff()["mediaRevision"].asText()}") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = body
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("managed_asset_mismatch") }
            }
        val other =
            dsl
                .select(
                    TOURS_OPERATOR_TOUR.ID,
                ).from(TOURS_OPERATOR_TOUR)
                .where(TOURS_OPERATOR_TOUR.ID.ne(id))
                .limit(1)
                .fetchOne()!!
                .value1()
        val otherRevision =
            json
                .readTree(
                    mvc
                        .get("/api/v1/tours-operator/staff/tours/$other/content") {
                            header("Authorization", "Bearer $token")
                        }.andReturn()
                        .response.contentAsString,
                )["mediaRevision"]
                .asText()
        mvc
            .put("/api/v1/tours-operator/staff/tours/$other/media?revision=$otherRevision") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = body.replace("\"width\":1,\"height\":1", "\"width\":800,\"height\":600")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("managed_asset_mismatch") }
            }
    }

    @Test fun `same request replays once and changed input cannot reuse it`() {
        val requestId = UUID.randomUUID().toString()
        val count = dsl.fetchCount(TOURS_OPERATOR_ASSET)
        val first = upload(params = mapOf("requestId" to requestId))
        val second = upload(params = mapOf("requestId" to requestId))
        assertThat(first.response.status).isEqualTo(200)
        assertThat(second.response.contentAsString).isEqualTo(first.response.contentAsString)
        assertThat(dsl.fetchCount(TOURS_OPERATOR_ASSET)).isEqualTo(count + 1)
        assertThat(staff()["media"].size()).isEqualTo(1)
        assertThat(upload(params = mapOf("requestId" to requestId, "altAr" to "Changed")).response.status).isEqualTo(400)
        assertThat(upload(bytes = image(600), params = mapOf("requestId" to requestId)).response.status).isEqualTo(400)
    }

    @Test fun `concurrent replay creates exactly one durable linked asset`() {
        val requestId = UUID.randomUUID().toString()
        val count = dsl.fetchCount(TOURS_OPERATOR_ASSET)
        val start = CountDownLatch(1)
        Executors.newFixedThreadPool(2).use { executor ->
            val tasks =
                (1..2).map {
                    executor.submit<org.springframework.test.web.servlet.MvcResult> {
                        check(start.await(10, TimeUnit.SECONDS))
                        upload(params = mapOf("requestId" to requestId))
                    }
                }
            start.countDown()
            val results = tasks.map { it.get(30, TimeUnit.SECONDS).response }
            assertThat(results.map { it.status }).containsExactly(200, 200)
            assertThat(results[0].contentAsString).isEqualTo(results[1].contentAsString)
        }
        assertThat(dsl.fetchCount(TOURS_OPERATOR_ASSET)).isEqualTo(count + 1)
        assertThat(staff()["media"].size()).isEqualTo(1)
    }

    @Test fun `stale list and missing revision cannot unlink a new managed upload`() {
        val stale = staff()["mediaRevision"].asText()
        assertThat(upload().response.status).isEqualTo(200)
        mvc
            .put("/api/v1/tours-operator/staff/tours/$id/media?revision=$stale") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = "[]"
            }.andExpect { status { isConflict() } }
        mvc
            .put("/api/v1/tours-operator/staff/tours/$id/media") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = "[]"
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error") { value("media_revision_required") }
            }
        assertThat(staff()["media"].size()).isEqualTo(1)
    }

    @Test fun `real rollback cleans new files while lost commit acknowledgement retains linked bytes`() {
        fun writer(runner: TransactionRunner) = MediaUploadService(tours, contents, assets, storage, processor, runner, Clock.systemUTC())
        val rolledBack = UUID.randomUUID()
        val rollback =
            object : TransactionRunner {
                override fun <T> runInTransaction(block: () -> T): T =
                    transactions.runInTransaction {
                        block()
                        error("Synthetic pre-commit failure")
                    }
            }
        val count = Files.walk(volume).use { paths -> paths.filter(Files::isRegularFile).count() }
        assertThatThrownBy {
            writer(rollback).uploadForTourMedia(TourId(id), null, image(), mapOf(ContentLocale.EN to "Test"), null, rolledBack)
        }.isInstanceOf(IllegalStateException::class.java)
        assertThat(assets.findByUploadRequest("tour_media", id.toString(), rolledBack)).isNull()
        assertThat(Files.walk(volume).use { paths -> paths.filter(Files::isRegularFile).count() }).isEqualTo(count)
        val committed = UUID.randomUUID()
        val acknowledgementFailure =
            object : TransactionRunner {
                override fun <T> runInTransaction(block: () -> T): T {
                    transactions.runInTransaction(block)
                    error("Synthetic lost commit acknowledgement")
                }
            }
        assertThatThrownBy {
            writer(acknowledgementFailure).uploadForTourMedia(TourId(id), null, image(), mapOf(ContentLocale.EN to "Test"), null, committed)
        }.isInstanceOf(IllegalStateException::class.java)
        val asset = requireNotNull(assets.findByUploadRequest("tour_media", id.toString(), committed))
        assertThat(contents.findMedia(TourId(id))).hasSize(1)
        assertThat(ImageIO.read(ByteArrayInputStream(requireNotNull(storage.read(asset.storageKey)))).width).isEqualTo(800)
        assertThat(asset.variants.all { storage.exists(it.storageKey) }).isTrue()
    }

    @Test fun `partial derivative write failure rolls back and cleans only new files`() {
        var writes = 0
        val failingStorage =
            object : AssetStorage by storage {
                override fun write(
                    storageKey: String,
                    bytes: ByteArray,
                ) {
                    if (++writes == 2) throw AssetStorageException("Synthetic second-write failure")
                    storage.write(storageKey, bytes)
                }
            }
        val before = Files.walk(volume).use { paths -> paths.filter(Files::isRegularFile).count() }
        val count = dsl.fetchCount(TOURS_OPERATOR_ASSET)
        val service = MediaUploadService(tours, contents, assets, failingStorage, processor, transactions, Clock.systemUTC())
        val result = service.uploadForTourMedia(TourId(id), null, image(), mapOf(ContentLocale.EN to "Test"), null, UUID.randomUUID())
        assertThat(result).isEqualTo(UploadResult.StorageFailed)
        assertThat(dsl.fetchCount(TOURS_OPERATOR_ASSET)).isEqualTo(count)
        assertThat(contents.findMedia(TourId(id))).isEmpty()
        assertThat(Files.walk(volume).use { paths -> paths.filter(Files::isRegularFile).count() }).isEqualTo(before)
    }

    @Test fun `fake empty oversized invalid alt and absent tour leave no registry rows`() {
        val count = dsl.fetchCount(TOURS_OPERATOR_ASSET)
        assertThat(upload("%PDF-fake".toByteArray()).response.status).isEqualTo(400)
        assertThat(upload(ByteArray(0)).response.status).isEqualTo(400)
        assertThat(upload(ByteArray(10 * 1024 * 1024 + 1)).response.status).isEqualTo(413)
        assertThat(upload(params = mapOf("altAr" to "x".repeat(201))).response.status).isEqualTo(400)
        assertThat(upload(url = "/api/v1/tours-operator/staff/tours/${UUID.randomUUID()}/media/upload").response.status).isEqualTo(404)
        assertThat(dsl.fetchCount(TOURS_OPERATOR_ASSET)).isEqualTo(count)
    }

    @Test fun `real servlet multipart file and request limits return 413 without error auth dispatch`() {
        val before = dsl.fetchCount(TOURS_OPERATOR_ASSET)
        val files = Files.walk(volume).use { paths -> paths.filter(Files::isRegularFile).count() }
        val client = HttpClient.newHttpClient()
        for (size in listOf(10 * 1024 * 1024 + 1, 11 * 1024 * 1024 + 1)) {
            val boundary = "media-test-${UUID.randomUUID()}"
            val prefix =
                (
                    "--$boundary\r\nContent-Disposition: form-data; name=\"requestId\"\r\n\r\n${UUID.randomUUID()}\r\n" +
                        "--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"synthetic.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n"
                ).toByteArray()
            val body = prefix + ByteArray(size) + "\r\n--$boundary--\r\n".toByteArray()
            val request =
                HttpRequest
                    .newBuilder(URI("http://127.0.0.1:$serverPort/api/v1/tours-operator/staff/tours/$id/media/upload"))
                    .header("Authorization", "Bearer $token")
                    .header("Content-Type", "multipart/form-data; boundary=$boundary")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            assertThat(response.statusCode()).isEqualTo(413)
            assertThat(json.readTree(response.body())["error"].asText()).isEqualTo("file_too_large")
            assertThat(response.headers().firstValue("Cache-Control").orElse("")).isEqualTo("no-store, private")
        }
        assertThat(dsl.fetchCount(TOURS_OPERATOR_ASSET)).isEqualTo(before)
        assertThat(Files.walk(volume).use { paths -> paths.filter(Files::isRegularFile).count() }).isEqualTo(files)
        assertThat(staff()["media"].size()).isZero()
    }

    @Test fun `category revisions reject unseen changes and rights guard exact category`() {
        val url = "/api/v1/tours-operator/staff/categories/SEA/media"

        fun current() =
            json.readTree(
                mvc
                    .get(url) { header("Authorization", "Bearer $token") }
                    .andReturn()
                    .response.contentAsString,
            )
        val revision = current()["revision"].asText()
        assertThat(upload(params = mapOf("revision" to revision), url = "$url/upload").response.status).isEqualTo(200)
        val draft = current()
        mvc.get(draft["path"].asText()).andExpect { status { isNotFound() } }

        fun publicCovers() =
            json.readTree(
                mvc
                    .get("/api/v1/tours-operator/categories/media?locale=ar")
                    .andExpect {
                        status { isOk() }
                    }.andReturn()
                    .response.contentAsString,
            )
        assertThat(publicCovers().any { it["category"].asText() == "SEA" }).isFalse()
        mvc.get("/api/v1/tours-operator/categories/media?locale=xx").andExpect { status { isBadRequest() } }
        mvc
            .post("$url/approve") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"revision":"$revision"}"""
            }.andExpect { status { isConflict() } }
        mvc
            .post("$url/approve") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content =
                    """{"revision":"${draft["revision"].asText()}"}"""
            }.andExpect { status { isNoContent() } }
        mvc.get(draft["path"].asText()).andExpect { status { isOk() } }
        val cover = publicCovers().single { it["category"].asText() == "SEA" }
        assertThat(cover["path"].asText()).isEqualTo(draft["path"].asText())
        assertThat(cover["alt"].asText()).isEqualTo("Synthetic image")
        assertThat(cover.has("revision")).isFalse()
        assertThat(cover.has("assetId")).isFalse()
        mvc.get(draft["path"].asText().replace("/SEA/", "/DESERT/")).andExpect { status { isNotFound() } }
        mvc
            .put("$url/alt") {
                header("Authorization", "Bearer $token")
                contentType = MediaType.APPLICATION_JSON
                content = """{"revision":"${draft["revision"].asText()}","altEn":"Changed caption"}"""
            }.andExpect { status { isNoContent() } }
        mvc.get(draft["path"].asText()).andExpect { status { isNotFound() } }
        assertThat(publicCovers().any { it["category"].asText() == "SEA" }).isFalse()
    }
}
