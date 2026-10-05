package com.wego.travelmarketplace

import com.wego.travelmarketplace.application.DispatchNotificationsService
import com.wego.travelmarketplace.application.EmailMessage
import com.wego.travelmarketplace.application.EmailSender
import com.wego.travelmarketplace.application.NotificationContext
import com.wego.travelmarketplace.application.NotificationEligibility
import com.wego.travelmarketplace.application.NotificationListItem
import com.wego.travelmarketplace.application.NotificationRepository
import com.wego.travelmarketplace.application.NotificationSettings
import com.wego.travelmarketplace.application.NotificationTemplates
import com.wego.travelmarketplace.application.ResendNotificationResult
import com.wego.travelmarketplace.application.ResendNotificationService
import com.wego.travelmarketplace.application.TransactionRunner
import com.wego.travelmarketplace.application.TravelRequestRepository
import com.wego.travelmarketplace.domain.LocalizedText
import com.wego.travelmarketplace.domain.Money
import com.wego.travelmarketplace.domain.NotificationId
import com.wego.travelmarketplace.domain.NotificationKind
import com.wego.travelmarketplace.domain.NotificationStatus
import com.wego.travelmarketplace.domain.PriceBasis
import com.wego.travelmarketplace.domain.RequestNotification
import com.wego.travelmarketplace.domain.ServiceId
import com.wego.travelmarketplace.domain.TravelRequest
import com.wego.travelmarketplace.domain.TravelRequestCancelReason
import com.wego.travelmarketplace.domain.TravelRequestCustomer
import com.wego.travelmarketplace.domain.TravelRequestId
import com.wego.travelmarketplace.domain.TravelRequestReference
import com.wego.travelmarketplace.domain.TravelRequestSourceChannel
import com.wego.travelmarketplace.domain.TravelRequestStatus
import com.wego.travelmarketplace.infrastructure.SmtpEmailSender
import com.wego.travelmarketplace.infrastructure.TravelMarketplaceBeanConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.mail.MailSendException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

private val T0: Instant = Instant.parse("2026-10-05T10:00:00Z")

class RequestNotificationTest {
    private fun pending() = RequestNotification.queue(TravelRequestId.generate(), NotificationKind.STAFF_NEW_REQUEST, T0, T0)

    @Test
    fun `a failed attempt backs off, and the last allowed attempt leaves it FAILED`() {
        val n = pending()
        n.claim(T0.plusSeconds(300))
        n.markAttemptFailed("SMTP down", T0, maxAttempts = 3)
        assertThat(n.status).isEqualTo(NotificationStatus.PENDING)
        assertThat(n.availableAt).isEqualTo(T0.plusSeconds(60))
        n.claim(T0.plusSeconds(300))
        n.markAttemptFailed("SMTP down", T0, maxAttempts = 3)
        assertThat(n.availableAt).isEqualTo(T0.plusSeconds(120))
        n.claim(T0.plusSeconds(300))
        n.markAttemptFailed("SMTP down", T0, maxAttempts = 3)
        assertThat(n.status).isEqualTo(NotificationStatus.FAILED)
        assertThat(n.attemptCount).isEqualTo(3)
    }

    @Test
    fun `reasons are reduced to a bare token so an address or message text cannot be stored`() {
        val n = pending()
        n.markSkipped("nour@example.com said: hello")
        assertThat(n.lastError).doesNotContain("@", " ", ":")
        assertThat(RequestNotification.toReasonCode("a".repeat(500))).hasSize(200)
    }

    @Test
    fun `only a PENDING notification can be delivered`() {
        val n = pending()
        n.claim(T0.plusSeconds(300))
        n.markSent(T0)
        assertThat(n.attemptCount).isEqualTo(1)
        assertThat(n.status).isEqualTo(NotificationStatus.SENT)
        assertThat(n.sentAt).isEqualTo(T0)
        assertThatIllegalArgumentException().isThrownBy { n.markSent(T0) }
        assertThatIllegalArgumentException().isThrownBy { n.markSkipped("x") }
    }

