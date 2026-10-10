package com.wego.travelmarketplace.infrastructure

import com.wego.identity.AuthenticatedApiPrefix
import com.wego.identity.PublicApiPrefix
import com.wego.travelmarketplace.application.ApproveServiceService
import com.wego.travelmarketplace.application.ArchiveCategoryService
import com.wego.travelmarketplace.application.ArchiveProviderService
import com.wego.travelmarketplace.application.ArchiveServiceService
import com.wego.travelmarketplace.application.CancelTravelRequestService
import com.wego.travelmarketplace.application.CategoryQueryService
import com.wego.travelmarketplace.application.CategoryRepository
import com.wego.travelmarketplace.application.CompleteTravelRequestService
import com.wego.travelmarketplace.application.ConfirmTravelRequestService
import com.wego.travelmarketplace.application.CreateCategoryService
import com.wego.travelmarketplace.application.CreateProviderService
import com.wego.travelmarketplace.application.CreateServiceService
import com.wego.travelmarketplace.application.CreateTravelRequestService
import com.wego.travelmarketplace.application.DispatchNotificationsService
import com.wego.travelmarketplace.application.EmailSender
import com.wego.travelmarketplace.application.ExpireTravelRequestsService
import com.wego.travelmarketplace.application.NotificationRepository
import com.wego.travelmarketplace.application.NotificationSettings
import com.wego.travelmarketplace.application.ProviderQueryService
import com.wego.travelmarketplace.application.ProviderRepository
import com.wego.travelmarketplace.application.PublicCatalogQueryService
import com.wego.travelmarketplace.application.PublishServiceService
import com.wego.travelmarketplace.application.ResendNotificationService
import com.wego.travelmarketplace.application.SMTP_TIMEOUT
import com.wego.travelmarketplace.application.SalesControlRepository
import com.wego.travelmarketplace.application.SalesControlService
import com.wego.travelmarketplace.application.ServiceQueryService
import com.wego.travelmarketplace.application.ServiceRepository
import com.wego.travelmarketplace.application.StartTravelRequestReviewService
import com.wego.travelmarketplace.application.SubmitServiceForReviewService
import com.wego.travelmarketplace.application.SuspendServiceService
import com.wego.travelmarketplace.application.TransactionRunner
import com.wego.travelmarketplace.application.TravelMarketplaceAuditRecorder
import com.wego.travelmarketplace.application.TravelRequestAuditQueryService
import com.wego.travelmarketplace.application.TravelRequestAuditRecorder
import com.wego.travelmarketplace.application.TravelRequestQueryService
import com.wego.travelmarketplace.application.TravelRequestRepository
import com.wego.travelmarketplace.application.UpdateCategoryService
import com.wego.travelmarketplace.application.UpdateProviderService
import com.wego.travelmarketplace.application.UpdateServiceService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSenderImpl
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class TravelMarketplaceBeanConfiguration {
    // Declares this product's own API surface to kernel security — see
    // AuthenticatedApiPrefix/PublicApiPrefix's doc comments. An application
    // built without this module on its compile classpath (e.g. the Sharm
    // Divers Club app) never registers these beans.
    @Bean
    fun travelMarketplacePublicApiPrefix(): PublicApiPrefix = PublicApiPrefix("/api/v1/travel-marketplace/public/**")

    @Bean
    fun travelMarketplaceAuthenticatedApiPrefix(): AuthenticatedApiPrefix = AuthenticatedApiPrefix("/api/v1/travel-marketplace/**")

    @Bean
    fun createProviderService(
        providerRepository: ProviderRepository,
        auditRecorder: TravelMarketplaceAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): CreateProviderService = CreateProviderService(providerRepository, auditRecorder, transactionRunner, clock)

    @Bean
    fun updateProviderService(
        providerRepository: ProviderRepository,
        auditRecorder: TravelMarketplaceAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): UpdateProviderService = UpdateProviderService(providerRepository, auditRecorder, transactionRunner, clock)

    @Bean
    fun archiveProviderService(
        providerRepository: ProviderRepository,
        auditRecorder: TravelMarketplaceAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): ArchiveProviderService = ArchiveProviderService(providerRepository, auditRecorder, transactionRunner, clock)

    @Bean
    fun providerQueryService(providerRepository: ProviderRepository): ProviderQueryService = ProviderQueryService(providerRepository)

    @Bean
    fun createCategoryService(
        categoryRepository: CategoryRepository,
        auditRecorder: TravelMarketplaceAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): CreateCategoryService = CreateCategoryService(categoryRepository, auditRecorder, transactionRunner, clock)

    @Bean
    fun updateCategoryService(
        categoryRepository: CategoryRepository,
        auditRecorder: TravelMarketplaceAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): UpdateCategoryService = UpdateCategoryService(categoryRepository, auditRecorder, transactionRunner, clock)

    @Bean
    fun archiveCategoryService(
        categoryRepository: CategoryRepository,
        auditRecorder: TravelMarketplaceAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): ArchiveCategoryService = ArchiveCategoryService(categoryRepository, auditRecorder, transactionRunner, clock)

    @Bean
    fun categoryQueryService(categoryRepository: CategoryRepository): CategoryQueryService = CategoryQueryService(categoryRepository)

    @Bean
    fun createServiceService(
        serviceRepository: ServiceRepository,
        categoryRepository: CategoryRepository,
        providerRepository: ProviderRepository,
        auditRecorder: TravelMarketplaceAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): CreateServiceService =
        CreateServiceService(serviceRepository, categoryRepository, providerRepository, auditRecorder, transactionRunner, clock)

    @Bean
    fun updateServiceService(
        serviceRepository: ServiceRepository,
        categoryRepository: CategoryRepository,
        providerRepository: ProviderRepository,
        auditRecorder: TravelMarketplaceAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): UpdateServiceService =
        UpdateServiceService(serviceRepository, categoryRepository, providerRepository, auditRecorder, transactionRunner, clock)

    @Bean
    fun submitServiceForReviewService(
        serviceRepository: ServiceRepository,
        auditRecorder: TravelMarketplaceAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): SubmitServiceForReviewService = SubmitServiceForReviewService(serviceRepository, auditRecorder, transactionRunner, clock)

    @Bean
    fun approveServiceService(
        serviceRepository: ServiceRepository,
        auditRecorder: TravelMarketplaceAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): ApproveServiceService = ApproveServiceService(serviceRepository, auditRecorder, transactionRunner, clock)

    @Bean
    fun publishServiceService(
        serviceRepository: ServiceRepository,
        auditRecorder: TravelMarketplaceAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): PublishServiceService = PublishServiceService(serviceRepository, auditRecorder, transactionRunner, clock)

    @Bean
    fun suspendServiceService(
        serviceRepository: ServiceRepository,
        auditRecorder: TravelMarketplaceAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): SuspendServiceService = SuspendServiceService(serviceRepository, auditRecorder, transactionRunner, clock)

    @Bean
    fun archiveServiceService(
        serviceRepository: ServiceRepository,
        auditRecorder: TravelMarketplaceAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): ArchiveServiceService = ArchiveServiceService(serviceRepository, auditRecorder, transactionRunner, clock)

    @Bean
    fun serviceQueryService(serviceRepository: ServiceRepository): ServiceQueryService = ServiceQueryService(serviceRepository)

    @Bean
    fun publicCatalogQueryService(
        serviceRepository: ServiceRepository,
        categoryRepository: CategoryRepository,
    ): PublicCatalogQueryService = PublicCatalogQueryService(serviceRepository, categoryRepository)

    @Bean
    fun createTravelRequestService(
        serviceRepository: ServiceRepository,
        requestRepository: TravelRequestRepository,
        auditRecorder: TravelRequestAuditRecorder,
        notificationRepository: NotificationRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
        salesControlRepository: SalesControlRepository,
    ): CreateTravelRequestService =
        CreateTravelRequestService(
            serviceRepository,
            requestRepository,
            auditRecorder,
            notificationRepository,
            transactionRunner,
            clock,
            salesControlRepository,
        )

    @Bean
    fun salesControlService(
        repository: SalesControlRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): SalesControlService = SalesControlService(repository, transactionRunner, clock)

    @Bean
    fun startTravelRequestReviewService(
        requestRepository: TravelRequestRepository,
        auditRecorder: TravelRequestAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): StartTravelRequestReviewService = StartTravelRequestReviewService(requestRepository, auditRecorder, transactionRunner, clock)

    @Bean
    fun confirmTravelRequestService(
        requestRepository: TravelRequestRepository,
        auditRecorder: TravelRequestAuditRecorder,
        notificationRepository: NotificationRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): ConfirmTravelRequestService =
        ConfirmTravelRequestService(requestRepository, auditRecorder, notificationRepository, transactionRunner, clock)

    @Bean
    fun cancelTravelRequestService(
        requestRepository: TravelRequestRepository,
        auditRecorder: TravelRequestAuditRecorder,
        notificationRepository: NotificationRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): CancelTravelRequestService =
        CancelTravelRequestService(requestRepository, auditRecorder, notificationRepository, transactionRunner, clock)

    @Bean
    fun completeTravelRequestService(
        requestRepository: TravelRequestRepository,
        auditRecorder: TravelRequestAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): CompleteTravelRequestService = CompleteTravelRequestService(requestRepository, auditRecorder, transactionRunner, clock)

    @Bean
    fun expireTravelRequestsService(
        requestRepository: TravelRequestRepository,
        auditRecorder: TravelRequestAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): ExpireTravelRequestsService = ExpireTravelRequestsService(requestRepository, auditRecorder, transactionRunner, clock)

    @Bean
    fun travelRequestQueryService(requestRepository: TravelRequestRepository): TravelRequestQueryService =
        TravelRequestQueryService(requestRepository)

    @Bean
    fun travelRequestAuditQueryService(auditRecorder: TravelRequestAuditRecorder): TravelRequestAuditQueryService =
        TravelRequestAuditQueryService(auditRecorder)

    // ── Notifications ────────────────────────────────────────────────────────

    /**
     * SMTP when travel-marketplace.notifications.smtp.host is set. The sender
     * is built here from our own settings, deliberately NOT from spring.mail.*:
     * Spring Boot creates a JavaMailSender (and its health indicator) as soon
     * as spring.mail.host exists, even as an empty string, and an empty
     * environment variable from compose counts as existing. A blank host here
     * simply means "not configured", and enabling the dispatcher without one
     * is a configuration error that fails startup instead of silently failing
     * every email.
     */
    @Bean
    fun travelNotificationEmailSender(
        @Value("\${travel-marketplace.notifications.smtp.host:}") host: String,
        @Value("\${travel-marketplace.notifications.smtp.port:587}") port: Int,
        @Value("\${travel-marketplace.notifications.smtp.username:}") username: String,
        @Value("\${travel-marketplace.notifications.smtp.password:}") password: String,
        @Value("\${travel-marketplace.notifications.enabled:false}") enabled: Boolean,
        @Value("\${travel-marketplace.notifications.from:}") from: String,
        @Value("\${travel-marketplace.notifications.reply-to:}") replyTo: String,
    ): EmailSender {
        val smtp = buildMailSender(host, port, username, password)
        if (smtp == null) {
            require(!enabled) { "travel-marketplace.notifications.enabled=true requires travel-marketplace.notifications.smtp.host" }
            return EmailSender { throw IllegalStateException("email_not_configured") }
        }
        require(!enabled || from.isNotBlank()) { "travel-marketplace.notifications.from is required when notifications are enabled" }
        return SmtpEmailSender(smtp, from, replyTo.ifBlank { null })
    }

    /** Null for a blank host: no sender is ever created for "not configured". */
    internal fun buildMailSender(
        host: String,
        port: Int,
        username: String,
        password: String,
    ): JavaMailSenderImpl? {
        if (host.isBlank()) return null
        val sender = JavaMailSenderImpl()
        sender.host = host.trim()
        sender.port = port
        if (username.isNotBlank()) {
            sender.username = username
            sender.password = password
            sender.javaMailProperties["mail.smtp.auth"] = "true"
        }
        if (port == SMTPS_PORT) {
            // Implicit TLS (SMTPS).
            sender.protocol = "smtps"
            sender.javaMailProperties["mail.smtps.auth"] = (username.isNotBlank()).toString()
        } else {
            // Never send credentials or customer mail in clear text: refuse a
            // relay that does not offer STARTTLS (or has it stripped).
            sender.javaMailProperties["mail.smtp.starttls.enable"] = "true"
            sender.javaMailProperties["mail.smtp.starttls.required"] = "true"
        }
        SmtpEmailSender.applyDefaultTimeouts(sender, SMTP_TIMEOUT)
        return sender
    }

    @Bean
    fun dispatchNotificationsService(
        notificationRepository: NotificationRepository,
        requestRepository: TravelRequestRepository,
        emailSender: EmailSender,
        transactionRunner: TransactionRunner,
        @Value("\${travel-marketplace.notifications.site-base-url:http://localhost:3000}") siteBaseUrl: String,
        @Value("\${travel-marketplace.notifications.staff-address:}") staffAddress: String,
        @Value("\${travel-marketplace.notifications.whatsapp:+20 10 0141 3469}") whatsapp: String,
        @Value("\${travel-marketplace.notifications.contact-email:info@sharmtogo.com}") contactEmail: String,
        @Value("\${travel-marketplace.notifications.max-attempts:5}") maxAttempts: Int,
        @Value("\${travel-marketplace.notifications.enabled:false}") enabled: Boolean,
        clock: Clock,
    ): DispatchNotificationsService {
        // Every email links to the public site; never let a live dispatcher
        // send customers to a localhost or plain-http default.
        require(!enabled || siteBaseUrl.startsWith("https://")) {
            "travel-marketplace.notifications.site-base-url must be an https:// URL when notifications are enabled"
        }
        return DispatchNotificationsService(
            notificationRepository,
            requestRepository,
            emailSender,
            transactionRunner,
            NotificationSettings(siteBaseUrl, staffAddress.ifBlank { null }, whatsapp, contactEmail, maxAttempts),
            clock,
        )
    }

    @Bean
    fun resendNotificationService(
        notificationRepository: NotificationRepository,
        requestRepository: TravelRequestRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): ResendNotificationService = ResendNotificationService(notificationRepository, requestRepository, transactionRunner, clock)
}

private const val SMTPS_PORT = 465
