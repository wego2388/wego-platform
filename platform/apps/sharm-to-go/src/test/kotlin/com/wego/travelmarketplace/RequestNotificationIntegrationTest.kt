package com.wego.travelmarketplace

import com.wego.identity.application.PasswordHasher
import com.wego.identity.application.UserRepository
import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.User
import com.wego.identity.domain.UserId
import com.wego.identity.domain.UserStatus
import com.wego.travelmarketplace.application.CancelTravelRequestCommand
import com.wego.travelmarketplace.application.CancelTravelRequestResult
import com.wego.travelmarketplace.application.CancelTravelRequestService
import com.wego.travelmarketplace.application.CategoryRepository
import com.wego.travelmarketplace.application.ConfirmTravelRequestResult
import com.wego.travelmarketplace.application.ConfirmTravelRequestService
import com.wego.travelmarketplace.application.CreateTravelRequestCommand
import com.wego.travelmarketplace.application.CreateTravelRequestResult
import com.wego.travelmarketplace.application.CreateTravelRequestService
import com.wego.travelmarketplace.application.DispatchNotificationsService
import com.wego.travelmarketplace.application.EmailSender
import com.wego.travelmarketplace.application.NotificationRepository
import com.wego.travelmarketplace.application.NotificationSettings
import com.wego.travelmarketplace.application.ServiceRepository
import com.wego.travelmarketplace.application.TransactionRunner
import com.wego.travelmarketplace.application.TravelRequestRepository
import com.wego.travelmarketplace.domain.Category
import com.wego.travelmarketplace.domain.CategoryId
import com.wego.travelmarketplace.domain.ConfirmationType
import com.wego.travelmarketplace.domain.FulfilmentModel
import com.wego.travelmarketplace.domain.LocalizedText
import com.wego.travelmarketplace.domain.Money
import com.wego.travelmarketplace.domain.NotificationKind
import com.wego.travelmarketplace.domain.NotificationStatus
import com.wego.travelmarketplace.domain.PriceBasis
import com.wego.travelmarketplace.domain.Service
import com.wego.travelmarketplace.domain.ServiceId
import com.wego.travelmarketplace.domain.ServiceMedia
import com.wego.travelmarketplace.domain.ServiceOption
import com.wego.travelmarketplace.domain.ServiceStatus
import com.wego.travelmarketplace.domain.TravelRequestActorType
import com.wego.travelmarketplace.domain.TravelRequestCancelReason
import com.wego.travelmarketplace.domain.TravelRequestCustomer
import com.wego.travelmarketplace.domain.TravelRequestId
import com.wego.travelmarketplace.domain.TravelRequestSourceChannel
import com.wego.travelmarketplace.domain.TravelRequestStatus
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
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
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * STG-NOTIFY against real PostgreSQL: the notification intent is written in
 * the same transaction as the request transition (and rolls back with it),
 * duplicates are impossible, the dispatcher's SKIP LOCKED claim lets several
 * dispatchers work in parallel without sending anything twice, and neither
 * the table nor the logs hold an address or a message body.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension::class)
class RequestNotificationIntegrationTest {
    @Autowired private lateinit var categoryRepository: CategoryRepository

    @Autowired private lateinit var serviceRepository: ServiceRepository

    @Autowired private lateinit var requestRepository: TravelRequestRepository

    @Autowired private lateinit var notificationRepository: NotificationRepository

    @Autowired private lateinit var createService: CreateTravelRequestService

    @Autowired private lateinit var confirmService: ConfirmTravelRequestService

    @Autowired private lateinit var cancelService: CancelTravelRequestService

    @Autowired private lateinit var transactionRunner: TransactionRunner

    @Autowired private lateinit var dsl: DSLContext

    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var passwordHasher: PasswordHasher

    private lateinit var staffUserId: UUID
    private var instantServiceId: ServiceId = ServiceId(UUID.randomUUID())
    private lateinit var instantOptionId: UUID
    private var reviewServiceId: ServiceId = ServiceId(UUID.randomUUID())
    private lateinit var reviewOptionId: UUID