    @Test
    fun `a resend after SENT queues it again and records who and when`() {
        val n = pending()
        n.markSent(T0)
        val staff = UUID.randomUUID()
        n.requeue(T0.plusSeconds(5), staff)
        assertThat(n.status).isEqualTo(NotificationStatus.PENDING)
        assertThat(n.sentAt).isNull()
        assertThat(n.attemptCount).isZero()
        assertThat(n.resendCount).isEqualTo(1)
        assertThat(n.lastResentByUserId).isEqualTo(staff)
    }

    @Test
    fun `each kind is only true for the request states it describes`() {
        assertThat(NotificationKind.CUSTOMER_REQUEST_CANCELLED.isStillTrueFor(TravelRequestStatus.CANCELLED)).isTrue()
        assertThat(NotificationKind.CUSTOMER_REQUEST_CANCELLED.isStillTrueFor(TravelRequestStatus.CONFIRMED)).isFalse()
        assertThat(NotificationKind.CUSTOMER_REQUEST_CONFIRMED.isStillTrueFor(TravelRequestStatus.CANCELLED)).isFalse()
        assertThat(NotificationKind.CUSTOMER_REQUEST_CONFIRMED.isStillTrueFor(TravelRequestStatus.COMPLETED)).isTrue()
        assertThat(NotificationKind.CUSTOMER_REQUEST_RECEIVED.isStillTrueFor(TravelRequestStatus.EXPIRED)).isFalse()
        assertThat(NotificationKind.STAFF_NEW_REQUEST.isStillTrueFor(TravelRequestStatus.NEW)).isTrue()
    }
}

class NotificationTemplatesTest {
    private val context =
        NotificationContext(
            reference = "STG-ABCDEFGH",
            serviceName = "Ras Mohammed Snorkel",
            optionLabel = "Full day",
            requestedDate = LocalDate.parse("2026-12-01"),
            requestedTime = null,
            adults = 2,
            children = 1,
            customerName = "Nour",
            confirmed = false,
            trackUrl = "https://sharmtogo.example/track/STG-ABCDEFGH",
            whatsapp = "+20 10 0141 3469",
            contactEmail = "info@sharmtogo.com",
        )

    @Test
    fun `received email carries the reference and track link and says it is not confirmed yet`() {
        val en = NotificationTemplates.render(NotificationKind.CUSTOMER_REQUEST_RECEIVED, "en", context)
        assertThat(en.subject).contains("STG-ABCDEFGH")
        assertThat(en.body).contains("STG-ABCDEFGH", "https://sharmtogo.example/track/STG-ABCDEFGH", "not confirmed yet")
        assertThat(en.body).contains("+20 10 0141 3469", "info@sharmtogo.com")
        val ar = NotificationTemplates.render(NotificationKind.CUSTOMER_REQUEST_RECEIVED, "ar", context)
        assertThat(ar.body).contains("STG-ABCDEFGH", "/track/STG-ABCDEFGH", "لم يتم تأكيده بعد")
    }

    @Test
    fun `received email for an already confirmed request is worded as confirmed`() {
        val en = NotificationTemplates.render(NotificationKind.CUSTOMER_REQUEST_RECEIVED, "en", context.copy(confirmed = true))
        assertThat(en.body).doesNotContain("not confirmed yet").contains("confirmed")
    }

    @Test
    fun `confirmed and cancelled emails exist in both languages and cancelled has no track link`() {
        for (locale in listOf("en", "ar")) {
            val confirmed = NotificationTemplates.render(NotificationKind.CUSTOMER_REQUEST_CONFIRMED, locale, context)
            val cancelled = NotificationTemplates.render(NotificationKind.CUSTOMER_REQUEST_CANCELLED, locale, context)
            assertThat(confirmed.body).contains("/track/STG-ABCDEFGH")
            assertThat(cancelled.body).doesNotContain("/track/")
            assertThat(confirmed.subject).isNotEqualTo(cancelled.subject)
        }
        assertThat(NotificationTemplates.render(NotificationKind.CUSTOMER_REQUEST_CONFIRMED, "ar", context).body).contains("تم تأكيد")
    }

