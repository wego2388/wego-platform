package com.wego.travelmarketplace.application

import com.wego.travelmarketplace.domain.TravelRequestAuditEvent
import java.util.UUID

class TravelRequestAuditQueryService(
    private val auditRecorder: TravelRequestAuditRecorder,
) {
    fun findByRequestId(requestId: UUID): List<TravelRequestAuditEvent> = auditRecorder.findByRequestId(requestId)
}
