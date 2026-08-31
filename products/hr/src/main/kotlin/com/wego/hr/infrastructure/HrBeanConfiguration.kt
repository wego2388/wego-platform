package com.wego.hr.infrastructure

import com.wego.hr.application.CreateEmployeeService
import com.wego.hr.application.EmployeeAuditRecorder
import com.wego.hr.application.EmployeeQueryService
import com.wego.hr.application.EmployeeRepository
import com.wego.hr.application.StaffUserLookup
import com.wego.hr.application.TerminateEmployeeService
import com.wego.hr.application.UpdateEmployeeService
import com.wego.transaction.TransactionRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class HrBeanConfiguration {
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
}