    @Test
    fun `an unknown locale falls back to English`() {
        val fr = NotificationTemplates.render(NotificationKind.CUSTOMER_REQUEST_CANCELLED, "fr", context)
        assertThat(fr.body).contains("cancelled")
    }

    @Test
    fun `customer-supplied text cannot add lines, headers or direction overrides, and is never HTML`() {
        val hostile = "Nour\r\nBcc: evil@example.com‮ <script>alert(1)</script>"
        val mail = NotificationTemplates.render(NotificationKind.CUSTOMER_REQUEST_RECEIVED, "en", context.copy(customerName = hostile))
        assertThat(mail.subject).doesNotContain("\n", "\r", "Bcc")
        assertThat(mail.body.lines().first()).startsWith("Hello Nour Bcc: evil@example.com")
        assertThat(mail.body).doesNotContain("‮", "\r")
        assertThat(mail.body.lines()).noneMatch { it.startsWith("Bcc:") }
        assertThat(NotificationTemplates.clean("x".repeat(1000))).hasSize(NotificationTemplates.MAX_VALUE_LENGTH)
    }

    @Test
    fun `C1 controls, line and paragraph separators and the Arabic letter mark are stripped too`() {
        val hostile = "a\u0085b\u0080c\u009Fd\u2028e\u2029f\u061Cg\u200Eh\u202Ei\u2066j"
        assertThat(NotificationTemplates.clean(hostile)).isEqualTo("a b c d e f g h i j")
        val mail =
            NotificationTemplates.render(
                NotificationKind.CUSTOMER_REQUEST_RECEIVED,
                "en",
                context.copy(customerName = "Nour\u2028Bcc: x"),
            )
        assertThat(mail.body.lines().first()).isEqualTo("Hello Nour Bcc: x,")
        // Ordinary Arabic text is untouched.
        assertThat(NotificationTemplates.clean("نور محمد")).isEqualTo("نور محمد")
    }

    @Test
    fun `the staff alert never claims auto-confirmation from the status at send time`() {
        val mail = NotificationTemplates.render(NotificationKind.STAFF_NEW_REQUEST, "en", context.copy(confirmed = true))
        assertThat(mail.body).doesNotContain("auto-confirmed")
    }

    @Test
    fun `the staff alert is English, has no customer name, phone or email`() {
        val mail = NotificationTemplates.render(NotificationKind.STAFF_NEW_REQUEST, "ar", context)
        assertThat(mail.subject).isEqualTo("New travel request STG-ABCDEFGH")
        assertThat(mail.body).contains("STG-ABCDEFGH", "Ras Mohammed Snorkel").doesNotContain("Nour")
    }
}

private class FakeRequests(
    var request: TravelRequest?,
) : TravelRequestRepository {
    override fun findById(id: TravelRequestId) = request?.takeIf { it.id == id }

    override fun findByIdForUpdate(id: TravelRequestId) = findById(id)

    override fun findByReference(reference: TravelRequestReference) = TODO()

    override fun findByIdempotencyKey(idempotencyKey: String) = TODO()

    override fun findAll(
        status: TravelRequestStatus?,
        limit: Int,
        offset: Int,
    ) = TODO()

    override fun findExpirable(asOfDate: LocalDate) = TODO()

    override fun save(request: TravelRequest) = TODO()
}

/**
 * Stores copies, like a database: a mutation only persists through [save] or
 * [recordOutcome], so a transaction that never runs (or fails) leaves no trace.
 */
private class FakeNotifications : NotificationRepository {
    private val stored = mutableListOf<RequestNotification>()
    val rows: List<RequestNotification> get() = stored.toList()

