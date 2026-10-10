package com.wego.travelmarketplace

import com.wego.identity.application.PasswordHasher
import com.wego.identity.application.UserRepository
import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.User
import com.wego.identity.domain.UserId
import com.wego.identity.domain.UserStatus
import com.wego.travelmarketplace.application.CreateTravelRequestCommand
import com.wego.travelmarketplace.application.CreateTravelRequestResult
import com.wego.travelmarketplace.application.CreateTravelRequestService
import com.wego.travelmarketplace.application.NotificationRepository
import com.wego.travelmarketplace.application.SalesControlRepository
import com.wego.travelmarketplace.application.SalesControlService
import com.wego.travelmarketplace.application.ServiceRepository
import com.wego.travelmarketplace.application.TransactionRunner
import com.wego.travelmarketplace.application.TravelRequestAuditRecorder
import com.wego.travelmarketplace.application.TravelRequestRepository
import com.wego.travelmarketplace.application.UpdateSalesControlResult
import com.wego.travelmarketplace.domain.Money
import com.wego.travelmarketplace.domain.Service
import com.wego.travelmarketplace.domain.ServiceId
import com.wego.travelmarketplace.domain.TravelRequestCustomer
import com.wego.travelmarketplace.domain.TravelRequestSourceChannel
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
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
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class SalesControlHttpTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var passwordHasher: PasswordHasher

    @Autowired private lateinit var sales: SalesControlService

    @Autowired private lateinit var salesRepository: SalesControlRepository

    @Autowired private lateinit var services: ServiceRepository

    @Autowired private lateinit var requests: TravelRequestRepository

    @Autowired private lateinit var audit: TravelRequestAuditRecorder

    @Autowired private lateinit var notifications: NotificationRepository

    @Autowired private lateinit var transactions: TransactionRunner

    @Autowired private lateinit var clock: Clock

    @Autowired private lateinit var dsl: DSLContext
    private lateinit var actor: UUID
    private lateinit var token: String
    private lateinit var pair: Pair<String, String>
    private val password = "sales-control-fixture-password-123"

    @BeforeEach
    fun setup() {
        actor = seedUserIfNeeded("sales-manager@example.com", password, setOf("platform-admin"))
        token = login("sales-manager@example.com", password)
        sales.update(false, sales.current().version, null, actor)
        pair = publishedService(token, "STAFF_REVIEW")
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
        return requireNotNull(Regex(""""token"\s*:\s*"([^"]+)"""").find(body)) { "No token in: $body" }.groupValues[1]
    }

    private fun jsonField(
        body: String,
        field: String,
    ): String = requireNotNull(Regex(""""$field"\s*:\s*"([^"]+)"""").find(body)) { "No $field field in: $body" }.groupValues[1]

    /** Publishes one real service (DRAFT -> REVIEW -> APPROVED -> PUBLISHED) through the real HTTP API, returns (serviceId, optionId). */
    private fun publishedService(
        token: String,
        confirmationType: String = "INSTANT",
    ): Pair<String, String> {
        val categoryId =
            jsonField(
                mockMvc
                    .post("/api/v1/travel-marketplace/categories") {
                        header("Authorization", "Bearer $token")
                        contentType = MediaType.APPLICATION_JSON
                        content =
                            """{"code": "cat-${UUID.randomUUID().toString().take(
                                8,
                            )}", "name": {"en": "Transfers", "ar": "الانتقالات"}, "description": null, "displayOrder": 0}"""
                    }.andReturn()
                    .response.contentAsString,
                "id",
            )
        val createBody =
            mockMvc
                .post("/api/v1/travel-marketplace/services") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
                        {
                          "categoryId": "$categoryId",
                          "name": {"en": "Airport Transfer", "ar": "انتقال من المطار"},
                          "description": {"en": "A private transfer.", "ar": "انتقال خاص."},
                          "fulfilmentModel": "DIRECT",
                          "providerId": null,
                          "confirmationType": "$confirmationType",
                          "cancellationPolicy": {"en": "Free up to 24h before.", "ar": "إلغاء مجاني حتى 24 ساعة قبل الموعد."},
                          "pickupInfo": null,
                          "inclusions": null,
                          "exclusions": null,
                          "options": [{"id": null, "label": {"en": "Arrival", "ar": "استلام"}, "durationMinutes": 60, "maxParticipants": 3, "priceAmount": 700.00, "priceCurrency": "EGP", "priceBasis": "PER_VEHICLE"}],
                          "media": [{"id": null, "assetReference": "asset-001", "rightsEvidence": "Owner-supplied, rights confirmed 2026-08-01", "locale": "en"}]
                        }
                        """.trimIndent()
                }.andReturn()
                .response.contentAsString
        val serviceId = jsonField(createBody, "id")
        val optionId =
            requireNotNull(Regex(""""options"\s*:\s*\[\s*\{\s*"id"\s*:\s*"([^"]+)"""").find(createBody)) {
                "No option id in: $createBody"
            }.groupValues[1]

        mockMvc.post("/api/v1/travel-marketplace/services/$serviceId/submit-for-review") { header("Authorization", "Bearer $token") }
        mockMvc.post("/api/v1/travel-marketplace/services/$serviceId/approve") { header("Authorization", "Bearer $token") }
        mockMvc.post("/api/v1/travel-marketplace/services/$serviceId/publish") { header("Authorization", "Bearer $token") }

        return serviceId to optionId
    }

    private fun createRequestJson(
        serviceId: String,
        optionId: String,
        adults: Int = 2,
        expectedPriceAmount: String = "700.00", // matches publishedService()'s own hardcoded option price
    ) = """
        {
          "serviceId": "$serviceId",
          "serviceOptionId": "$optionId",
          "requestedDate": "${LocalDate.now().plusDays(10)}",
          "requestedTime": null,
          "adults": $adults,
          "children": 0,
          "hotelOrPickup": "Four Seasons Sharm",
          "locale": "en",
          "notes": null,
          "sourceChannel": "WEBSITE",
          "customer": {"name": "Nour", "phone": "+201001413469", "email": null},
          "expectedPriceAmount": $expectedPriceAmount,
          "expectedPriceCurrency": "EGP"
        }
        """.trimIndent()

    private fun change(
        paused: Boolean,
        version: Long = sales.current().version,
        reason: String = "staff-only note",
    ) = mockMvc.put("/api/v1/travel-marketplace/sales-control") {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = """{"requestsPaused":$paused,"expectedVersion":$version,"reason":"$reason"}"""
    }

    private fun create(
        key: String = UUID.randomUUID().toString(),
        channel: String = "WEBSITE",
    ) = mockMvc.post("/api/v1/travel-marketplace/public/requests") {
        header("Idempotency-Key", key)
        contentType = MediaType.APPLICATION_JSON
        content = createRequestJson(pair.first, pair.second).replace("WEBSITE", channel)
    }

    private fun count(table: String): Int = dsl.fetchOne("select count(*) from wego.$table")!!.get(0, Int::class.java)!!

    @Test
    fun `pause is private durable and blocks fresh website and mobile requests without side effects`() {
        change(true).andExpect {
            status { isOk() }
            jsonPath("$.updatedByUserId") { value(actor.toString()) }
        }
        mockMvc.get("/api/v1/travel-marketplace/public/sales-status").andExpect {
            status { isOk() }
            header { string("Cache-Control", "no-store") }
            content { json("""{"requestsOpen":false}""", true) }
        }
        val before = listOf(count("travel_request"), count("travel_request_audit_event"), count("travel_request_notification"))
        for (channel in listOf("WEBSITE", "MOBILE")) {
            create(channel = channel).andExpect {
                status { isServiceUnavailable() }
                jsonPath("$.error") { value("requests_paused") }
            }
        }
        assertThat(
            listOf(count("travel_request"), count("travel_request_audit_event"), count("travel_request_notification")),
        ).isEqualTo(before)
        assertThat(salesRepository.current().requestsPaused).isTrue()
        assertThat(
            dsl.fetchOne("select reason from wego.travel_sales_control_event order by version desc limit 1")!!.get(0, String::class.java),
        ).isEqualTo("staff-only note")
        change(false).andExpect { status { isOk() } }
        create().andExpect {
            status { isCreated() }
            jsonPath("$.status") { value("NEW") }
        }
    }

    @Test
    fun `existing retry tracking catalog and staff lifecycle work during pause`() {
        val key = UUID.randomUUID().toString()
        val body =
            create(key)
                .andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString
        val reference = jsonField(body, "reference")
        val id = requests.findByIdempotencyKey(key)!!.id.value
        change(true).andExpect { status { isOk() } }
        create(key).andExpect {
            status { isOk() }
            jsonPath("$.reference") { value(reference) }
        }
        mockMvc.get("/api/v1/travel-marketplace/public/requests/$reference").andExpect { status { isOk() } }
        mockMvc.get("/api/v1/travel-marketplace/public/services/${pair.first}").andExpect { status { isOk() } }
        for (action in listOf("start-review", "confirm", "complete")) {
            mockMvc
                .post("/api/v1/travel-marketplace/requests/$id/$action") { header("Authorization", "Bearer $token") }
                .andExpect { status { isOk() } }
        }
    }

    @Test
    fun `dedicated permission protects staff read and write independently of catalog management`() {
        seedUserIfNeeded("sales-no-permission@example.com", password, emptySet())
        val plain = login("sales-no-permission@example.com", password)
        val code = "sales-catalog-only"
        dsl.execute("insert into wego.identity_role(code, description) values (?, ?) on conflict do nothing", code, "fixture")
        dsl.execute(
            "insert into wego.identity_role_permission(role_code, permission_code) values (?, ?) on conflict do nothing",
            code,
            "service:manage",
        )
        seedUserIfNeeded("sales-catalog@example.com", password, setOf(code))
        val catalog = login("sales-catalog@example.com", password)
        val before = sales.current()
        for (unauthorized in listOf(null, plain, catalog)) {
            val expected = if (unauthorized == null) 401 else 403
            assertThat(
                mockMvc
                    .get("/api/v1/travel-marketplace/sales-control") {
                        unauthorized?.let { header("Authorization", "Bearer $it") }
                    }.andReturn()
                    .response.status,
            ).isEqualTo(expected)
            assertThat(
                mockMvc
                    .put("/api/v1/travel-marketplace/sales-control") {
                        unauthorized?.let { header("Authorization", "Bearer $it") }
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"requestsPaused":true,"expectedVersion":${before.version}}"""
                    }.andReturn()
                    .response.status,
            ).isEqualTo(expected)
        }
        assertThat(sales.current()).isEqualTo(before)
        mockMvc
            .get("/api/v1/travel-marketplace/sales-control") { header("Authorization", "Bearer $token") }
            .andExpect { status { isOk() } }
    }

    @Test
    fun `invalid flag version and reason are refused and stale edits cannot reopen sales`() {
        val before = sales.current()
        val events = count("travel_sales_control_event")
        for (body in listOf(
            """{"expectedVersion":${before.version}}""",
            """{"requestsPaused":null,"expectedVersion":${before.version}}""",
            """{"requestsPaused":true}""",
            """{"requestsPaused":true,"expectedVersion":-1}""",
            """{"requestsPaused":true,"expectedVersion":${before.version},"reason":"${"x".repeat(301)}"}""",
        )) {
            mockMvc
                .put("/api/v1/travel-marketplace/sales-control") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = body
                }.andExpect { status { isBadRequest() } }
        }
        assertThat(sales.current()).isEqualTo(before)
        assertThat(count("travel_sales_control_event")).isEqualTo(events)
        change(true, before.version).andExpect { status { isOk() } }
        change(false, before.version).andExpect {
            status { isConflict() }
            jsonPath("$.error") { value("version_conflict") }
        }
        assertThat(sales.current().requestsPaused).isTrue()
        assertThat(count("travel_sales_control_event")).isEqualTo(events + 1)
    }

    private fun command(key: String = UUID.randomUUID().toString()) =
        CreateTravelRequestCommand(
            ServiceId(UUID.fromString(pair.first)),
            UUID.fromString(pair.second),
            LocalDate.now().plusDays(10),
            null,
            2,
            0,
            null,
            "en",
            null,
            TravelRequestSourceChannel.WEBSITE,
            TravelRequestCustomer("Fixture guest", "+201000000001", null),
            key,
            null,
            Money(BigDecimal("700.00"), "EGP"),
        )

    private fun awaitDatabaseLock() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            if (dsl
                    .fetchOne(
                        """
                        select count(*) from pg_stat_activity
                        where datname=current_database() and wait_event_type='Lock'
                          and query ilike '%travel_sales_control%'
                        """.trimIndent(),
                    )!!
                    .get(0, Int::class.java)!! >
                0
            ) {
                return
            }
            Thread.yield()
        }
        error("No sales-control lock waiter observed in PostgreSQL")
    }

    @Test
    fun `V9 upgrades an existing V8 database without changing its existing records`() {
        postgres.createConnection("").use { connection ->
            connection.createStatement().use { it.execute("create database stg_sales_upgrade") }
        }
        val url = postgres.jdbcUrl.replace("/stg_sales_test", "/stg_sales_upgrade")
        Flyway
            .configure()
            .dataSource(url, postgres.username, postgres.password)
            .target("8")
            .load()
            .migrate()
        java.sql.DriverManager.getConnection(url, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("insert into wego.identity_role(code, description) values ('historic-role', 'preserve this row')")
            }
        }
        Flyway
            .configure()
            .dataSource(url, postgres.username, postgres.password)
            .load()
            .migrate()
        java.sql.DriverManager.getConnection(url, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("select requests_paused, version from wego.travel_sales_control").use {
                    check(it.next())
                    assertThat(it.getBoolean(1)).isFalse()
                    assertThat(it.getLong(2)).isZero()
                    assertThat(it.next()).isFalse()
                }
                statement.executeQuery("select description from wego.identity_role where code='historic-role'").use {
                    check(it.next())
                    assertThat(it.getString(1)).isEqualTo("preserve this row")
                }
                statement
                    .executeQuery(
                        "select count(*) from wego.identity_role_permission where permission_code='travel-sales:manage'",
                    ).use {
                        check(it.next())
                        assertThat(it.getInt(1)).isEqualTo(1)
                    }
            }
        }
    }

    @Test
    fun `pause waits for an in-flight request commit then all later creates are refused`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val heldServices =
            object : ServiceRepository by services {
                override fun findPublishedById(id: ServiceId): Service? {
                    entered.countDown()
                    check(release.await(15, TimeUnit.SECONDS))
                    return services.findPublishedById(id)
                }
            }
        val creator = CreateTravelRequestService(heldServices, requests, audit, notifications, transactions, clock, salesRepository)
        val pool = Executors.newFixedThreadPool(4)
        val version = sales.current().version
        try {
            val creating = pool.submit<CreateTravelRequestResult> { creator.create(command()) }
            check(entered.await(10, TimeUnit.SECONDS))
            val pausing = pool.submit<UpdateSalesControlResult> { sales.update(true, version, null, actor) }
            awaitDatabaseLock()
            assertThat(pausing.isDone).isFalse()
            release.countDown()
            assertThat(creating.get(15, TimeUnit.SECONDS)).isInstanceOf(CreateTravelRequestResult.Created::class.java)
            assertThat(pausing.get(15, TimeUnit.SECONDS)).isInstanceOf(UpdateSalesControlResult.Updated::class.java)
            val count = count("travel_request")
            val attempts =
                (1..12).map {
                    pool.submit<CreateTravelRequestResult> {
                        CreateTravelRequestService(
                            services,
                            requests,
                            audit,
                            notifications,
                            transactions,
                            clock,
                            salesRepository,
                        ).create(command())
                    }
                }
            assertThat(attempts.map { it.get(15, TimeUnit.SECONDS) }).allMatch { it == CreateTravelRequestResult.RequestsPaused }
            assertThat(count("travel_request")).isEqualTo(count)
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun `a new request queued behind the pause observes the committed paused state`() {
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val version = sales.current().version
        val creator = CreateTravelRequestService(services, requests, audit, notifications, transactions, clock, salesRepository)
        try {
            val pause =
                pool.submit<UpdateSalesControlResult> {
                    transactions.runInTransaction {
                        salesRepository.lockForUpdate()
                        locked.countDown()
                        check(release.await(15, TimeUnit.SECONDS))
                        sales.update(true, version, null, actor)
                    }
                }
            check(locked.await(10, TimeUnit.SECONDS))
            val create = pool.submit<CreateTravelRequestResult> { creator.create(command()) }
            awaitDatabaseLock()
            release.countDown()
            assertThat(pause.get(15, TimeUnit.SECONDS)).isInstanceOf(UpdateSalesControlResult.Updated::class.java)
            assertThat(create.get(15, TimeUnit.SECONDS)).isEqualTo(CreateTravelRequestResult.RequestsPaused)
        } finally {
            release.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun `a failed flag update rolls back its audit history too`() {
        val before = sales.current()
        val events = count("travel_sales_control_event")
        try {
            transactions.runInTransaction {
                sales.update(true, before.version, "not committed", actor)
                error("forced rollback")
            }
        } catch (_: IllegalStateException) {
        }
        assertThat(sales.current()).isEqualTo(before)
        assertThat(count("travel_sales_control_event")).isEqualTo(events)
    }

    companion object {
        @Container @JvmStatic
        val postgres =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("stg_sales_test")
                .withUsername("wego_test")
                .withPassword("wego_test")

        @DynamicPropertySource @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.flyway.enabled") { true }
        }
    }
}
