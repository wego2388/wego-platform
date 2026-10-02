package com.wego.travelmarketplace.infrastructure

import com.wego.travelmarketplace.application.ExpireTravelRequestsService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Wires [ExpireTravelRequestsService] into a real production schedule.
 * The service itself was deliberately left schedule-agnostic and
 * independently testable (see its own class doc) — this is the one place
 * that decides when it actually runs, and until now nothing did: the
 * service existed, fully tested, with no caller in the real application.
 *
 * `fixedDelay`, not `fixedRate`: the next run only starts 15 minutes after
 * the previous one *finishes*, so a slow sweep (or a growing backlog) can
 * never overlap itself. [requestedDate] is a calendar day, not an instant,
 * so this cadence is far tighter than the check itself needs — it exists
 * at this frequency only so a request that crosses midnight is never
 * visibly stale on the staff dashboard for more than a few minutes.
 *
 * `initialDelay` is 5 minutes, comfortably longer than this app's entire
 * backend test suite takes to run end to end — deliberately chosen so no
 * `@SpringBootTest` class needs its own explicit opt-out wiring to avoid a
 * sweep firing mid-test; it is not meant to delay anything meaningful in a
 * real deployment.
 */
@Component
class ExpireTravelRequestsScheduler(
    private val expireTravelRequestsService: ExpireTravelRequestsService,
) {
    @Scheduled(fixedDelay = FIFTEEN_MINUTES_MS, initialDelay = FIVE_MINUTES_MS)
    fun run() {
        val expiredCount = expireTravelRequestsService.expireOverdue()
        if (expiredCount > 0) {
            logger.info("Expiry sweep expired {} overdue travel request(s)", expiredCount)
        }
    }

    companion object {
        private const val FIFTEEN_MINUTES_MS = 15 * 60 * 1000L
        private const val FIVE_MINUTES_MS = 5 * 60 * 1000L
        private val logger = LoggerFactory.getLogger(ExpireTravelRequestsScheduler::class.java)
    }
}