    private fun copy(n: RequestNotification) =
        RequestNotification(
            n.id,
            n.requestId,
            n.kind,
            n.status,
            n.attemptCount,
            n.availableAt,
            n.createdAt,
            n.sentAt,
            n.lastError,
            n.resendCount,
            n.lastResentByUserId,
            n.lastResentAt,
        )

    override fun enqueueOnce(
        requestId: TravelRequestId,
        kind: NotificationKind,
        availableAt: Instant,
        now: Instant,
    ): Boolean {
        if (exists(requestId, kind)) return false
        stored += RequestNotification.queue(requestId, kind, availableAt, now)
        return true
    }

    override fun exists(
        requestId: TravelRequestId,
        kind: NotificationKind,
    ) = stored.any { it.requestId == requestId && it.kind == kind }

    override fun claimNextDue(now: Instant) =
        stored
            .filter { it.status == NotificationStatus.PENDING && !it.availableAt.isAfter(now) }
            .minByOrNull { it.availableAt }
            ?.let(::copy)

    override fun findByIdForUpdate(id: NotificationId) = stored.find { it.id == id }?.let(::copy)

    override fun list(
        status: NotificationStatus?,
        limit: Int,
        offset: Int,
    ): List<NotificationListItem> = TODO()

    override fun save(notification: RequestNotification) {
        stored.replaceAll { if (it.id == notification.id) copy(notification) else it }
    }

    override fun recordOutcome(
        notification: RequestNotification,
        expectedAttemptCount: Int,
    ): Boolean {
        val current = stored.find { it.id == notification.id } ?: return false
        if (current.status != NotificationStatus.PENDING || current.attemptCount != expectedAttemptCount) return false
        save(notification)
        return true
    }
}

private class StepClock(
    var now: Instant,
) : Clock() {
    override fun getZone() = ZoneOffset.UTC

    override fun withZone(zone: java.time.ZoneId?) = this

    override fun instant(): Instant = now
}

private val direct =
    object : TransactionRunner {
        override fun <T> runInTransaction(block: () -> T): T = block()
    }

private fun newRequest(
    email: String? = "nour@example.com",
    locale: String = "en",
): TravelRequest =
    TravelRequest.create(
        id = TravelRequestId.generate(),
        reference = TravelRequestReference.generate(),
        serviceId = ServiceId.generate(),
        serviceOptionId = UUID.randomUUID(),
        serviceName = LocalizedText("Airport Transfer", "انتقال من المطار"),
        optionLabel = LocalizedText("Arrival", "استلام"),
        price = Money(BigDecimal("700.00"), "EGP"),
        priceBasis = PriceBasis.PER_VEHICLE,
        cancellationPolicy = LocalizedText("Free.", "مجاني."),
        requestedDate = LocalDate.parse("2026-12-01"),
        requestedTime = null,
        adults = 2,
        children = 0,
        hotelOrPickup = null,
        locale = locale,
        notes = null,
        sourceChannel = TravelRequestSourceChannel.WEBSITE,
        customer = TravelRequestCustomer(name = "Nour", phone = "+201001413469", email = email),
        idempotencyKey = UUID.randomUUID().toString(),
        now = T0,
    )

class DispatchNotificationsServiceTest {
    private val sent = mutableListOf<EmailMessage>()
    private val sender = EmailSender { sent += it }
    private val settings =
        NotificationSettings("https://sharmtogo.example/", "staff@sharmtogo.example", "+20 10 0141 3469", "info@sharmtogo.com", 3)
    private val clock = Clock.fixed(T0, ZoneOffset.UTC)

    private fun service(
        requests: FakeRequests,
        notifications: FakeNotifications,
        emailSender: EmailSender = sender,
        settings: NotificationSettings = this.settings,
    ) = DispatchNotificationsService(notifications, requests, emailSender, direct, settings, clock)

