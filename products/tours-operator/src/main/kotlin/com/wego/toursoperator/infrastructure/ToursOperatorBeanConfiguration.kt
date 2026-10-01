package com.wego.toursoperator.infrastructure

import com.wego.events.OutboxWriter
import com.wego.identity.AuthenticatedApiPrefix
import com.wego.identity.PublicApiPrefix
import com.wego.toursoperator.application.BookingAuditRecorder
import com.wego.toursoperator.application.BookingHistoryQuery
import com.wego.toursoperator.application.BookingQueryService
import com.wego.toursoperator.application.BookingRepository
import com.wego.toursoperator.application.CancelBookingService
import com.wego.toursoperator.application.CompleteBookingService
import com.wego.toursoperator.application.ConfirmBookingService
import com.wego.toursoperator.application.CreateBookingService
import com.wego.toursoperator.application.CreateSlotService
import com.wego.toursoperator.application.CreateTourService
import com.wego.toursoperator.application.DispatchNotificationsService
import com.wego.toursoperator.application.EmailSender
import com.wego.toursoperator.application.ExpireBookingService
import com.wego.toursoperator.application.ExpireOverduePaymentsService
import com.wego.toursoperator.application.HandlePaymobWebhookService
import com.wego.toursoperator.application.InitiatePaymentService
import com.wego.toursoperator.application.NotificationRepository
import com.wego.toursoperator.application.NotificationSettings
import com.wego.toursoperator.application.PaymentQueryService
import com.wego.toursoperator.application.PaymentRepository
import com.wego.toursoperator.application.PaymobClient
import com.wego.toursoperator.application.PublicTourContentQuery
import com.wego.toursoperator.application.ResendNotificationService
import com.wego.toursoperator.application.SalesControlRepository
import com.wego.toursoperator.application.SalesControlService
import com.wego.toursoperator.application.SetSlotBlockedService
import com.wego.toursoperator.application.SetTourActiveService
import com.wego.toursoperator.application.TourContentRepository
import com.wego.toursoperator.application.TourContentService
import com.wego.toursoperator.application.TourQueryService
import com.wego.toursoperator.application.TourRepository
import com.wego.toursoperator.application.TourSlotQueryService
import com.wego.toursoperator.application.TourSlotRepository
import com.wego.toursoperator.application.TransactionRunner
import com.wego.toursoperator.application.UpdateTourService
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.scheduling.annotation.EnableScheduling
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Duration

