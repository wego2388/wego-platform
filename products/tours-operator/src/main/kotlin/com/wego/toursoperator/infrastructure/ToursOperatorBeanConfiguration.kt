package com.wego.toursoperator.infrastructure

import com.wego.events.OutboxWriter
import com.wego.identity.AuthenticatedApiPrefix
import com.wego.identity.PublicApiPrefix
import com.wego.toursoperator.application.BookingAuditRecorder
import com.wego.toursoperator.application.BookingQueryService
import com.wego.toursoperator.application.BookingRepository
import com.wego.toursoperator.application.CancelBookingService
import com.wego.toursoperator.application.CompleteBookingService
import com.wego.toursoperator.application.ConfirmBookingService
import com.wego.toursoperator.application.CreateBookingService
import com.wego.toursoperator.application.CreateSlotService
import com.wego.toursoperator.application.CreateTourService
import com.wego.toursoperator.application.ExpireBookingService
import com.wego.toursoperator.application.SetSlotBlockedService
import com.wego.toursoperator.application.SetTourActiveService
import com.wego.toursoperator.application.TourQueryService
import com.wego.toursoperator.application.TourRepository
import com.wego.toursoperator.application.TourSlotQueryService
import com.wego.toursoperator.application.TourSlotRepository
import com.wego.toursoperator.application.TransactionRunner
import com.wego.toursoperator.application.UpdateTourService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper
import java.time.Clock

/**
 * All bean names are prefixed with "sto" (Safari Tours Operator) to avoid
 * conflicts with identically-named beans from DiversBeanConfiguration.
 *
 * Repositories are discovered via @Repository("sto*Impl") on each class —
 * this ensures the kotlin-spring allopen plugin keeps them open for
 * @Transactional proxy generation. Services are wired here via @Qualifier.
 */
@Configuration(proxyBeanMethods = false)
class ToursOperatorBeanConfiguration {

    // ── Security prefixes ────────────────────────────────────────────────────
    // Declares this product's API surface to kernel security — see
    // AuthenticatedApiPrefix/PublicApiPrefix doc comments.
    // Public: tour catalog reads + public booking creation + booking lookup.
    // Authenticated: everything else (ERP staff operations).

    @Bean
    fun toursOperatorAuthenticatedApiPrefix(): AuthenticatedApiPrefix =
        AuthenticatedApiPrefix("/api/v1/tours-operator/**")

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
    fun toursOperatorPublicToursListPrefix(): PublicApiPrefix =
        PublicApiPrefix("/api/v1/tours-operator/tours")

    @Bean
    fun toursOperatorPublicTourByIdPrefix(): PublicApiPrefix =
        PublicApiPrefix("/api/v1/tours-operator/tours/*")

    @Bean
    fun toursOperatorPublicTourBySlugPrefix(): PublicApiPrefix =
        PublicApiPrefix("/api/v1/tours-operator/tours/by-slug")

    @Bean
    fun toursOperatorPublicTourSlotListPrefix(): PublicApiPrefix =
        PublicApiPrefix("/api/v1/tours-operator/tours/*/slots")

    @Bean
    fun toursOperatorPublicTourSlotByDatePrefix(): PublicApiPrefix =
        PublicApiPrefix("/api/v1/tours-operator/tours/*/slots/by-date")

    @Bean
    fun toursOperatorPublicBookingCreatePrefix(): PublicApiPrefix =
        PublicApiPrefix("/api/v1/tours-operator/bookings")

    @Bean
    fun toursOperatorPublicBookingLookupPrefix(): PublicApiPrefix =
        PublicApiPrefix("/api/v1/tours-operator/bookings/lookup")

    @Bean("stoObjectMapper")
    fun toursOperatorObjectMapper(): ObjectMapper = ObjectMapper()

    // ── Query services ───────────────────────────────────────────────────────

    @Bean("stoTourQueryService")
    fun tourQueryService(
        @Qualifier("stoTourRepositoryImpl") tourRepository: TourRepository,
    ): TourQueryService = TourQueryService(tourRepository)

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
    ): BookingQueryService = BookingQueryService(bookingRepository)

    // ── Command services ─────────────────────────────────────────────────────

    @Bean("stoCreateBookingService")
    fun createBookingService(
        @Qualifier("stoTourRepositoryImpl") tourRepository: TourRepository,
        @Qualifier("stoTourSlotRepositoryImpl") slotRepository: TourSlotRepository,
        @Qualifier("stoBookingRepositoryImpl") bookingRepository: BookingRepository,
        bookingAuditRecorder: BookingAuditRecorder,
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
            outboxWriter,
            transactionRunner,
            toursOperatorObjectMapper,
            clock,
        )

    @Bean("stoConfirmBookingService")
    fun confirmBookingService(
        @Qualifier("stoBookingRepositoryImpl") bookingRepository: BookingRepository,
        bookingAuditRecorder: BookingAuditRecorder,
        outboxWriter: OutboxWriter,
        transactionRunner: TransactionRunner,
        @Qualifier("stoObjectMapper") toursOperatorObjectMapper: ObjectMapper,
        clock: Clock,
    ): ConfirmBookingService =
        ConfirmBookingService(
            bookingRepository,
            bookingAuditRecorder,
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
        outboxWriter: OutboxWriter,
        transactionRunner: TransactionRunner,
        @Qualifier("stoObjectMapper") toursOperatorObjectMapper: ObjectMapper,
        clock: Clock,
    ): CancelBookingService =
        CancelBookingService(
            bookingRepository,
            slotRepository,
            bookingAuditRecorder,
            outboxWriter,
            transactionRunner,
            toursOperatorObjectMapper,
            clock,
        )

    @Bean("stoCompleteBookingService")
    fun completeBookingService(
        @Qualifier("stoBookingRepositoryImpl") bookingRepository: BookingRepository,
        bookingAuditRecorder: BookingAuditRecorder,
        outboxWriter: OutboxWriter,
        transactionRunner: TransactionRunner,
        @Qualifier("stoObjectMapper") toursOperatorObjectMapper: ObjectMapper,
        clock: Clock,
    ): CompleteBookingService =
        CompleteBookingService(
            bookingRepository,
            bookingAuditRecorder,
            outboxWriter,
            transactionRunner,
            toursOperatorObjectMapper,
            clock,
        )

    @Bean("stoExpireBookingService")
    fun expireBookingService(
        @Qualifier("stoBookingRepositoryImpl") bookingRepository: BookingRepository,
        @Qualifier("stoTourSlotRepositoryImpl") slotRepository: TourSlotRepository,
        bookingAuditRecorder: BookingAuditRecorder,
        outboxWriter: OutboxWriter,
        transactionRunner: TransactionRunner,
        @Qualifier("stoObjectMapper") toursOperatorObjectMapper: ObjectMapper,
        clock: Clock,
    ): ExpireBookingService =
        ExpireBookingService(
            bookingRepository,
            slotRepository,
            bookingAuditRecorder,
            outboxWriter,
            transactionRunner,
            toursOperatorObjectMapper,
            clock,
        )
}