    @Test
    fun `sends the customer receipt to the address on the request, in the request locale`() {
        val request = newRequest(locale = "ar")
        val notifications = FakeNotifications().also { it.enqueueOnce(request.id, NotificationKind.CUSTOMER_REQUEST_RECEIVED, T0, T0) }

        assertThat(service(FakeRequests(request), notifications).dispatchDue(10)).isEqualTo(1)

        assertThat(sent.single().to).isEqualTo("nour@example.com")
        assertThat(sent.single().body).contains(request.reference.value, "https://sharmtogo.example/track/${request.reference.value}")
        assertThat(sent.single().body).contains("لم يتم تأكيده بعد")
        assertThat(notifications.rows.single().status).isEqualTo(NotificationStatus.SENT)
    }

    @Test
    fun `sends the staff alert to the configured address`() {
        val request = newRequest(email = null)
        val notifications = FakeNotifications().also { it.enqueueOnce(request.id, NotificationKind.STAFF_NEW_REQUEST, T0, T0) }
        service(FakeRequests(request), notifications).dispatchDue(10)
        assertThat(sent.single().to).isEqualTo("staff@sharmtogo.example")
    }

    @Test
    fun `skips with a code instead of sending when the message is no longer true or has no recipient`() {
        val cancelled = newRequest().also { it.cancel(TravelRequestCancelReason.OTHER, null, T0) }
        val noEmail = newRequest(email = null)
        val notifications =
            FakeNotifications().also {
                it.enqueueOnce(cancelled.id, NotificationKind.CUSTOMER_REQUEST_CONFIRMED, T0, T0)
                it.enqueueOnce(noEmail.id, NotificationKind.CUSTOMER_REQUEST_RECEIVED, T0, T0)
                it.enqueueOnce(noEmail.id, NotificationKind.STAFF_NEW_REQUEST, T0, T0)
            }
        val requests =
            object : TravelRequestRepository by FakeRequests(null) {
                override fun findById(id: TravelRequestId) = listOf(cancelled, noEmail).find { it.id == id }
            }
        val noStaff = settings.copy(staffAddress = null)

        DispatchNotificationsService(notifications, requests, sender, direct, noStaff, clock).dispatchDue(10)

        assertThat(sent).isEmpty()
        assertThat(notifications.rows.map { it.lastError }).containsExactlyInAnyOrder(
            "request_state_changed",
            "no_customer_email",
            "no_staff_address",
        )
        assertThat(notifications.rows).allMatch { it.status == NotificationStatus.SKIPPED }
    }

    @Test
    fun `the receipt is superseded once a separate confirmation email is queued`() {
        val request = newRequest().also { it.confirm(T0) }
        val notifications =
            FakeNotifications().also {
                it.enqueueOnce(request.id, NotificationKind.CUSTOMER_REQUEST_RECEIVED, T0, T0)
                it.enqueueOnce(request.id, NotificationKind.CUSTOMER_REQUEST_CONFIRMED, T0.plusSeconds(1), T0)
            }
        service(FakeRequests(request), notifications).dispatchDue(10)
        assertThat(sent).isEmpty() // the confirmation is not yet due at T0
        assertThat(notifications.rows.first().lastError).isEqualTo("superseded_by_confirmation")
    }

    @Test
    fun `a failing sender retries then ends FAILED, never logging or storing the address`() {
        val request = newRequest()
        val notifications = FakeNotifications().also { it.enqueueOnce(request.id, NotificationKind.CUSTOMER_REQUEST_RECEIVED, T0, T0) }
        val failing = EmailSender { throw IllegalStateException("550 rejected nour@example.com") }
        val clock = StepClock(T0)
        val dispatcher = DispatchNotificationsService(notifications, FakeRequests(request), failing, direct, settings, clock)

        repeat(3) {
            dispatcher.dispatchDue(10)
            clock.now = clock.now.plusSeconds(3600) // past any backoff
        }

        val row = notifications.rows.single()
        assertThat(row.status).isEqualTo(NotificationStatus.FAILED)
        assertThat(row.lastError).isEqualTo("IllegalStateException").doesNotContain("@")
    }

