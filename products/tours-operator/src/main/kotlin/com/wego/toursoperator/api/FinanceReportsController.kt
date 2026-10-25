package com.wego.toursoperator.api

import com.wego.toursoperator.application.FinanceReportService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * SAF-04 Slice B read-only reports. Reversals and departure-day money use the finance permission
 * `payment:view` (the most restrictive existing finance read: it guards profitability and the
 * office summary). Cash variances need `cash-box:confirm` or `payment:view`, not the reception
 * `cash-box:close`, so a counter does not browse everyone's variance history. Request conversion
 * uses `booking:view` like the requests inbox. Every CSV uses the same permission as its report.
 */
@Validated
@RestController("toursOperatorFinanceReportsController")
@RequestMapping("/api/v1/tours-operator/staff/reports")
class FinanceReportsController(
    private val reports: FinanceReportService,
) {
    private companion object {
        const val FINANCE = "hasAuthority('tours-operator.payment:view')"
        const val CASH = "hasAnyAuthority('tours-operator.cash-box:confirm', 'tours-operator.payment:view')"
        const val REQUESTS = "hasAuthority('tours-operator.booking:view')"
    }

    private fun bad(code: String): ResponseEntity<Any> = ResponseEntity.badRequest().body(ErrorResponse(code))

    private fun badRange(
        from: LocalDate,
        to: LocalDate,
    ) = to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > FinanceReportService.MAX_RANGE_DAYS

    private fun json(body: Any): ResponseEntity<Any> = ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body)

    private fun csv(
        name: String,
        bytes: ByteArray,
    ): ResponseEntity<Any> =
        ResponseEntity
            .ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$name\"")
            .body(bytes)

    private fun checked(
        from: LocalDate,
        to: LocalDate,
        lang: String,
        then: (Boolean) -> ResponseEntity<Any>,
    ): ResponseEntity<Any> =
        when {
            badRange(from, to) -> bad("invalid_date_range")
            lang != "en" && lang != "ar" -> bad("invalid_language")
            else -> then(lang == "ar")
        }

    @GetMapping("/reversals")
    @PreAuthorize(FINANCE)
    fun reversals(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): ResponseEntity<Any> = if (badRange(from, to)) bad("invalid_date_range") else json(reports.reversals(from, to).toResponse())

    @GetMapping("/reversals.csv")
    @PreAuthorize(FINANCE)
    fun reversalsCsv(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(defaultValue = "en") lang: String,
    ): ResponseEntity<Any> =
        checked(from, to, lang) { ar -> csv("reversals-$from-$to.csv", FinanceReportCsv.reversals(reports.reversals(from, to), ar)) }

    @GetMapping("/cash-variances")
    @PreAuthorize(CASH)
    fun cashVariances(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): ResponseEntity<Any> = if (badRange(from, to)) bad("invalid_date_range") else json(reports.cashVariances(from, to).toResponse())

    @GetMapping("/cash-variances.csv")
    @PreAuthorize(CASH)
    fun cashVariancesCsv(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(defaultValue = "en") lang: String,
    ): ResponseEntity<Any> =
        checked(
            from,
            to,
            lang,
        ) { ar -> csv("cash-variances-$from-$to.csv", FinanceReportCsv.cashVariances(reports.cashVariances(from, to), ar)) }

    @GetMapping("/request-conversion")
    @PreAuthorize(REQUESTS)
    fun requestConversion(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): ResponseEntity<Any> = if (badRange(from, to)) bad("invalid_date_range") else json(reports.requestConversion(from, to).toResponse())

    @GetMapping("/request-conversion.csv")
    @PreAuthorize(REQUESTS)
    fun requestConversionCsv(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(defaultValue = "en") lang: String,
    ): ResponseEntity<Any> =
        checked(
            from,
            to,
            lang,
        ) { ar -> csv("request-conversion-$from-$to.csv", FinanceReportCsv.requestConversion(reports.requestConversion(from, to), ar)) }

    @GetMapping("/departure-money")
    @PreAuthorize(FINANCE)
    fun departureMoney(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
    ): ResponseEntity<Any> = if (badRange(from, to)) bad("invalid_date_range") else json(reports.departureMoney(from, to).toResponse())

    @GetMapping("/departure-money.csv")
    @PreAuthorize(FINANCE)
    fun departureMoneyCsv(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(defaultValue = "en") lang: String,
    ): ResponseEntity<Any> =
        checked(
            from,
            to,
            lang,
        ) { ar -> csv("departure-money-$from-$to.csv", FinanceReportCsv.departureMoney(reports.departureMoney(from, to), ar)) }
}
