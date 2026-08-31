package com.wego.divers.infrastructure

import com.wego.divers.application.AddServiceRecordService
import com.wego.divers.application.AdvanceEnrollmentStageService
import com.wego.divers.application.ArchiveDiverService
import com.wego.divers.application.AssignInstructorService
import com.wego.divers.application.BoatCharterAuditRecorder
import com.wego.divers.application.BoatCharterQueryService
import com.wego.divers.application.BoatCharterRepository
import com.wego.divers.application.BookingAuditRecorder
import com.wego.divers.application.BookingQueryService
import com.wego.divers.application.BookingRepository
import com.wego.divers.application.CancelBookingService
import com.wego.divers.application.CloseOfferingService
import com.wego.divers.application.CompleteMaintenanceService
import com.wego.divers.application.CourseEnrollmentAuditRecorder
import com.wego.divers.application.CourseEnrollmentQueryService
import com.wego.divers.application.CourseEnrollmentRepository
import com.wego.divers.application.CourseSkillEvaluationRepository
import com.wego.divers.application.CreateBoatCharterService
import com.wego.divers.application.CreateBookingService
import com.wego.divers.application.CreateDiverService
import com.wego.divers.application.CreateEquipmentService
import com.wego.divers.application.CreateOfferingService
import com.wego.divers.application.DiverAuditRecorder
import com.wego.divers.application.DiverQueryService
import com.wego.divers.application.DiverRepository
import com.wego.divers.application.EndCharterService
import com.wego.divers.application.EnrollDiverInCourseService
import com.wego.divers.application.EquipmentAuditRecorder
import com.wego.divers.application.EquipmentQueryService
import com.wego.divers.application.EquipmentRentalRecordRepository
import com.wego.divers.application.EquipmentRepository
import com.wego.divers.application.EquipmentServiceRecordRepository
import com.wego.divers.application.LinkOfferingToCharterService
import com.wego.divers.application.MarkBookingPaidService
import com.wego.divers.application.OfferingAuditRecorder
import com.wego.divers.application.OfferingBoatCharterLinkRepository
import com.wego.divers.application.OfferingQueryService
import com.wego.divers.application.OfferingRepository
import com.wego.divers.application.RecordRentalReturnService
import com.wego.divers.application.RecordRentalService
import com.wego.divers.application.RecordSkillEvaluationService
import com.wego.divers.application.RefundBookingService
import com.wego.divers.application.RetireEquipmentService
import com.wego.divers.application.StaffUserLookup
import com.wego.divers.application.StartMaintenanceService
import com.wego.divers.application.UnlinkOfferingFromCharterService
import com.wego.divers.application.UpdateBoatCharterService
import com.wego.divers.application.UpdateDiverService
import com.wego.divers.application.UpdateEquipmentService
import com.wego.divers.application.WithdrawEnrollmentService
import com.wego.events.OutboxWriter
import com.wego.transaction.TransactionRunner
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
    fun offeringQueryService(
        offeringRepository: OfferingRepository,
        clock: Clock,
    ): OfferingQueryService = OfferingQueryService(offeringRepository, clock)

    @Bean
    fun bookingQueryService(
        bookingRepository: BookingRepository,
        clock: Clock,
    ): BookingQueryService = BookingQueryService(bookingRepository, clock)

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

    @Bean
    fun createDiverService(
        diverRepository: DiverRepository,
        diverAuditRecorder: DiverAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): CreateDiverService = CreateDiverService(diverRepository, diverAuditRecorder, transactionRunner, clock)

    @Bean
    fun updateDiverService(
        diverRepository: DiverRepository,
        diverAuditRecorder: DiverAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): UpdateDiverService = UpdateDiverService(diverRepository, diverAuditRecorder, transactionRunner, clock)

    @Bean
    fun archiveDiverService(
        diverRepository: DiverRepository,
        diverAuditRecorder: DiverAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): ArchiveDiverService = ArchiveDiverService(diverRepository, diverAuditRecorder, transactionRunner, clock)

    @Bean
    fun diverQueryService(diverRepository: DiverRepository): DiverQueryService = DiverQueryService(diverRepository)

    @Bean
    fun createEquipmentService(
        equipmentRepository: EquipmentRepository,
        equipmentAuditRecorder: EquipmentAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): CreateEquipmentService = CreateEquipmentService(equipmentRepository, equipmentAuditRecorder, transactionRunner, clock)

    @Bean
    fun updateEquipmentService(
        equipmentRepository: EquipmentRepository,
        transactionRunner: TransactionRunner,
    ): UpdateEquipmentService = UpdateEquipmentService(equipmentRepository, transactionRunner)

    @Bean
    fun startMaintenanceService(
        equipmentRepository: EquipmentRepository,
        rentalRecordRepository: EquipmentRentalRecordRepository,
        equipmentAuditRecorder: EquipmentAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): StartMaintenanceService =
        StartMaintenanceService(equipmentRepository, rentalRecordRepository, equipmentAuditRecorder, transactionRunner, clock)

    @Bean
    fun completeMaintenanceService(
        equipmentRepository: EquipmentRepository,
        equipmentAuditRecorder: EquipmentAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): CompleteMaintenanceService = CompleteMaintenanceService(equipmentRepository, equipmentAuditRecorder, transactionRunner, clock)

    @Bean
    fun retireEquipmentService(
        equipmentRepository: EquipmentRepository,
        rentalRecordRepository: EquipmentRentalRecordRepository,
        equipmentAuditRecorder: EquipmentAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): RetireEquipmentService =
        RetireEquipmentService(equipmentRepository, rentalRecordRepository, equipmentAuditRecorder, transactionRunner, clock)

    @Bean
    fun addServiceRecordService(
        equipmentRepository: EquipmentRepository,
        serviceRecordRepository: EquipmentServiceRecordRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): AddServiceRecordService = AddServiceRecordService(equipmentRepository, serviceRecordRepository, transactionRunner, clock)

    @Bean
    fun recordRentalService(
        equipmentRepository: EquipmentRepository,
        rentalRecordRepository: EquipmentRentalRecordRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): RecordRentalService = RecordRentalService(equipmentRepository, rentalRecordRepository, transactionRunner, clock)

    @Bean
    fun recordRentalReturnService(
        rentalRecordRepository: EquipmentRentalRecordRepository,
        transactionRunner: TransactionRunner,
    ): RecordRentalReturnService = RecordRentalReturnService(rentalRecordRepository, transactionRunner)

    @Bean
    fun equipmentQueryService(
        equipmentRepository: EquipmentRepository,
        serviceRecordRepository: EquipmentServiceRecordRepository,
        rentalRecordRepository: EquipmentRentalRecordRepository,
    ): EquipmentQueryService = EquipmentQueryService(equipmentRepository, serviceRecordRepository, rentalRecordRepository)

    @Bean
    fun createBoatCharterService(
        boatCharterRepository: BoatCharterRepository,
        boatCharterAuditRecorder: BoatCharterAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): CreateBoatCharterService = CreateBoatCharterService(boatCharterRepository, boatCharterAuditRecorder, transactionRunner, clock)

    @Bean
    fun updateBoatCharterService(
        boatCharterRepository: BoatCharterRepository,
        linkRepository: OfferingBoatCharterLinkRepository,
        offeringRepository: OfferingRepository,
        transactionRunner: TransactionRunner,
    ): UpdateBoatCharterService = UpdateBoatCharterService(boatCharterRepository, linkRepository, offeringRepository, transactionRunner)

    @Bean
    fun endCharterService(
        boatCharterRepository: BoatCharterRepository,
        boatCharterAuditRecorder: BoatCharterAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): EndCharterService = EndCharterService(boatCharterRepository, boatCharterAuditRecorder, transactionRunner, clock)

    @Bean
    fun boatCharterQueryService(
        boatCharterRepository: BoatCharterRepository,
        linkRepository: OfferingBoatCharterLinkRepository,
    ): BoatCharterQueryService = BoatCharterQueryService(boatCharterRepository, linkRepository)

    @Bean
    fun linkOfferingToCharterService(
        offeringRepository: OfferingRepository,
        boatCharterRepository: BoatCharterRepository,
        linkRepository: OfferingBoatCharterLinkRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): LinkOfferingToCharterService =
        LinkOfferingToCharterService(offeringRepository, boatCharterRepository, linkRepository, transactionRunner, clock)

    @Bean
    fun unlinkOfferingFromCharterService(
        linkRepository: OfferingBoatCharterLinkRepository,
        transactionRunner: TransactionRunner,
    ): UnlinkOfferingFromCharterService = UnlinkOfferingFromCharterService(linkRepository, transactionRunner)

    @Bean
    fun enrollDiverInCourseService(
        diverRepository: DiverRepository,
        offeringRepository: OfferingRepository,
        enrollmentRepository: CourseEnrollmentRepository,
        enrollmentAuditRecorder: CourseEnrollmentAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): EnrollDiverInCourseService =
        EnrollDiverInCourseService(
            diverRepository,
            offeringRepository,
            enrollmentRepository,
            enrollmentAuditRecorder,
            transactionRunner,
            clock,
        )

    @Bean
    fun assignInstructorService(
        enrollmentRepository: CourseEnrollmentRepository,
        staffUserLookup: StaffUserLookup,
        transactionRunner: TransactionRunner,
    ): AssignInstructorService = AssignInstructorService(enrollmentRepository, staffUserLookup, transactionRunner)

    @Bean
    fun advanceEnrollmentStageService(
        enrollmentRepository: CourseEnrollmentRepository,
        enrollmentAuditRecorder: CourseEnrollmentAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): AdvanceEnrollmentStageService =
        AdvanceEnrollmentStageService(enrollmentRepository, enrollmentAuditRecorder, transactionRunner, clock)

    @Bean
    fun withdrawEnrollmentService(
        enrollmentRepository: CourseEnrollmentRepository,
        enrollmentAuditRecorder: CourseEnrollmentAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): WithdrawEnrollmentService = WithdrawEnrollmentService(enrollmentRepository, enrollmentAuditRecorder, transactionRunner, clock)

    @Bean
    fun recordSkillEvaluationService(
        enrollmentRepository: CourseEnrollmentRepository,
        skillEvaluationRepository: CourseSkillEvaluationRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): RecordSkillEvaluationService =
        RecordSkillEvaluationService(enrollmentRepository, skillEvaluationRepository, transactionRunner, clock)

    @Bean
    fun courseEnrollmentQueryService(
        enrollmentRepository: CourseEnrollmentRepository,
        skillEvaluationRepository: CourseSkillEvaluationRepository,
    ): CourseEnrollmentQueryService = CourseEnrollmentQueryService(enrollmentRepository, skillEvaluationRepository)
}
