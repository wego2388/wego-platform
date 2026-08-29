package com.wego.divers.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** One real rental event on one item. `returnedOn == null` means the item is currently out. */
data class EquipmentRentalRecord(
    val id: UUID,
    val equipmentId: EquipmentId,
    val customerName: String,
    val rentedOn: LocalDate,
    val returnedOn: LocalDate?,
    val notes: String?,
    val createdAt: Instant,
) {
    init {
        require(customerName.isNotBlank()) { "Rental record customer name must not be blank" }
        require(returnedOn == null || !returnedOn.isBefore(rentedOn)) { "Return date must not be before the rental date" }
    }

    val isOpen: Boolean get() = returnedOn == null
}