    @Test
    fun `resend requeues a SENT notification but refuses a stale one`() {
        val request = newRequest()
        val notifications = FakeNotifications().also { it.enqueueOnce(request.id, NotificationKind.CUSTOMER_REQUEST_RECEIVED, T0, T0) }
        service(FakeRequests(request), notifications).dispatchDue(10)
        val resend = ResendNotificationService(notifications, FakeRequests(request), direct, clock)
        val id = notifications.rows.single().id

        assertThat(resend.resend(id, UUID.randomUUID())).isEqualTo(ResendNotificationResult.Requeued)
        assertThat(notifications.rows.single().status).isEqualTo(NotificationStatus.PENDING)

        request.cancel(TravelRequestCancelReason.OTHER, null, T0)
        assertThat(resend.resend(id, null)).isEqualTo(ResendNotificationResult.RequestStateChanged)
        assertThat(resend.resend(NotificationId.generate(), null)).isEqualTo(ResendNotificationResult.NotFound)
    }

    @Test
    fun `a failed commit after the send never loops past max-attempts`() {
        val request = newRequest()
        val notifications = FakeNotifications().also { it.enqueueOnce(request.id, NotificationKind.CUSTOMER_REQUEST_RECEIVED, T0, T0) }
        val clock = StepClock(T0)
        // Saving the outcome always fails (as a failed commit would), after the email went out.
        val failingRecord =
            object : NotificationRepository by notifications {
                override fun recordOutcome(
                    notification: RequestNotification,
                    expectedAttemptCount: Int,
                ): Boolean = throw IllegalStateException("commit failed")
            }
        val dispatcher = DispatchNotificationsService(failingRecord, FakeRequests(request), sender, direct, settings, clock)

        repeat(10) {
            dispatcher.dispatchDue(10)
            clock.now = clock.now.plus(settings.lease).plusSeconds(1)
        }

        assertThat(sent).hasSize(settings.maxAttempts) // 3, then the row is FAILED instead of a fourth send
        val row = notifications.rows.single()
        assertThat(row.status).isEqualTo(NotificationStatus.FAILED)
        assertThat(row.attemptCount).isEqualTo(settings.maxAttempts)
    }

    @Test
    fun `a crash between send and record gives at most max-attempts sends`() {
        val request = newRequest()
        val notifications = FakeNotifications().also { it.enqueueOnce(request.id, NotificationKind.CUSTOMER_REQUEST_RECEIVED, T0, T0) }
        val clock = StepClock(T0)
        val crashing =
            EmailSender {
                sent += it
                throw OutOfMemoryError("process dies right after the relay accepted it")
            }
        val dispatcher = DispatchNotificationsService(notifications, FakeRequests(request), crashing, direct, settings, clock)

        repeat(8) {
            runCatching { dispatcher.dispatchDue(10) }
            // Within the lease nothing is claimable, so a restart does not resend at once.
            runCatching { dispatcher.dispatchDue(10) }
            clock.now = clock.now.plus(settings.lease).plusSeconds(1)
        }

        assertThat(sent).hasSize(settings.maxAttempts)
        assertThat(notifications.rows.single().status).isEqualTo(NotificationStatus.FAILED)
        assertThat(notifications.rows.single().lastError).isEqualTo("attempts_exhausted")
    }

    @Test
    fun `a claimed row is invisible to other dispatchers until the lease runs out`() {
        val request = newRequest()
        val notifications = FakeNotifications().also { it.enqueueOnce(request.id, NotificationKind.CUSTOMER_REQUEST_RECEIVED, T0, T0) }
        val clock = StepClock(T0)
        lateinit var other: DispatchNotificationsService
        var otherProcessed = -1
        // While the first dispatcher is inside the send, a second one runs.
        val sender =
            EmailSender {
                sent += it
                otherProcessed = other.dispatchDue(10)
            }
        val first = DispatchNotificationsService(notifications, FakeRequests(request), sender, direct, settings, clock)
        other = DispatchNotificationsService(notifications, FakeRequests(request), sender, direct, settings, clock)

        first.dispatchDue(10)

        assertThat(otherProcessed).isZero()
        assertThat(sent).hasSize(1)
        assertThat(notifications.rows.single().status).isEqualTo(NotificationStatus.SENT)
    }

