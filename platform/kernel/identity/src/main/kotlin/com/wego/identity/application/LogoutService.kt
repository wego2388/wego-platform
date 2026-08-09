package com.wego.identity.application

import com.wego.identity.domain.Session
import com.wego.identity.domain.UserId
import java.time.Clock
import java.time.Instant
import java.util.UUID

class LogoutService(
    private val sessionRepository: SessionRepository,
    private val auditRecorder: IdentityAuditRecorder,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun logout(
        session: Session,
        userId: UserId,
        correlationId: UUID?,
    ) {
        transactionRunner.runInTransaction {
            val now = Instant.now(clock)
            sessionRepository.revoke(session.id, now)
            auditRecorder.recordLogout(userId, now, correlationId)
        }
    }
}
