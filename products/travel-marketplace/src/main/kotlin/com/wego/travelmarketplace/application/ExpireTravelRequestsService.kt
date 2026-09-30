package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.TravelRequestActorType
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * The expiry sweep: every `NEW`/`IN_REVIEW` request whose
 * [com.wego.travelmarketplace.domain.TravelRequest.requestedDate] has
 * already passed without confirmation moves to `EXPIRED`. Scheduling this
 * (a `@Scheduled` bean, mirroring `com.wego.toursoperator.infrastructure.BookingExpiryScheduler`'s
 * pattern) is deliberately not wired in this sub-packet — this service is
 * the pure, independently-testable unit the scheduler will call once it is
 * added alongside the rest of the API layer.
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
                if (locked.status.isTerminal) return@runInTransaction

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
