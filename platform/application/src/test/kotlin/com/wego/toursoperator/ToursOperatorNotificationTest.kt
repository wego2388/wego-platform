package com.wego.toursoperator

import com.wego.generated.jooq.tables.ToursOperatorNotification.TOURS_OPERATOR_NOTIFICATION
import com.wego.generated.jooq.tables.ToursOperatorPayment.TOURS_OPERATOR_PAYMENT
import com.wego.generated.jooq.tables.ToursOperatorTour.TOURS_OPERATOR_TOUR
import com.wego.generated.jooq.tables.ToursOperatorTourSlot.TOURS_OPERATOR_TOUR_SLOT
import com.wego.identity.application.PasswordHasher
import com.wego.identity.application.UserRepository
import com.wego.identity.domain.EmailAddress
import com.wego.identity.domain.RoleCode
import com.wego.identity.domain.User
import com.wego.identity.domain.UserId
import com.wego.identity.domain.UserStatus
import com.wego.toursoperator.application.DispatchNotificationsService
import com.wego.toursoperator.application.EmailMessage
import com.wego.toursoperator.application.EmailSender
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mail.MailSendException
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.convention.TestBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * WEGO-016-G: customer notifications are queued with the booking transition,
 * sent exactly once by the dispatcher, retried on failure, and never expose
 * the customer's address to staff APIs or logs.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension::class)
class ToursOperatorNotificationTest {
    @Autowired private lateinit var mockMvc: MockMvc

    @Autowired private lateinit var userRepository: UserRepository

    @Autowired private lateinit var passwordHasher: PasswordHasher

    @Autowired private lateinit var dsl: DSLContext

    @Autowired private lateinit var dispatcher: DispatchNotificationsService

    @TestBean(name = "stoEmailSender", methodName = "recordingSender")
    private lateinit var emailSender: EmailSender

    private val recorder get() = emailSender as RecordingEmailSender

    private val adminEmail = "sts-notify-admin@example.com"
    private val adminPassword = "a-very-long-admin-password-notify-1"
    private val viewerEmail = "sts-notify-viewer@example.com"
    private val viewerPassword = "a-very-long-viewer-password-notify-1"
    private val customerEmail = "guest.notify@example.com"

