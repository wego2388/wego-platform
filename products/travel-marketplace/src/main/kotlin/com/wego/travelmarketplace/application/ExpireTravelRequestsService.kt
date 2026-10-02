package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.TravelRequestActorType
import com.wego.travelmarketplace.domain.TravelRequestStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The expiry sweep: every `NEW`/`IN_REVIEW` request whose
 * [com.wego.travelmarketplace.domain.TravelRequest.requestedDate] has
 * already passed without confirmation moves to `EXPIRED`. Kept as a pure,
 * independently-testable unit with no scheduling concern of its own —
 * [com.wego.travelmarketplace.infrastructure.ExpireTravelRequestsScheduler]
 * is the real production caller, on a 15-minute `@Scheduled` cadence.
 */
class ExpireTravelRequestsService(
    private val requestRepository: TravelRequestRepository,
    private val auditRecorder: TravelRequestAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun expireOverdue(): Int {
        val today = LocalDate.ofInstant(Instant.now(clock), ZoneOffset.UTC)
        val expirable = transactionRunner.runInTransaction { requestRepository.findExpirable(today) }
        var expiredCount = 0
        expirable.forEach { request ->
            transactionRunner.runInTransaction {
                val locked = requestRepository.findByIdForUpdate(request.id) ?: return@runInTransaction
                // expire() only accepts NEW/IN_REVIEW and throws otherwise.
                // A plain "not terminal" check lets CONFIRMED through (it is
                // not a terminal status), which would throw here and stop
                // the rest of this sweep's batch — this can genuinely race
                // with a real confirm() between candidate selection above
                // and this lock. Skip anything that moved out of the
                // expirable states instead of letting expire() find out.
                if (locked.status != TravelRequestStatus.NEW && locked.status != TravelRequestStatus.IN_REVIEW) {
                    return@runInTransaction
                }

                val fromStatus = locked.status
                val now = Instant.now(clock)
                locked.expire(now)
                requestRepository.save(locked)
                auditRecorder.record(
                    requestId = locked.id.value,
                    fromStatus = fromStatus,
                    toStatus = locked.status,
                    actorType = TravelRequestActorType.SYSTEM,
                    actorUserId = null,
                    reason = null,
                    detail = "Requested date $today has passed without confirmation",
                    correlationId = null,
                    occurredAt = now,
                )
                expiredCount++
            }
        }
        return expiredCount
    }
}