    private val settings =
        NotificationSettings("https://sharmtogo.example", "staff@sharmtogo.example", "+20 10 0141 3469", "info@sharmtogo.com", 3)

    @BeforeEach
    fun seed() {
        dsl.deleteFrom(DSL.table(DSL.name("wego", "travel_request_notification"))).execute()
        val staffEmail = "stg-notify-${UUID.randomUUID().toString().take(8)}@example.com"
        val user =
            User(
                id = UserId.generate(),
                email = EmailAddress.of(staffEmail),
                passwordHash = passwordHasher.hash(STAFF_PASSWORD),
                status = UserStatus.ACTIVE,
                roles = setOf(RoleCode.of("platform-admin")),
                createdAt = Instant.now(),
                failedLoginCount = 0,
                lockedUntil = null,
            )
        userRepository.save(user)
        staffUserId = user.id.value
        staffLoginEmail = staffEmail

        val category =
            Category.create(
                CategoryId.generate(),
                "notify-${UUID.randomUUID().toString().take(8)}",
                LocalizedText("Tours", "رحلات"),
                null,
                0,
                Instant.now(),
            )
        categoryRepository.save(category)
        val instant = publish(category.id, ConfirmationType.INSTANT, BigDecimal("700.00"))
        instantServiceId = instant.id
        instantOptionId = instant.options.single().id
        val review = publish(category.id, ConfirmationType.STAFF_REVIEW, BigDecimal("900.00"))
        reviewServiceId = review.id
        reviewOptionId = review.options.single().id
    }

    private lateinit var staffLoginEmail: String

    private fun publish(
        categoryId: CategoryId,
        confirmation: ConfirmationType,
        price: BigDecimal,
    ): Service {
        val now = Instant.now()
        val service =
            Service(
                id = ServiceId.generate(),
                categoryId = categoryId,
                name = LocalizedText("Test service", "خدمة اختبار"),
                description = LocalizedText("A test service.", "خدمة اختبار."),
                fulfilmentModel = FulfilmentModel.DIRECT,
                providerId = null,
                confirmationType = confirmation,
                cancellationPolicy = LocalizedText("Free.", "مجاني."),
                pickupInfo = null,
                inclusions = null,
                exclusions = null,
                options =
                    listOf(
                        ServiceOption(
                            UUID.randomUUID(),
                            LocalizedText("Option", "خيار"),
                            60,
                            4,
                            Money(price, "EGP"),
                            PriceBasis.PER_VEHICLE,
                        ),
                    ),
                media = listOf(ServiceMedia(UUID.randomUUID(), "seed.jpg", "fixture", "en")),
                status = ServiceStatus.PUBLISHED,
                createdAt = now,
                publishedAt = now,
                archivedAt = null,
                version = 1,
            )
        serviceRepository.save(service)
        return service
    }

    private fun command(
        serviceId: ServiceId,
        optionId: UUID,
        email: String?,
        key: String = UUID.randomUUID().toString(),
        price: String = if (serviceId == instantServiceId) "700.00" else "900.00",
    ) = CreateTravelRequestCommand(
        serviceId = serviceId,
        serviceOptionId = optionId,
        requestedDate = LocalDate.now().plusDays(10),
        requestedTime = null,
        adults = 2,
        children = 0,
        hotelOrPickup = null,
        locale = "en",
        notes = null,
        sourceChannel = TravelRequestSourceChannel.WEBSITE,
        customer = TravelRequestCustomer(name = "Nour", phone = "+201001413469", email = email),
        idempotencyKey = key,
        correlationId = null,
        expectedPrice = Money(BigDecimal(price), "EGP"),
    )

    private fun createReview(email: String? = uniqueEmail()): TravelRequestId {
        val result = createService.create(command(reviewServiceId, reviewOptionId, email))
        check(result is CreateTravelRequestResult.Created)
        return result.request.id
    }

    private fun uniqueEmail() = "nour-${UUID.randomUUID().toString().take(8)}@example.com"

