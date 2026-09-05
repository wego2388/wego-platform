package com.wego.accounting.api

import com.wego.accounting.application.ReportingQueryService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

/**
 * Read-only projections over Accounting's own ledger — `accounting:journal-view`
 * is the right gate (the same permission that already lets a caller read
 * individual journal entries), not a new report-specific permission.
 */
@Validated
@RestController
@RequestMapping("/api/v1/accounting/reports")
class ReportController(
    private val reportingQueryService: ReportingQueryService,
) {
    @GetMapping("/trial-balance")
    @PreAuthorize("hasAuthority('accounting:journal-view')")
    fun trialBalance(
        @RequestParam asOfDate: LocalDate,
    ): TrialBalanceResponse = reportingQueryService.trialBalance(asOfDate).toResponse()

    @GetMapping("/income-statement")
    @PreAuthorize("hasAuthority('accounting:journal-view')")
    fun incomeStatement(
        @RequestParam from: LocalDate,
        @RequestParam to: LocalDate,
    ): IncomeStatementResponse = reportingQueryService.incomeStatement(from, to).toResponse()

    @GetMapping("/balance-sheet")
    @PreAuthorize("hasAuthority('accounting:journal-view')")
    fun balanceSheet(
        @RequestParam asOfDate: LocalDate,
    ): BalanceSheetResponse = reportingQueryService.balanceSheet(asOfDate).toResponse()
}