    @BeforeEach
    fun setUp() {
        recorder.reset()
        seedUser(adminEmail, adminPassword, setOf("platform-admin"))
        seedUser(viewerEmail, viewerPassword, emptySet())
        // Deliver everything already queued by earlier tests so each test
        // observes only its own notifications.
        dispatcher.dispatchDue(1_000)
        recorder.reset()
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun seedUser(
        email: String,
        password: String,
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
        return requireNotNull(Regex(""""token"\s*:\s*"([^"]+)"""").find(body)) { "No token: $body" }.groupValues[1]
    }

    private fun seedSlot(): UUID {
        val tourId = UUID.randomUUID()
        val slotId = UUID.randomUUID()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        dsl
            .insertInto(TOURS_OPERATOR_TOUR)
            .set(TOURS_OPERATOR_TOUR.ID, tourId)
            .set(TOURS_OPERATOR_TOUR.SLUG, "sts-notify-${tourId.toString().take(8)}")
            .set(TOURS_OPERATOR_TOUR.NAME_EN, "Notify Test Safari")
            .set(TOURS_OPERATOR_TOUR.CATEGORY, "DESERT")
            .set(TOURS_OPERATOR_TOUR.DURATION_TEXT, "4 hours")
            .set(TOURS_OPERATOR_TOUR.PRICE_ADULT_CENTS, 3500L)
            .set(TOURS_OPERATOR_TOUR.CAPACITY, 20)
            .set(TOURS_OPERATOR_TOUR.AVAILABLE_TIME_SLOTS, "MORNING")
            .set(TOURS_OPERATOR_TOUR.SORT_ORDER, 1)
            .set(TOURS_OPERATOR_TOUR.IS_ACTIVE, true)
            .set(TOURS_OPERATOR_TOUR.CREATED_AT, now)
            .execute()
        dsl
            .insertInto(TOURS_OPERATOR_TOUR_SLOT)
            .set(TOURS_OPERATOR_TOUR_SLOT.ID, slotId)
            .set(TOURS_OPERATOR_TOUR_SLOT.TOUR_ID, tourId)
            .set(TOURS_OPERATOR_TOUR_SLOT.DATE, LocalDate.of(2027, 7, 1))
            .set(TOURS_OPERATOR_TOUR_SLOT.TIME_SLOT, "MORNING")
            .set(TOURS_OPERATOR_TOUR_SLOT.CAPACITY, 20)
            .set(TOURS_OPERATOR_TOUR_SLOT.BOOKED_COUNT, 0)
            .set(TOURS_OPERATOR_TOUR_SLOT.IS_BLOCKED, false)
            .set(TOURS_OPERATOR_TOUR_SLOT.CREATED_AT, now)
            .execute()
        return slotId
    }

    /** Creates a booking (1 adult, €35) and returns (id, reference). */
    private fun createBooking(
        email: String? = customerEmail,
        locale: String = "en",
    ): Pair<String, String> {
        val emailJson = email?.let { "\"$it\"" } ?: "null"
        val body =
            mockMvc
                .post("/api/v1/tours-operator/bookings") {
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
                        {"slotId":"${seedSlot()}","adultsCount":1,"childrenCount":0,
                         "customer":{"fullName":"Notify Guest","phone":"+201000000777",
                                     "nationality":"EG","email":$emailJson},
                         "hotelName":"Test Hotel","locale":"$locale"}
                        """.trimIndent()
                }.andExpect { status { isCreated() } }
                .andReturn()
                .response.contentAsString

        fun field(name: String) = Regex(""""$name"\s*:\s*"([^"]+)"""").find(body)!!.groupValues[1]
        return field("id") to field("reference")
    }

    private fun payAndConfirm(bookingId: String) {
        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/pay") {
                contentType = MediaType.APPLICATION_JSON
                content = "{}"
            }.andExpect { status { isCreated() } }
        val orderId =
            dsl
                .select(TOURS_OPERATOR_PAYMENT.PAYMOB_ORDER_ID)
                .from(TOURS_OPERATOR_PAYMENT)
                .where(TOURS_OPERATOR_PAYMENT.BOOKING_ID.eq(UUID.fromString(bookingId)))
                .fetchOne(TOURS_OPERATOR_PAYMENT.PAYMOB_ORDER_ID)!!
        val callback =
            """
            {"obj":{"id":"TXN-${UUID.randomUUID()}","success":true,"pending":false,"is_refunded":false,
             "error_occured":false,"has_parent_transaction":false,"is_3d_secure":true,"is_auth":false,
             "is_capture":false,"is_standalone_payment":true,"is_voided":false,"owner":"100001",
             "amount_cents":3500,"currency":"EUR","created_at":"2026-09-29T00:00:00Z",
             "integration_id":"100001","order":{"id":"$orderId"},
             "source_data":{"pan":"1234","sub_type":"MasterCard","type":"card"},
             "data":{"txn_response_code":"APPROVED"}}}
            """.trimIndent()
        // Delivered twice on purpose: a replayed webhook must not queue a second email.
        repeat(2) {
            mockMvc
                .post("/api/v1/tours-operator/payments/paymob-callback") {
                    param("hmac", "valid-hmac")
                    contentType = MediaType.APPLICATION_JSON
                    content = callback
                }.andExpect { status { isOk() } }
        }
    }

    private fun notificationRows(bookingId: String) =
        dsl
            .selectFrom(TOURS_OPERATOR_NOTIFICATION)
            .where(TOURS_OPERATOR_NOTIFICATION.BOOKING_ID.eq(UUID.fromString(bookingId)))
            .fetch()

    private fun sentMessages(count: Int): List<EmailMessage> {
        assertThat(recorder.sent).hasSize(count)
        return recorder.sent.toList()
    }

    // ── tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `a confirmed booking queues one confirmation that is sent exactly once`(output: CapturedOutput) {
        val (bookingId, reference) = createBooking()
        payAndConfirm(bookingId)

        val rows = notificationRows(bookingId)
        assertThat(rows).hasSize(1)
        assertThat(rows[0].kind).isEqualTo("BOOKING_CONFIRMED")
        assertThat(rows[0].status).isEqualTo("PENDING")

        assertThat(dispatcher.dispatchDue(100)).isEqualTo(1)
        assertThat(dispatcher.dispatchDue(100)).isEqualTo(0)

        val message = sentMessages(1).single()
        assertThat(message.to).isEqualTo(customerEmail)
        assertThat(message.subject).contains(reference)
        assertThat(message.body).contains(reference, "Notify Test Safari", "€35.00", "/my-booking")
        assertThat(message.body).doesNotContain("+201000000777")

        val sent = notificationRows(bookingId).single()
        assertThat(sent.status).isEqualTo("SENT")
        assertThat(sent.sentAt).isNotNull()
        assertThat(output.all).doesNotContain(customerEmail)
    }

    @Test
    fun `a failing provider retries with backoff, then FAILED, and staff can resend`(output: CapturedOutput) {
        val (bookingId, _) = createBooking()
        payAndConfirm(bookingId)
        recorder.failWith = MailSendException("smtp down for $customerEmail")

        dispatcher.dispatchDue(100)
        val first = notificationRows(bookingId).single()
        assertThat(first.status).isEqualTo("PENDING")
        assertThat(first.attemptCount).isEqualTo(1)
        assertThat(first.lastError).isEqualTo("MailSendException")
        assertThat(first.availableAt.toInstant()).isAfter(Instant.now())

        // Not due yet: nothing is retried early.
        dispatcher.dispatchDue(100)
        assertThat(notificationRows(bookingId).single().attemptCount).isEqualTo(1)

        // Exhaust the configured two attempts.
        dsl
            .update(TOURS_OPERATOR_NOTIFICATION)
            .set(TOURS_OPERATOR_NOTIFICATION.AVAILABLE_AT, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1))
            .where(TOURS_OPERATOR_NOTIFICATION.BOOKING_ID.eq(UUID.fromString(bookingId)))
            .execute()
        dispatcher.dispatchDue(100)
        val failed = notificationRows(bookingId).single()
        assertThat(failed.status).isEqualTo("FAILED")
        assertThat(failed.attemptCount).isEqualTo(2)
        assertThat(output.all).doesNotContain(customerEmail)

        // A viewer cannot resend; an admin can, and the row is queued afresh.
        mockMvc
            .post("/api/v1/tours-operator/staff/notifications/${failed.id}/resend") {
                header("Authorization", "Bearer ${login(viewerEmail, viewerPassword)}")
            }.andExpect { status { isForbidden() } }
        mockMvc
            .post("/api/v1/tours-operator/staff/notifications/${failed.id}/resend") {
                header("Authorization", "Bearer ${login(adminEmail, adminPassword)}")
            }.andExpect { status { isAccepted() } }
        val requeued = notificationRows(bookingId).single()
        assertThat(requeued.status).isEqualTo("PENDING")
        assertThat(requeued.attemptCount).isEqualTo(0)
        assertThat(requeued.lastError).isNull()
        assertThat(requeued.resendCount).isEqualTo(1)
        assertThat(requeued.lastResentByUserId).isEqualTo(userRepository.findByEmail(EmailAddress.of(adminEmail))!!.id.value)
        assertThat(requeued.lastResentAt).isNotNull()

        recorder.reset()
        dispatcher.dispatchDue(100)
        assertThat(notificationRows(bookingId).single().status).isEqualTo("SENT")
    }

    @Test
    fun `a booking without an email is skipped visibly instead of failing`() {
        val (bookingId, _) = createBooking(email = null)
        payAndConfirm(bookingId)

        dispatcher.dispatchDue(100)

        val row = notificationRows(bookingId).single()
        assertThat(row.status).isEqualTo("SKIPPED")
        assertThat(row.lastError).isEqualTo("no_customer_email")
        assertThat(recorder.sent).isEmpty()
    }

    @Test
    fun `cancelling a confirmed booking notifies, cancelling an unpaid one does not`() {
        val token = login(adminEmail, adminPassword)
        val (confirmedId, confirmedRef) = createBooking(locale = "it")
        payAndConfirm(confirmedId)
        val (unpaidId, _) = createBooking()
        listOf(confirmedId, unpaidId).forEach { id ->
            mockMvc
                .post("/api/v1/tours-operator/bookings/$id/cancel") {
                    header("Authorization", "Bearer $token")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"reason":"Weather"}"""
                }.andExpect { status { isOk() } }
        }

        assertThat(notificationRows(confirmedId).map { it.kind })
            .containsExactlyInAnyOrder("BOOKING_CONFIRMED", "BOOKING_CANCELLED")
        assertThat(notificationRows(unpaidId)).isEmpty()

        // The confirmation was still undelivered when the booking was cancelled:
        // it must not reach the customer after (or alongside) the cancellation.
        dispatcher.dispatchDue(100)
        val cancellation = sentMessages(1).single()
        assertThat(cancellation.subject).contains("annullata")
        val stale = notificationRows(confirmedId).single { it.kind == "BOOKING_CONFIRMED" }
        assertThat(stale.status).isEqualTo("SKIPPED")
        assertThat(stale.lastError).isEqualTo("booking_state_changed")

        // Nor can staff resend it by mistake.
        mockMvc
            .post("/api/v1/tours-operator/staff/notifications/${stale.id}/resend") {
                header("Authorization", "Bearer $token")
            }.andExpect {
                status { isConflict() }
                jsonPath("$.error") { value("booking_state_changed") }
            }
        assertThat(cancellation.subject).contains(confirmedRef)
        // The internal cancellation reason is never sent to the customer.
        assertThat(cancellation.body).doesNotContain("Weather")
    }

