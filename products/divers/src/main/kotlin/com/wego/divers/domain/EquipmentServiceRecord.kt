package com.wego.divers.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** One real maintenance event on one item — append-only, never edited or removed. */
data class EquipmentServiceRecord(
    val id: UUID,
    val equipmentId: EquipmentId,
    val servicedOn: LocalDate,
    val description: String,
    val performedBy: String?,
    val createdAt: Instant,
) {
    init {
        require(description.isNotBlank()) { "Service record description must not be blank" }
    }
}
