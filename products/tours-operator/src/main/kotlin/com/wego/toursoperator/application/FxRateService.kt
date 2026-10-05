package com.wego.toursoperator.application

import com.wego.toursoperator.domain.FxRate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/**
 * The manager-set daily EUR→EGP rate used to settle EGP office payments.
 * Policy status: proposed, owner confirmation pending. Staff never type a
 * rate; they only see today's.
 */
class FxRateService(
    private val fxRateRepository: FxRateRepository,
    private val clock: Clock,
) {
    fun today(): FxRate? = fxRateRepository.latestFor(todayInSharm(clock))

    fun set(
        egpPerEur: BigDecimal,
        actorUserId: UUID,
    ): FxRate {
        val now = Instant.now(clock)
        val rate =
            FxRate(
                id = UUID.randomUUID(),
                rateDate = todayInSharm(clock),
                egpPerEur = egpPerEur,
                setByUserId = actorUserId,
                setAt = now,
            )
        fxRateRepository.append(rate)
        return rate
    }

    fun history(limit: Int): List<FxRate> = fxRateRepository.history(limit)

    companion object {
        private val SHARM_ZONE: ZoneId = ZoneId.of("Africa/Cairo")

        fun todayInSharm(clock: Clock): LocalDate = LocalDate.now(clock.withZone(SHARM_ZONE))
    }
}
