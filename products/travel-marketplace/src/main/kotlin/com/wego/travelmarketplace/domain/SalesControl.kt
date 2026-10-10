package com.wego.travelmarketplace.domain

import java.time.Instant
import java.util.UUID

/** Controls new public requests only, independently of confirmation and payment. */
data class SalesControl(
    val requestsPaused: Boolean,
    val version: Long,
    val reason: String?,
    val updatedByUserId: UUID?,
    val updatedAt: Instant?,
) {
    init {
        require(version >= 0)
        require(reason == null || reason.length <= 300)
    }
}
