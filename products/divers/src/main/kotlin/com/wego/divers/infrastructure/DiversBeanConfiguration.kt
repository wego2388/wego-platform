package com.wego.divers.infrastructure

import com.wego.divers.application.BookingAuditRecorder
import com.wego.divers.application.BookingQueryService
import com.wego.divers.application.BookingRepository
import com.wego.divers.application.CancelBookingService
import com.wego.divers.application.CloseOfferingService
import com.wego.divers.application.CreateBookingService
import com.wego.divers.application.CreateOfferingService
import com.wego.divers.application.MarkBookingPaidService
import com.wego.divers.application.OfferingAuditRecorder
import com.wego.divers.application.OfferingQueryService
import com.wego.divers.application.OfferingRepository
import com.wego.divers.application.RefundBookingService
import com.wego.divers.application.TransactionRunner
import com.wego.events.OutboxWriter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.databind.ObjectMapper
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class DiversBeanConfiguration {
    @Bean
    fun diversEventObjectMapper(): ObjectMapper = ObjectMapper()

    @Bean
    fun createOfferingService(
        offeringRepository: OfferingRepository,
        offeringAuditRecorder: OfferingAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): CreateOfferingService = CreateOfferingService(offeringRepository, offeringAuditRecorder, transactionRunner, clock)

    @Bean
    fun closeOfferingService(
        offeringRepository: OfferingRepository,
        offeringAuditRecorder: OfferingAuditRecorder,
        outboxWriter: OutboxWriter,
        transactionRunner: TransactionRunner,
        diversEventObjectMapper: ObjectMapper,
        clock: Clock,
    ): CloseOfferingService =
        CloseOfferingService(
            offeringRepository,
            offeringAuditRecorder,
            outboxWriter,
            transactionRunner,
            diversEventObjectMapper,
            clock,
        )

    @Bean
    fun offeringQueryService(offeringRepository: OfferingRepository): OfferingQueryService = OfferingQueryService(offeringRepository)

    @Bean
    fun bookingQueryService(bookingRepository: BookingRepository): BookingQueryService = BookingQueryService(bookingRepository)

    @Bean
    fun createBookingService(
        offeringRepository: OfferingRepository,
        bookingRepository: BookingRepository,
        bookingAuditRecorder: BookingAuditRecorder,
        outboxWriter: OutboxWriter,
        transactionRunner: TransactionRunner,
        diversEventObjectMapper: ObjectMapper,
        clock: Clock,
    ): CreateBookingService =
        CreateBookingService(
            offeringRepository,
            bookingRepository,
            bookingAuditRecorder,
            outboxWriter,
            transactionRunner,
            diversEventObjectMapper,
            clock,
        )

    @Bean
    fun cancelBookingService(
        bookingRepository: BookingRepository,
        bookingAuditRecorder: BookingAuditRecorder,
        outboxWriter: OutboxWriter,
        transactionRunner: TransactionRunner,
        diversEventObjectMapper: ObjectMapper,
        clock: Clock,
    ): CancelBookingService =
        CancelBookingService(
            bookingRepository,
            bookingAuditRecorder,
            outboxWriter,
            transactionRunner,
            diversEventObjectMapper,
            clock,
        )

    @Bean
    fun markBookingPaidService(
        bookingRepository: BookingRepository,
        bookingAuditRecorder: BookingAuditRecorder,
        outboxWriter: OutboxWriter,
        transactionRunner: TransactionRunner,
        diversEventObjectMapper: ObjectMapper,
        clock: Clock,
    ): MarkBookingPaidService =
        MarkBookingPaidService(
            bookingRepository,
            bookingAuditRecorder,
            outboxWriter,
            transactionRunner,
            diversEventObjectMapper,
            clock,
        )

    @Bean
    fun refundBookingService(
        bookingRepository: BookingRepository,
        bookingAuditRecorder: BookingAuditRecorder,
        outboxWriter: OutboxWriter,
        transactionRunner: TransactionRunner,
        diversEventObjectMapper: ObjectMapper,
        clock: Clock,
    ): RefundBookingService =
        RefundBookingService(
            bookingRepository,
            bookingAuditRecorder,
            outboxWriter,
            transactionRunner,
            diversEventObjectMapper,
            clock,
        )
}
