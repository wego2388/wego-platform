package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.SalesControl
import java.time.Clock
import java.time.Instant
import java.util.UUID

interface SalesControlRepository {
    fun current(): SalesControl

    /** Shared lock, held until request creation commits or rolls back. */
    fun lockForRequest(): SalesControl

    /** Exclusive lock, held until the pause/resume transaction finishes. */
    fun lockForUpdate(): SalesControl

    fun saveAndAudit(
        before: SalesControl,
        after: SalesControl,
    )
}

sealed interface UpdateSalesControlResult {
    data class Updated(
        val control: SalesControl,
    ) : UpdateSalesControlResult

    data class VersionConflict(
        val currentVersion: Long,
    ) : UpdateSalesControlResult
}

class SalesControlService(
    private val repository: SalesControlRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    fun current(): SalesControl = repository.current()

    fun update(
        requestsPaused: Boolean,
        expectedVersion: Long,
        reason: String?,
        actorUserId: UUID,
    ): UpdateSalesControlResult =
        transactionRunner.runInTransaction {
            val before = repository.lockForUpdate()
            if (before.version != expectedVersion) {
                return@runInTransaction UpdateSalesControlResult.VersionConflict(before.version)
            }
            val after =
                SalesControl(
                    requestsPaused,
                    before.version + 1,
                    reason?.trim()?.takeIf { it.isNotEmpty() },
                    actorUserId,
                    Instant.now(clock),
                )
            repository.saveAndAudit(before, after)
            UpdateSalesControlResult.Updated(after)
        }
}