    @Test
    fun `no transaction is open while the email is sent`() {
        val request = newRequest()
        val notifications = FakeNotifications().also { it.enqueueOnce(request.id, NotificationKind.CUSTOMER_REQUEST_RECEIVED, T0, T0) }
        var open = 0
        val runner =
            object : TransactionRunner {
                override fun <T> runInTransaction(block: () -> T): T {
                    open++
                    try {
                        return block()
                    } finally {
                        open--
                    }
                }
            }
        var openDuringSend = -1
        val sender = EmailSender { openDuringSend = open }
        DispatchNotificationsService(notifications, FakeRequests(request), sender, runner, settings, clock).dispatchDue(10)
        assertThat(openDuringSend).isZero()
    }

    @Test
    fun `a database error during the request lookup still counts the attempt and ends FAILED at max-attempts`() {
        val request = newRequest()
        val notifications = FakeNotifications().also { it.enqueueOnce(request.id, NotificationKind.CUSTOMER_REQUEST_RECEIVED, T0, T0) }
        val clock = StepClock(T0)
        val brokenLookup =
            object : TravelRequestRepository by FakeRequests(request) {
                override fun findById(id: TravelRequestId): TravelRequest = throw IllegalStateException("connection reset")
            }
        val dispatcher = DispatchNotificationsService(notifications, brokenLookup, sender, direct, settings, clock)

        repeat(settings.maxAttempts + 2) {
            dispatcher.dispatchDue(10)
            clock.now = clock.now.plus(settings.lease).plusSeconds(1)
        }

        val row = notifications.rows.single()
        assertThat(sent).isEmpty()
        assertThat(row.status).isEqualTo(NotificationStatus.FAILED)
        assertThat(row.attemptCount).isEqualTo(settings.maxAttempts)
        assertThat(row.lastError).isEqualTo("IllegalStateException")
    }

    @Test
    fun `an outcome for a row that a staff resend meanwhile requeued is not recorded over the newer state`() {
        val request = newRequest()
        val notifications = FakeNotifications().also { it.enqueueOnce(request.id, NotificationKind.CUSTOMER_REQUEST_RECEIVED, T0, T0) }
        val resend = ResendNotificationService(notifications, FakeRequests(request), direct, clock)
        val id = notifications.rows.single().id
        val sender =
            EmailSender {
                sent += it
                resend.resend(id, UUID.randomUUID()) // resend lands while the email is in flight
            }
        DispatchNotificationsService(notifications, FakeRequests(request), sender, direct, settings, clock).dispatchDue(1)

        val row = notifications.rows.single()
        assertThat(row.status).isEqualTo(NotificationStatus.PENDING)
        assertThat(row.attemptCount).isZero()
        assertThat(row.resendCount).isEqualTo(1)
    }

    @Test
    fun `eligibility reports a missing request`() {
        assertThat(NotificationEligibility.stateSkipReason(NotificationKind.STAFF_NEW_REQUEST, null, false)).isEqualTo("request_missing")
    }
}

