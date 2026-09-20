package com.wego.payroll.infrastructure

import com.wego.identity.AuthenticatedApiPrefix
import com.wego.payroll.application.CreatePayrollRunService
import com.wego.payroll.application.DiscardPayrollRunService
import com.wego.payroll.application.PayrollEmployeeLookup
import com.wego.payroll.application.PayrollRunQueryService
import com.wego.payroll.application.PayrollRunRepository
import com.wego.payroll.application.PostPayrollRunService
import com.wego.payroll.application.SalaryJournalPoster
import com.wego.transaction.TransactionRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration(proxyBeanMethods = false)
class PayrollBeanConfiguration {
    // Declares this product's own API surface to kernel security — see
    // AuthenticatedApiPrefix's doc comment and DiversBeanConfiguration's
    // identical pattern. This bean contribution replaces the hardcoded
    // "/api/v1/payroll/**" rule SecurityConfiguration carried before the
    // generalized-prefix mechanism existed.
    @Bean
    fun payrollAuthenticatedApiPrefix(): AuthenticatedApiPrefix = AuthenticatedApiPrefix("/api/v1/payroll/**")

    @Bean
    fun createPayrollRunService(
        payrollEmployeeLookup: PayrollEmployeeLookup,
        payrollRunRepository: PayrollRunRepository,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): CreatePayrollRunService = CreatePayrollRunService(payrollEmployeeLookup, payrollRunRepository, transactionRunner, clock)

    @Bean
    fun postPayrollRunService(
        payrollRunRepository: PayrollRunRepository,
        salaryJournalPoster: SalaryJournalPoster,
        transactionRunner: TransactionRunner,
        clock: Clock,
    ): PostPayrollRunService = PostPayrollRunService(payrollRunRepository, salaryJournalPoster, transactionRunner, clock)

    @Bean
    fun discardPayrollRunService(
        payrollRunRepository: PayrollRunRepository,
        transactionRunner: TransactionRunner,
    ): DiscardPayrollRunService = DiscardPayrollRunService(payrollRunRepository, transactionRunner)

    @Bean
    fun payrollRunQueryService(payrollRunRepository: PayrollRunRepository): PayrollRunQueryService =
        PayrollRunQueryService(payrollRunRepository)
}