    private fun kinds(id: TravelRequestId): List<NotificationKind> =
        dsl
            .select(DSL.field(DSL.name("kind"), String::class.java))
            .from(DSL.table(DSL.name("wego", "travel_request_notification")))
            .where(DSL.field(DSL.name("request_id"), UUID::class.java).eq(id.value))
            .fetch { NotificationKind.valueOf(it.value1()) }

    private fun rowCount(): Int = dsl.fetchCount(DSL.table(DSL.name("wego", "travel_request_notification")))

    @Test
    fun `creating a request with an email writes the staff alert and the customer receipt in the same transaction`() {
        val id = createReview()
        assertThat(kinds(id)).containsExactlyInAnyOrder(NotificationKind.STAFF_NEW_REQUEST, NotificationKind.CUSTOMER_REQUEST_RECEIVED)
    }

    @Test
    fun `a request without an email only alerts staff`() {
        val id = createReview(email = null)
        assertThat(kinds(id)).containsExactly(NotificationKind.STAFF_NEW_REQUEST)
    }

    @Test
    fun `an auto-confirmed INSTANT request gets one customer email, not a receipt plus a confirmation`() {
        val result = createService.create(command(instantServiceId, instantOptionId, uniqueEmail()))
        check(result is CreateTravelRequestResult.Created)
        assertThat(result.request.status).isEqualTo(TravelRequestStatus.CONFIRMED)
        assertThat(
            kinds(result.request.id),
        ).containsExactlyInAnyOrder(NotificationKind.STAFF_NEW_REQUEST, NotificationKind.CUSTOMER_REQUEST_RECEIVED)
    }

    @Test
    fun `a rejected create and an idempotent replay leave no extra notification rows`() {
        val key = UUID.randomUUID().toString()
        val stale = createService.create(command(reviewServiceId, reviewOptionId, uniqueEmail(), price = "1.00"))
        assertThat(stale).isInstanceOf(CreateTravelRequestResult.PriceChanged::class.java)
        assertThat(rowCount()).isZero()

        val first = createService.create(command(reviewServiceId, reviewOptionId, uniqueEmail(), key))
        val replay = createService.create(command(reviewServiceId, reviewOptionId, uniqueEmail(), key))
        check(first is CreateTravelRequestResult.Created)
        check(replay is CreateTravelRequestResult.AlreadyExists)
        assertThat(rowCount()).isEqualTo(2)
    }

    @Test
    fun `concurrent creates with the same idempotency key produce exactly one set of notifications`() {
        val key = UUID.randomUUID().toString()
        val email = uniqueEmail()
        val threads = 8
        val pool = Executors.newFixedThreadPool(threads)
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)
        val futures =
            (1..threads).map {
                pool.submit(
                    Callable {
                        ready.countDown()
                        go.await()
                        createService.create(command(reviewServiceId, reviewOptionId, email, key))
                    },
                )
            }
        ready.await(10, TimeUnit.SECONDS)
        go.countDown()
        futures.forEach { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()

        assertThat(requestRepository.findByIdempotencyKey(key)).isNotNull()
        assertThat(rowCount()).isEqualTo(2)
    }

    @Test
    fun `confirm and cancel each write their email intent once, and replays add nothing`() {
        val id = createReview()
        check(confirmService.confirm(id, staffUserId, null) is ConfirmTravelRequestResult.Confirmed)
        assertThat(kinds(id)).contains(NotificationKind.CUSTOMER_REQUEST_CONFIRMED)
        assertThat(confirmService.confirm(id, staffUserId, null)).isEqualTo(ConfirmTravelRequestResult.InvalidTransition)

        val cancel = CancelTravelRequestCommand(id, TravelRequestCancelReason.OTHER, null, TravelRequestActorType.STAFF, staffUserId, null)
        check(cancelService.cancel(cancel) is CancelTravelRequestResult.Cancelled)
        assertThat(cancelService.cancel(cancel)).isEqualTo(CancelTravelRequestResult.AlreadyTerminal)
        assertThat(kinds(id)).containsExactlyInAnyOrder(
            NotificationKind.STAFF_NEW_REQUEST,
            NotificationKind.CUSTOMER_REQUEST_RECEIVED,
            NotificationKind.CUSTOMER_REQUEST_CONFIRMED,
            NotificationKind.CUSTOMER_REQUEST_CANCELLED,
        )
    }