class SmtpAndStartupTest {
    @Test
    fun `SmtpEmailSender sets from, reply-to, recipient and plain text`() {
        val mailSender = mock(JavaMailSender::class.java)
        SmtpEmailSender(mailSender, "noreply@sharmtogo.example", "info@sharmtogo.com")
            .send(EmailMessage("nour@example.com", "Subject", "Body"))
        val captor = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage::class.java)
        verify(mailSender).send(captor.capture())
        assertThat(captor.value.from).isEqualTo("noreply@sharmtogo.example")
        assertThat(captor.value.replyTo).isEqualTo("info@sharmtogo.com")
        assertThat(captor.value.to).containsExactly("nour@example.com")
        assertThat(captor.value.text).isEqualTo("Body")
    }

    @Test
    fun `EmailMessage never prints the recipient`() {
        assertThat(EmailMessage("nour@example.com", "S", "B").toString()).doesNotContain("nour@example.com")
    }

    private val config = TravelMarketplaceBeanConfiguration()

    private fun sender(
        host: String,
        enabled: Boolean,
        from: String = "noreply@x.example",
    ) = config.travelNotificationEmailSender(host, 587, "", "", enabled, from, "")

    @Test
    fun `enabling notifications without an SMTP host fails startup, and a blank host counts as no host`() {
        assertThatThrownBy { sender("", true) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("smtp.host")
        // Compose passes an empty value when no host is set.
        assertThatThrownBy { sender("   ", true) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `a blank host never creates a mail sender, a real host does with default timeouts`() {
        assertThat(config.buildMailSender("", 587, "", "")).isNull()
        assertThat(config.buildMailSender("  ", 587, "", "")).isNull()
        val real = config.buildMailSender("smtp.example", 2525, "user", "pw")!!
        assertThat(real.host).isEqualTo("smtp.example")
        assertThat(real.port).isEqualTo(2525)
        assertThat(real.javaMailProperties).containsEntry("mail.smtp.timeout", "15000")
        assertThat(real.javaMailProperties).containsEntry("mail.smtp.auth", "true")
        assertThat(real.javaMailProperties).containsEntry("mail.smtp.starttls.required", "true")
        val noAuth = config.buildMailSender("smtp.example", 587, "", "")!!
        assertThat(noAuth.javaMailProperties).containsEntry("mail.smtp.starttls.required", "true")
        val smtps = config.buildMailSender("smtp.example", 465, "user", "pw")!!
        assertThat(smtps.protocol).isEqualTo("smtps")
    }

    @Test
    fun `enabling notifications without a from address fails startup`() {
        assertThatThrownBy { sender("smtp.example", true, from = "") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("from")
    }

    @Test
    fun `disabled without SMTP is fine, and any send attempt fails loudly rather than silently succeeding`() {
        val sender = sender("", false, from = "")
        assertThatThrownBy { sender.send(EmailMessage("a@b.example", "s", "b")) }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `enabling notifications requires an https site URL`() {
        val sender = EmailSender { }
        assertThatThrownBy {
            config.dispatchNotificationsService(
                FakeNotifications(),
                FakeRequests(null),
                sender,
                direct,
                "http://localhost:3000",
                "",
                "+20",
                "i@x.example",
                5,
                true,
                Clock.systemUTC(),
            )
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("https")
        // The same URL is fine while disabled.
        config.dispatchNotificationsService(
            FakeNotifications(),
            FakeRequests(null),
            sender,
            direct,
            "http://localhost:3000",
            "",
            "+20",
            "i@x.example",
            5,
            false,
            Clock.systemUTC(),
        )
    }

    @Test
    fun `MailSendException from the relay is just another failed attempt`() {
        val request = newRequest()
        val notifications = FakeNotifications().also { it.enqueueOnce(request.id, NotificationKind.CUSTOMER_REQUEST_RECEIVED, T0, T0) }
        val settings = NotificationSettings("https://s.example", null, "+20", "i@x.example", 1)
        DispatchNotificationsService(
            notifications,
            FakeRequests(request),
            {
                throw MailSendException("550 nour@example.com")
            },
            direct,
            settings,
            Clock.fixed(T0, ZoneOffset.UTC),
        ).dispatchDue(5)
        assertThat(notifications.rows.single().status).isEqualTo(NotificationStatus.FAILED)
        assertThat(notifications.rows.single().lastError).isEqualTo("MailSendException")
    }
}