    @Test
    fun `completing a booking schedules the review request for later`() {
        val token = login(adminEmail, adminPassword)
        val (bookingId, _) = createBooking()
        payAndConfirm(bookingId)
        dispatcher.dispatchDue(100)
        mockMvc
            .post("/api/v1/tours-operator/bookings/$bookingId/complete") {
                header("Authorization", "Bearer $token")
            }.andExpect { status { isOk() } }

        val review = notificationRows(bookingId).single { it.kind == "REVIEW_REQUEST" }
        assertThat(Duration.between(Instant.now(), review.availableAt.toInstant()))
            .isBetween(Duration.ofHours(23), Duration.ofHours(25))

        recorder.reset()
        dispatcher.dispatchDue(100)
        assertThat(recorder.sent).isEmpty()

        dsl
            .update(TOURS_OPERATOR_NOTIFICATION)
            .set(TOURS_OPERATOR_NOTIFICATION.AVAILABLE_AT, OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1))
            .where(TOURS_OPERATOR_NOTIFICATION.ID.eq(review.id))
            .execute()
        dispatcher.dispatchDue(100)
        val message = sentMessages(1).single()
        assertThat(message.body).contains("https://reviews.example.test/safari")
    }

    @Test
    fun `staff list shows status and reference but never the customer address`() {
        val (bookingId, reference) = createBooking()
        payAndConfirm(bookingId)

        val viewerToken = login(viewerEmail, viewerPassword)
        mockMvc
            .get("/api/v1/tours-operator/staff/notifications")
            .andExpect { status { isUnauthorized() } }
        mockMvc
            .get("/api/v1/tours-operator/staff/notifications") {
                header("Authorization", "Bearer $viewerToken")
            }.andExpect { status { isForbidden() } }

        val body =
            mockMvc
                .get("/api/v1/tours-operator/staff/notifications?status=PENDING") {
                    header("Authorization", "Bearer ${login(adminEmail, adminPassword)}")
                }.andExpect {
                    status { isOk() }
                    jsonPath("$[0].bookingReference") { value(reference) }
                    jsonPath("$[0].kind") { value("BOOKING_CONFIRMED") }
                    jsonPath("$[0].status") { value("PENDING") }
                }.andReturn()
                .response.contentAsString
        assertThat(body).doesNotContain(customerEmail)
    }

    @Test
    fun `concurrent dispatchers never send the same notification twice`() {
        val bookings = (1..6).map { createBooking().first }
        bookings.forEach(::payAndConfirm)
        recorder.delay = Duration.ofMillis(150)

        val pool =
            java.util.concurrent.Executors
                .newFixedThreadPool(3)
        try {
            val runs = (1..3).map { pool.submit<Int> { dispatcher.dispatchDue(100) } }
            val processed = runs.sumOf { it.get(30, java.util.concurrent.TimeUnit.SECONDS) }
            assertThat(processed).isEqualTo(6)
        } finally {
            pool.shutdownNow()
        }

        assertThat(recorder.sent).hasSize(6)
        bookings.forEach { id -> assertThat(notificationRows(id).single().status).isEqualTo("SENT") }
    }

    /** Records messages instead of sending; can be told to fail. */
    class RecordingEmailSender : EmailSender {
        val sent = CopyOnWriteArrayList<EmailMessage>()

        @Volatile var failWith: Exception? = null

        @Volatile var delay: Duration = Duration.ZERO

        override fun send(message: EmailMessage) {
            failWith?.let { throw it }
            if (!delay.isZero) Thread.sleep(delay.toMillis())
            sent += message
        }

        fun reset() {
            sent.clear()
            failWith = null
            delay = Duration.ZERO
        }
    }

    companion object {
        @JvmStatic
        fun recordingSender(): EmailSender = RecordingEmailSender()

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:18.4-alpine")
                .withDatabaseName("wego_test_notify")
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
            registry.add("tours-operator.notifications.max-attempts") { 2 }
            registry.add("tours-operator.notifications.site-base-url") { "https://site.example.test" }
            registry.add("tours-operator.notifications.review-url") { "https://reviews.example.test/safari" }
        }
    }
}
