package com.wego.hr.infrastructure

import com.wego.hr.application.ApproveLeaveRequestService
import com.wego.hr.application.AttendanceQueryService
import com.wego.hr.application.AttendanceRecordRepository
import com.wego.hr.application.CancelLeaveRequestService
import com.wego.hr.application.CreateEmployeeService
import com.wego.hr.application.EmployeeAuditRecorder
import com.wego.hr.application.EmployeeQueryService
import com.wego.hr.application.EmployeeRepository
import com.wego.hr.application.LeaveRequestAuditRecorder
import com.wego.hr.application.LeaveRequestQueryService
import com.wego.hr.application.LeaveRequestRepository
import com.wego.hr.application.RecordAttendanceService
import com.wego.hr.application.RejectLeaveRequestService
import com.wego.hr.application.StaffUserLookup
import com.wego.hr.application.SubmitLeaveRequestService
import com.wego.hr.application.TerminateEmployeeService
import com.wego.hr.application.UpdateEmployeeService
import com.wego.identity.AuthenticatedApiPrefix
import com.wego.transaction.TransactionRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class HrBeanConfiguration {
    // Declares this product's own API surface to kernel security — see
    // AuthenticatedApiPrefix's doc comment and DiversBeanConfiguration's
    // identical pattern. This bean contribution replaces the hardcoded
    // "/api/v1/hr/**" rule SecurityConfiguration carried before the
    // generalized-prefix mechanism existed.
    @Bean
    fun hrAuthenticatedApiPrefix(): AuthenticatedApiPrefix = AuthenticatedApiPrefix("/api/v1/hr/**")

    @Bean
    fun createEmployeeService(
        employeeRepository: EmployeeRepository,
        staffUserLookup: StaffUserLookup,
        auditRecorder: EmployeeAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): CreateEmployeeService = CreateEmployeeService(employeeRepository, staffUserLookup, auditRecorder, transactionRunner, clock)

    @Bean
    fun updateEmployeeService(
        employeeRepository: EmployeeRepository,
        staffUserLookup: StaffUserLookup,
        auditRecorder: EmployeeAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): UpdateEmployeeService = UpdateEmployeeService(employeeRepository, staffUserLookup, auditRecorder, transactionRunner, clock)

    @Bean
    fun terminateEmployeeService(
        employeeRepository: EmployeeRepository,
        auditRecorder: EmployeeAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): TerminateEmployeeService = TerminateEmployeeService(employeeRepository, auditRecorder, transactionRunner, clock)

    @Bean
    fun employeeQueryService(employeeRepository: EmployeeRepository): EmployeeQueryService = EmployeeQueryService(employeeRepository)

    @Bean
    fun recordAttendanceService(
        employeeRepository: EmployeeRepository,
        attendanceRecordRepository: AttendanceRecordRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): RecordAttendanceService = RecordAttendanceService(employeeRepository, attendanceRecordRepository, transactionRunner, clock)

    @Bean
    fun attendanceQueryService(attendanceRecordRepository: AttendanceRecordRepository): AttendanceQueryService =
        AttendanceQueryService(attendanceRecordRepository)

    @Bean
    fun submitLeaveRequestService(
        employeeRepository: EmployeeRepository,
        leaveRequestRepository: LeaveRequestRepository,
        auditRecorder: LeaveRequestAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): SubmitLeaveRequestService =
        SubmitLeaveRequestService(employeeRepository, leaveRequestRepository, auditRecorder, transactionRunner, clock)

    @Bean
    fun approveLeaveRequestService(
        leaveRequestRepository: LeaveRequestRepository,
        auditRecorder: LeaveRequestAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): ApproveLeaveRequestService = ApproveLeaveRequestService(leaveRequestRepository, auditRecorder, transactionRunner, clock)

    @Bean
    fun rejectLeaveRequestService(
        leaveRequestRepository: LeaveRequestRepository,
        auditRecorder: LeaveRequestAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): RejectLeaveRequestService = RejectLeaveRequestService(leaveRequestRepository, auditRecorder, transactionRunner, clock)

    @Bean
    fun cancelLeaveRequestService(
        leaveRequestRepository: LeaveRequestRepository,
        auditRecorder: LeaveRequestAuditRecorder,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): CancelLeaveRequestService = CancelLeaveRequestService(leaveRequestRepository, auditRecorder, transactionRunner, clock)

    @Bean
    fun leaveRequestQueryService(leaveRequestRepository: LeaveRequestRepository): LeaveRequestQueryService =
        LeaveRequestQueryService(leaveRequestRepository)
}
