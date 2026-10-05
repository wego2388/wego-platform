package com.wego.toursoperator.api

import com.wego.identity.AuthenticatedUser
import com.wego.toursoperator.application.FxRateService
import com.wego.toursoperator.domain.FxRate
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock

/**
 * The manager-set daily EUR→EGP rate (policy proposed; owner confirmation
 * pending). Anyone who may record office payments can read today's rate;
 * only a manager can set it, and every rate is kept as history.
 */
@Validated
@RestController("toursOperatorFxRateController")
@RequestMapping("/api/v1/tours-operator/staff/fx-rate")
class FxRateController(
    private val fxRateService: FxRateService,
    private val clock: Clock,
) {
    @GetMapping("/today")
    @PreAuthorize("hasAnyAuthority('tours-operator.booking:collect-cash', 'tours-operator.fx-rate:manage')")
    fun today(): FxRateTodayResponse = FxRateTodayResponse(FxRateService.todayInSharm(clock), fxRateService.today()?.toResponse())

    @PostMapping
    @PreAuthorize("hasAuthority('tours-operator.fx-rate:manage')")
    fun set(
        @Valid @RequestBody request: SetFxRateRequest,
        authentication: Authentication,
    ): ResponseEntity<FxRateResponse> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        val rate = fxRateService.set(request.egpPerEur.setScale(FxRate.RATE_SCALE), actorUserId)
        return ResponseEntity.status(HttpStatus.CREATED).body(rate.toResponse())
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('tours-operator.fx-rate:manage')")
    fun history(
        @RequestParam(defaultValue = "30") @Min(1) @Max(200) limit: Int,
    ): List<FxRateResponse> = fxRateService.history(limit).map { it.toResponse() }
}

private fun FxRate.toResponse() = FxRateResponse(id, rateDate, egpPerEur.toPlainString(), setByUserId, setAt)