/**
 * All bean names are prefixed with "sto" (Safari Tours Operator) to avoid
 * conflicts with identically-named beans from DiversBeanConfiguration.
 *
 * Repositories are discovered via @Repository("sto*Impl") on each class —
 * this ensures the kotlin-spring allopen plugin keeps them open for
 * @Transactional proxy generation. Services are wired here via @Qualifier.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class ToursOperatorBeanConfiguration {
    // ── Security prefixes ────────────────────────────────────────────────────
    // Declares this product's API surface to kernel security — see
    // AuthenticatedApiPrefix/PublicApiPrefix doc comments.
    // Public: tour catalog reads + public booking creation + booking lookup.
    // Authenticated: everything else (ERP staff operations).

    @Bean
    fun toursOperatorAuthenticatedApiPrefix(): AuthenticatedApiPrefix = AuthenticatedApiPrefix("/api/v1/tours-operator/**")

    // Public reads use narrow patterns. Staff writes live under the separate
    // /api/v1/tours-operator/staff/** tree and therefore cannot collide with
    // a public tour-id wildcard.
    // Spring Security evaluates matchers in registration order; public rules are
    // registered before the authenticated rule above (see SecurityConfiguration).
    // Pattern rules:
    //   * (single *) matches one path segment — does NOT cross a slash.
    //   ** matches zero or more segments.
    // /tours/* therefore matches /tours/{uuid}; staff routes fall through to
    // the authenticated product rule and then require method permissions.
    @Bean
    fun toursOperatorPublicToursListPrefix(): PublicApiPrefix = PublicApiPrefix("/api/v1/tours-operator/tours")

    @Bean
    fun toursOperatorPublicTourByIdPrefix(): PublicApiPrefix = PublicApiPrefix("/api/v1/tours-operator/tours/*")

    @Bean
    fun toursOperatorPublicTourBySlugPrefix(): PublicApiPrefix = PublicApiPrefix("/api/v1/tours-operator/tours/by-slug")

    @Bean
    fun toursOperatorPublicTourContentPrefix(): PublicApiPrefix = PublicApiPrefix("/api/v1/tours-operator/tours/by-slug/content")

    @Bean
    fun toursOperatorPublicTourSlotListPrefix(): PublicApiPrefix = PublicApiPrefix("/api/v1/tours-operator/tours/*/slots")

    @Bean
    fun toursOperatorPublicTourSlotByDatePrefix(): PublicApiPrefix = PublicApiPrefix("/api/v1/tours-operator/tours/*/slots/by-date")

    @Bean
    fun toursOperatorPublicBookingCreatePrefix(): PublicApiPrefix = PublicApiPrefix("/api/v1/tours-operator/bookings")

    @Bean("stoPublicBookingLookupPrefix")
    fun toursOperatorPublicBookingLookupPrefix(): PublicApiPrefix = PublicApiPrefix("/api/v1/tours-operator/bookings/lookup")

    @Bean("stoPublicPaymentInitiatePrefix")
    fun toursOperatorPublicPaymentInitiatePrefix(): PublicApiPrefix = PublicApiPrefix("/api/v1/tours-operator/bookings/*/pay")

    @Bean("stoPublicPaymentStatusPrefix")
    fun toursOperatorPublicPaymentStatusPrefix(): PublicApiPrefix = PublicApiPrefix("/api/v1/tours-operator/bookings/*/payment-status")

    @Bean("stoPublicPaymobCallbackPrefix")
    fun toursOperatorPublicPaymobCallbackPrefix(): PublicApiPrefix = PublicApiPrefix("/api/v1/tours-operator/payments/paymob-callback")

    @Bean("stoPublicSalesStatusPrefix")
    fun toursOperatorPublicSalesStatusPrefix(): PublicApiPrefix = PublicApiPrefix("/api/v1/tours-operator/sales-status")

    @Bean("stoSalesControlService")
    fun salesControlService(
        @Qualifier("stoSalesControlRepositoryImpl") repository: SalesControlRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): SalesControlService = SalesControlService(repository, transactionRunner, clock)

    @Bean("stoObjectMapper")
    fun toursOperatorObjectMapper(): ObjectMapper = ObjectMapper()

    // ── Query services ───────────────────────────────────────────────────────

    @Bean("stoTourQueryService")
    fun tourQueryService(
        @Qualifier("stoTourRepositoryImpl") tourRepository: TourRepository,
    ): TourQueryService = TourQueryService(tourRepository)

    @Bean("stoPublicTourContentQuery")
    fun publicTourContentQuery(
        @Qualifier("stoTourRepositoryImpl") tourRepository: TourRepository,
        @Qualifier("stoTourContentRepositoryImpl") contentRepository: TourContentRepository,
        transactionRunner: TransactionRunner,
    ): PublicTourContentQuery = PublicTourContentQuery(tourRepository, contentRepository, transactionRunner)

    @Bean("stoTourContentService")
    fun tourContentService(
        @Qualifier("stoTourRepositoryImpl") tourRepository: TourRepository,
        @Qualifier("stoTourContentRepositoryImpl") contentRepository: TourContentRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): TourContentService = TourContentService(tourRepository, contentRepository, transactionRunner, clock)

    @Bean("stoCreateTourService")
    fun createTourService(
        @Qualifier("stoTourRepositoryImpl") tourRepository: TourRepository,
        clock: Clock,
    ): CreateTourService = CreateTourService(tourRepository, clock)

    @Bean("stoUpdateTourService")
    fun updateTourService(
        @Qualifier("stoTourRepositoryImpl") tourRepository: TourRepository,
        transactionRunner: TransactionRunner,
    ): UpdateTourService = UpdateTourService(tourRepository, transactionRunner)

    @Bean("stoSetTourActiveService")
    fun setTourActiveService(
        @Qualifier("stoTourRepositoryImpl") tourRepository: TourRepository,
        transactionRunner: TransactionRunner,
    ): SetTourActiveService = SetTourActiveService(tourRepository, transactionRunner)

    @Bean("stoTourSlotQueryService")
    fun tourSlotQueryService(
        @Qualifier("stoTourSlotRepositoryImpl") slotRepository: TourSlotRepository,
    ): TourSlotQueryService = TourSlotQueryService(slotRepository)

    @Bean("stoCreateSlotService")
    fun createSlotService(
        @Qualifier("stoTourSlotRepositoryImpl") slotRepository: TourSlotRepository,
        @Qualifier("stoTourRepositoryImpl") tourRepository: TourRepository,
        clock: Clock,
    ): CreateSlotService = CreateSlotService(slotRepository, tourRepository, clock)

    @Bean("stoSetSlotBlockedService")
    fun setSlotBlockedService(
        @Qualifier("stoTourSlotRepositoryImpl") slotRepository: TourSlotRepository,
        transactionRunner: TransactionRunner,
    ): SetSlotBlockedService = SetSlotBlockedService(slotRepository, transactionRunner)

    @Bean("stoBookingQueryService")
    fun bookingQueryService(
        @Qualifier("stoBookingRepositoryImpl") bookingRepository: BookingRepository,
        bookingHistoryQuery: BookingHistoryQuery,
    ): BookingQueryService = BookingQueryService(bookingRepository, bookingHistoryQuery)

    // ── Command services ─────────────────────────────────────────────────────

    @Bean("stoCreateBookingService")
    fun createBookingService(
        @Qualifier("stoTourRepositoryImpl") tourRepository: TourRepository,
        @Qualifier("stoTourSlotRepositoryImpl") slotRepository: TourSlotRepository,
        @Qualifier("stoBookingRepositoryImpl") bookingRepository: BookingRepository,
        bookingAuditRecorder: BookingAuditRecorder,
        @Qualifier("stoSalesControlRepositoryImpl") salesControlRepository: SalesControlRepository,
        outboxWriter: OutboxWriter,
        transactionRunner: TransactionRunner,
        @Qualifier("stoObjectMapper") toursOperatorObjectMapper: ObjectMapper,
        clock: Clock,
    ): CreateBookingService =
        CreateBookingService(
            tourRepository,
            slotRepository,
            bookingRepository,
            bookingAuditRecorder,
            salesControlRepository,
            outboxWriter,
            transactionRunner,
            toursOperatorObjectMapper,
            clock,
        )

    @Bean("stoConfirmBookingService")
    fun confirmBookingService(
        @Qualifier("stoBookingRepositoryImpl") bookingRepository: BookingRepository,
        @Qualifier("stoPaymentRepositoryImpl") paymentRepository: PaymentRepository,
        bookingAuditRecorder: BookingAuditRecorder,
        @Qualifier("stoNotificationRepositoryImpl") notificationRepository: NotificationRepository,
        outboxWriter: OutboxWriter,
        transactionRunner: TransactionRunner,
        @Qualifier("stoObjectMapper") toursOperatorObjectMapper: ObjectMapper,
        clock: Clock,
    ): ConfirmBookingService =
        ConfirmBookingService(
            bookingRepository,
            paymentRepository,
            bookingAuditRecorder,
            notificationRepository,
            outboxWriter,
            transactionRunner,
            toursOperatorObjectMapper,
            clock,
        )

    @Bean("stoCancelBookingService")
    fun cancelBookingService(
        @Qualifier("stoBookingRepositoryImpl") bookingRepository: BookingRepository,
        @Qualifier("stoTourSlotRepositoryImpl") slotRepository: TourSlotRepository,
        bookingAuditRecorder: BookingAuditRecorder,
        @Qualifier("stoNotificationRepositoryImpl") notificationRepository: NotificationRepository,
        outboxWriter: OutboxWriter,
        transactionRunner: TransactionRunner,
        @Qualifier("stoObjectMapper") toursOperatorObjectMapper: ObjectMapper,
        clock: Clock,
    ): CancelBookingService =
        CancelBookingService(
            bookingRepository,
            slotRepository,
            bookingAuditRecorder,
            notificationRepository,
            outboxWriter,
            transactionRunner,
            toursOperatorObjectMapper,
            clock,
        )

    @Bean("stoCompleteBookingService")
    fun completeBookingService(
        @Qualifier("stoBookingRepositoryImpl") bookingRepository: BookingRepository,
        bookingAuditRecorder: BookingAuditRecorder,
        @Qualifier("stoNotificationRepositoryImpl") notificationRepository: NotificationRepository,
        @Value("\${tours-operator.notifications.review-request-delay:PT24H}") reviewRequestDelay: Duration,
        outboxWriter: OutboxWriter,
        transactionRunner: TransactionRunner,
        @Qualifier("stoObjectMapper") toursOperatorObjectMapper: ObjectMapper,
        clock: Clock,
    ): CompleteBookingService =
        CompleteBookingService(
            bookingRepository,
            bookingAuditRecorder,
            notificationRepository,
            reviewRequestDelay,
            outboxWriter,
            transactionRunner,
            toursOperatorObjectMapper,
            clock,
        )

    // ── Customer notifications ───────────────────────────────────────────────

    /**
     * SMTP when spring.mail.host is configured. Without it, enabling the
     * dispatcher is a configuration error and fails startup instead of
     * silently failing every email.
     */
    @Bean("stoEmailSender")
    fun emailSender(
        mailSender: ObjectProvider<JavaMailSender>,
        @Value("\${tours-operator.notifications.enabled:false}") enabled: Boolean,
        @Value("\${tours-operator.notifications.from:}") from: String,
        @Value("\${tours-operator.notifications.reply-to:}") replyTo: String,
    ): EmailSender {
        val smtp = mailSender.ifAvailable
        (smtp as? JavaMailSenderImpl)?.let { SmtpEmailSender.applyDefaultTimeouts(it, Duration.ofSeconds(15)) }
        if (smtp == null) {
            require(!enabled) { "tours-operator.notifications.enabled=true requires spring.mail.host" }
            return EmailSender { throw IllegalStateException("email_not_configured") }
        }
        require(!enabled || from.isNotBlank()) { "tours-operator.notifications.from is required when notifications are enabled" }
        return SmtpEmailSender(smtp, from, replyTo.ifBlank { null })
    }

    @Bean("stoDispatchNotificationsService")
    fun dispatchNotificationsService(
        @Qualifier("stoNotificationRepositoryImpl") notificationRepository: NotificationRepository,
        @Qualifier("stoBookingRepositoryImpl") bookingRepository: BookingRepository,
        @Qualifier("stoTourRepositoryImpl") tourRepository: TourRepository,
        emailSender: EmailSender,
        transactionRunner: TransactionRunner,
        @Value("\${tours-operator.notifications.site-base-url:http://localhost:3000}") siteBaseUrl: String,
        @Value("\${tours-operator.notifications.review-url:}") reviewUrl: String,
        @Value("\${tours-operator.notifications.max-attempts:5}") maxAttempts: Int,
        @Value("\${tours-operator.notifications.enabled:false}") enabled: Boolean,
        clock: Clock,
    ): DispatchNotificationsService {
        // Every confirmation links to the public site; never let a live
        // dispatcher send customers to a localhost or plain-http default.
        require(!enabled || siteBaseUrl.startsWith("https://")) {
            "tours-operator.notifications.site-base-url must be an https:// URL when notifications are enabled"
        }
        return DispatchNotificationsService(
            notificationRepository,
            bookingRepository,
            tourRepository,
            emailSender,
            transactionRunner,
            NotificationSettings(siteBaseUrl, reviewUrl.ifBlank { null }, maxAttempts),
            clock,
        )
    }

    @Bean("stoResendNotificationService")
    fun resendNotificationService(
        @Qualifier("stoNotificationRepositoryImpl") notificationRepository: NotificationRepository,
        @Qualifier("stoBookingRepositoryImpl") bookingRepository: BookingRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): ResendNotificationService = ResendNotificationService(notificationRepository, bookingRepository, transactionRunner, clock)

    @Bean("stoExpireBookingService")
    fun expireBookingService(
        @Qualifier("stoBookingRepositoryImpl") bookingRepository: BookingRepository,
        @Qualifier("stoPaymentRepositoryImpl") paymentRepository: PaymentRepository,
        @Qualifier("stoTourSlotRepositoryImpl") slotRepository: TourSlotRepository,
        bookingAuditRecorder: BookingAuditRecorder,
        outboxWriter: OutboxWriter,
        transactionRunner: TransactionRunner,
        @Qualifier("stoObjectMapper") toursOperatorObjectMapper: ObjectMapper,
        clock: Clock,
    ): ExpireBookingService =
        ExpireBookingService(
            bookingRepository,
            paymentRepository,
            slotRepository,
            bookingAuditRecorder,
            outboxWriter,
            transactionRunner,
            toursOperatorObjectMapper,
            clock,
        )

    // ── Payment beans ────────────────────────────────────────────────────────

    @Bean("stoPaymobConfig")
    fun paymobConfig(
        @Value("\${tours-operator.paymob.base-url:https://accept.paymob.com}") baseUrl: String,
        @Value("\${tours-operator.paymob.secret-key:PLACEHOLDER_PAYMOB_SECRET_KEY}") secretKey: String,
        @Value("\${tours-operator.paymob.public-key:PLACEHOLDER_PAYMOB_PUBLIC_KEY}") publicKey: String,
        @Value("\${tours-operator.paymob.api-key:PLACEHOLDER_PAYMOB_API_KEY}") apiKey: String,
        @Value("\${tours-operator.paymob.integration-id:PLACEHOLDER_INTEGRATION_ID}") integrationId: String,
        @Value("\${tours-operator.paymob.owner-id:PLACEHOLDER_OWNER_ID}") ownerId: String,
        @Value("\${tours-operator.paymob.hmac-secret:PLACEHOLDER_HMAC_SECRET}") hmacSecret: String,
        @Value("\${tours-operator.paymob.checkout-base-url:https://accept.paymob.com/unifiedcheckout/}") checkoutBaseUrl: String,
        @Value(
            "\${tours-operator.paymob.notification-url:https://example.invalid/api/v1/tours-operator/payments/paymob-callback}",
        ) notificationUrl: String,
        @Value("\${tours-operator.paymob.redirection-url:https://example.invalid/booking/payment-result}") redirectionUrl: String,
        @Value("\${tours-operator.paymob.billing-city:Sharm El Sheikh}") billingCity: String,
        @Value("\${tours-operator.paymob.billing-country-code:EG}") billingCountryCode: String,
        @Value("\${tours-operator.paymob.checkout-expiration-seconds:1800}") checkoutExpirationSeconds: Int,
    ): PaymobConfig =
        PaymobConfig(
            baseUrl = baseUrl,
            secretKey = secretKey,
            publicKey = publicKey,
            apiKey = apiKey,
            integrationId = integrationId,
            ownerId = ownerId,
            hmacSecret = hmacSecret,
            checkoutBaseUrl = checkoutBaseUrl,
            notificationUrl = notificationUrl,
            redirectionUrl = redirectionUrl,
            billingCity = billingCity,
            billingCountryCode = billingCountryCode,
            checkoutExpirationSeconds = checkoutExpirationSeconds,
        )

    @Bean("stoPaymobClient")
    @ConditionalOnProperty(
        name = ["tours-operator.paymob.mock-enabled"],
        havingValue = "false",
        matchIfMissing = true,
    )
    fun paymobClient(
        @Qualifier("stoPaymobConfig") config: PaymobConfig,
        @Qualifier("stoObjectMapper") objectMapper: ObjectMapper,
    ): PaymobClient = PaymobHttpClient(config, objectMapper)

    @Bean("stoPaymobClient")
    @ConditionalOnProperty(
        name = ["tours-operator.paymob.mock-enabled"],
        havingValue = "true",
    )
    fun mockPaymobClient(): PaymobClient = MockPaymobClient()

    @Bean("stoPaymentQueryService")
    fun paymentQueryService(
        @Qualifier("stoPaymentRepositoryImpl") paymentRepository: PaymentRepository,
    ): PaymentQueryService = PaymentQueryService(paymentRepository)

    @Bean("stoInitiatePaymentService")
    fun initiatePaymentService(
        @Qualifier("stoBookingRepositoryImpl") bookingRepository: BookingRepository,
        @Qualifier("stoPaymentRepositoryImpl") paymentRepository: PaymentRepository,
        @Qualifier("stoPaymobClient") paymobClient: PaymobClient,
        @Qualifier("stoSalesControlRepositoryImpl") salesControlRepository: SalesControlRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): InitiatePaymentService =
        InitiatePaymentService(
            bookingRepository,
            paymentRepository,
            paymobClient,
            salesControlRepository,
            transactionRunner,
            clock,
        )

    @Bean("stoHandlePaymobWebhookService")
    fun handlePaymobWebhookService(
        @Qualifier("stoPaymentRepositoryImpl") paymentRepository: PaymentRepository,
        @Qualifier("stoBookingRepositoryImpl") bookingRepository: BookingRepository,
        @Qualifier("stoConfirmBookingService") confirmBookingService: ConfirmBookingService,
        @Qualifier("stoPaymobClient") paymobClient: PaymobClient,
        outboxWriter: OutboxWriter,
        transactionRunner: TransactionRunner,
        @Qualifier("stoObjectMapper") objectMapper: ObjectMapper,
        clock: Clock,
    ): HandlePaymobWebhookService =
        HandlePaymobWebhookService(
            paymentRepository,
            bookingRepository,
            confirmBookingService,
            paymobClient,
            outboxWriter,
            transactionRunner,
            objectMapper,
            clock,
        )

    @Bean("stoExpireOverduePaymentsService")
    fun expireOverduePaymentsService(
        @Qualifier("stoBookingRepositoryImpl") bookingRepository: BookingRepository,
        @Qualifier("stoExpireBookingService") expireBookingService: ExpireBookingService,
        clock: Clock,
    ): ExpireOverduePaymentsService =
        ExpireOverduePaymentsService(
            bookingRepository,
            expireBookingService,
            clock,
        )
}
