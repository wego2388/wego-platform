package com.wego.toursoperator.application

import com.wego.toursoperator.domain.OnlineRequest
import com.wego.toursoperator.domain.OnlineRequestAudit
import com.wego.toursoperator.domain.OnlineRequestStatus
import java.util.UUID

interface OnlineRequestRepository {
    /** Conflict-safe insert; false means another transaction owns this id. */
    fun insert(request: OnlineRequest): Boolean

    fun find(
        id: UUID,
        lock: Boolean = false,
    ): OnlineRequest?

    fun list(
        status: OnlineRequestStatus?,
        page: Int,
        size: Int,
    ): List<OnlineRequest>

    fun countOpen(): Int

    fun save(request: OnlineRequest)

    fun audit(
        request: OnlineRequest,
        actorUserId: UUID?,
    )

    fun history(id: UUID): List<OnlineRequestAudit>
}
