package com.wego.travelmarketplace.infrastructure

import com.wego.identity.AuthenticatedApiPrefix
import com.wego.identity.PublicApiPrefix
import com.wego.travelmarketplace.application.ApproveServiceService
import com.wego.travelmarketplace.application.ArchiveCategoryService
import com.wego.travelmarketplace.application.ArchiveProviderService
import com.wego.travelmarketplace.application.ArchiveServiceService
import com.wego.travelmarketplace.application.CategoryQueryService
import com.wego.travelmarketplace.application.CategoryRepository
import com.wego.travelmarketplace.application.CreateCategoryService
import com.wego.travelmarketplace.application.CreateProviderService
import com.wego.travelmarketplace.application.CreateServiceService
import com.wego.travelmarketplace.application.ProviderQueryService
import com.wego.travelmarketplace.application.ProviderRepository
import com.wego.travelmarketplace.application.PublicCatalogQueryService
import com.wego.travelmarketplace.application.PublishServiceService
import com.wego.travelmarketplace.application.ServiceQueryService
import com.wego.travelmarketplace.application.ServiceRepository
import com.wego.travelmarketplace.application.SubmitServiceForReviewService
import com.wego.travelmarketplace.application.SuspendServiceService
import com.wego.travelmarketplace.application.TransactionRunner
import com.wego.travelmarketplace.application.TravelMarketplaceAuditRecorder
import com.wego.travelmarketplace.application.UpdateCategoryService
import com.wego.travelmarketplace.application.UpdateProviderService
import com.wego.travelmarketplace.application.UpdateServiceService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
}