    @Test
    fun `the notification rolls back together with the transition it belongs to`() {
        val id = createReview()
        assertThat(notificationRepository.exists(id, NotificationKind.CUSTOMER_REQUEST_CONFIRMED)).isFalse()

        runCatching {
            transactionRunner.runInTransaction {
                check(confirmService.confirm(id, staffUserId, null) is ConfirmTravelRequestResult.Confirmed)
                error("simulated failure after the transition, before commit")
            }
        }

        assertThat(requestRepository.findById(id)?.status).isEqualTo(TravelRequestStatus.NEW)
        assertThat(notificationRepository.exists(id, NotificationKind.CUSTOMER_REQUEST_CONFIRMED)).isFalse()
    }

    @Test
    fun `parallel dispatchers claim with SKIP LOCKED, so each row is sent once and sends overlap`() {
        repeat(12) { createReview() }
        val pending = rowCount()
        assertThat(pending).isEqualTo(24)

        val sends = AtomicInteger()
        val inFlight = AtomicInteger()
        val maxInFlight = AtomicInteger()
        val sender =
            EmailSender {
                val now = inFlight.incrementAndGet()
                maxInFlight.accumulateAndGet(now, ::maxOf)
                Thread.sleep(150)
                inFlight.decrementAndGet()
                sends.incrementAndGet()
            }
        val dispatcher =
            DispatchNotificationsService(notificationRepository, requestRepository, sender, transactionRunner, settings, Clock.systemUTC())

        val pool = Executors.newFixedThreadPool(4)
        val futures = (1..4).map { pool.submit(Callable { dispatcher.dispatchDue(100) }) }
        val processed = futures.sumOf { it.get(60, TimeUnit.SECONDS) }
        pool.shutdown()

        assertThat(processed).isEqualTo(pending)
        assertThat(sends.get()).isEqualTo(pending)
        assertThat(maxInFlight.get()).isGreaterThan(1) // SKIP LOCKED let dispatchers work on different rows at once
        assertThat(
            dsl.fetchCount(
                DSL.table(DSL.name("wego", "travel_request_notification")),
                DSL.field(DSL.name("status"), String::class.java).eq("SENT"),
            ),
        ).isEqualTo(pending)
    }

    @Test
    fun `neither the table nor the logs hold an address, a phone number or a message body`(output: CapturedOutput) {
        val email = uniqueEmail()
        val id = createReview(email)
        check(confirmService.confirm(id, staffUserId, null) is ConfirmTravelRequestResult.Confirmed)
        val failing = EmailSender { throw IllegalStateException("550 rejected $email +201001413469") }
        val dispatcher =
            DispatchNotificationsService(notificationRepository, requestRepository, failing, transactionRunner, settings, Clock.systemUTC())
        dispatcher.dispatchDue(100)

        val columns =
            dsl
                .select(DSL.field(DSL.name("column_name"), String::class.java))
                .from(DSL.table(DSL.name("information_schema", "columns")))
                .where(DSL.field(DSL.name("table_name")).eq("travel_request_notification"))
                .fetch { it.value1() }
        assertThat(columns).noneMatch { it.contains("email") || it.contains("phone") || it.contains("body") || it.contains("recipient") }

        val stored = dsl.fetch("select last_error, kind, status from wego.travel_request_notification").toString()
        assertThat(stored).doesNotContain("@", email, "201001413469", "Nour")
        assertThat(output.all).doesNotContain(email, "201001413469")
        assertThat(output.all).contains("IllegalStateException")
    }

    @Test
    fun `the database refuses a free-text last_error`() {
        val id = createReview()
        val attempt =
            runCatching {
                dsl.execute(
                    "update wego.travel_request_notification set last_error = ? where request_id = ?",
                    "to nour@example.com",
                    id.value,
                )
            }
        assertThat(attempt.isFailure).isTrue()
    }

