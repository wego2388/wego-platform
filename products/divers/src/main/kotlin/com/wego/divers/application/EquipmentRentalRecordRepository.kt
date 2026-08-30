package com.wego.divers.application

import com.wego.divers.domain.EquipmentId
import com.wego.divers.domain.EquipmentRentalRecord
import java.util.UUID

interface EquipmentRentalRecordRepository {
    fun findById(id: UUID): EquipmentRentalRecord?

    fun findByEquipmentId(
        equipmentId: EquipmentId,
        limit: Int,
        offset: Int,
    ): List<EquipmentRentalRecord>

    /** At most one open rental can exist per item — also enforced by a real unique partial index, this is the pre-check. */
    fun findOpenByEquipmentId(equipmentId: EquipmentId): EquipmentRentalRecord?

    /** Row-locked read for a read-modify-write cycle — see JooqOfferingRepository.findByIdForUpdate for the established pattern. */
    fun findOpenByEquipmentIdForUpdate(equipmentId: EquipmentId): EquipmentRentalRecord?

    fun save(record: EquipmentRentalRecord)
}