    private fun login(): String {
        val body =
            mockMvc
                .post("/api/v1/identity/login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"$staffLoginEmail","password":"$STAFF_PASSWORD"}"""
                }.andReturn()
                .response.contentAsString
        return requireNotNull(Regex(""""token"\s*:\s*"([^"]+)"""").find(body)) { "No token in: $body" }.groupValues[1]
    }

    @Test
    fun `staff list shows the reference and status but never an address, and resend queues it again`() {
        val email = uniqueEmail()
        val id = createReview(email)
        val reference = requestRepository.findById(id)!!.reference.value
        val token = login()

        mockMvc.get("/api/v1/travel-marketplace/notifications") { header("Authorization", "Bearer $token") }.andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString(reference)) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("@"))) }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(email))) }
        }
        mockMvc.get("/api/v1/travel-marketplace/notifications?status=SENT") { header("Authorization", "Bearer $token") }.andExpect {
            status { isOk() }
            content { json("[]") }
        }

        val notification =
            dsl.fetch(
                "select id from wego.travel_request_notification where request_id = ? and kind = 'CUSTOMER_REQUEST_RECEIVED'",
                id.value,
            )
        val notificationId = notification[0].get("id", UUID::class.java)
        dsl.execute("update wego.travel_request_notification set status = 'FAILED', attempt_count = 3 where id = ?", notificationId)

        mockMvc
            .post(
                "/api/v1/travel-marketplace/notifications/$notificationId/resend",
            ) { header("Authorization", "Bearer $token") }
            .andExpect {
                status { isAccepted() }
            }
        val after = dsl.fetch("select status, resend_count from wego.travel_request_notification where id = ?", notificationId)
        assertThat(after[0].get("status", String::class.java)).isEqualTo(NotificationStatus.PENDING.name)
        assertThat(after[0].get("resend_count", Int::class.java)).isEqualTo(1)

        mockMvc
            .post("/api/v1/travel-marketplace/notifications/${UUID.randomUUID()}/resend") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isNotFound() }
            }
    }

    @Test
    fun `resending an email that is no longer true is refused, and the endpoints need the permission`() {
        val id = createReview()
        val token = login()
        cancelService.cancel(
            CancelTravelRequestCommand(id, TravelRequestCancelReason.OTHER, null, TravelRequestActorType.STAFF, staffUserId, null),
        )
        val notificationId =
            dsl
                .fetch(
                    "select id from wego.travel_request_notification where request_id = ? and kind = 'CUSTOMER_REQUEST_RECEIVED'",
                    id.value,
                )[0]
                .get("id", UUID::class.java)

        mockMvc
            .post(
                "/api/v1/travel-marketplace/notifications/$notificationId/resend",
            ) { header("Authorization", "Bearer $token") }
            .andExpect {
                status { isConflict() }
            }
        mockMvc.get("/api/v1/travel-marketplace/notifications").andExpect { status { isUnauthorized() } }

        val plain =
            User(
                id = UserId.generate(),
                email = EmailAddress.of("no-perm-${UUID.randomUUID().toString().take(8)}@example.com"),
                passwordHash = passwordHasher.hash(STAFF_PASSWORD),
                status = UserStatus.ACTIVE,
                roles = emptySet(),
                createdAt = Instant.now(),
                failedLoginCount = 0,
                lockedUntil = null,
            )
        userRepository.save(plain)
        val body =
            mockMvc
                .post("/api/v1/identity/login") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"email":"${plain.email.value}","password":"$STAFF_PASSWORD"}"""
                }.andReturn()
                .response.contentAsString
        val plainToken = Regex(""""token"\s*:\s*"([^"]+)"""").find(body)!!.groupValues[1]
        mockMvc
            .get("/api/v1/travel-marketplace/notifications") {
                header("Authorization", "Bearer $plainToken")
            }.andExpect { status { isForbidden() } }
    }

    companion object {
        private const val STAFF_PASSWORD = "a-very-long-staff-password-123"

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
