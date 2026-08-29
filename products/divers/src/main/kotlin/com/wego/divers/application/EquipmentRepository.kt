package com.wego.divers.application

import com.wego.divers.domain.Equipment
import com.wego.divers.domain.EquipmentId
import com.wego.divers.domain.EquipmentStatus
import com.wego.divers.domain.EquipmentType

interface EquipmentRepository {
    fun findById(id: EquipmentId): Equipment?

    /** Row-locked read for a read-modify-write cycle — see JooqOfferingRepository.findByIdForUpdate for the established pattern. */
    fun findByIdForUpdate(id: EquipmentId): Equipment?

    fun findByQrCode(qrCode: String): Equipment?

    fun findAll(
        equipmentType: EquipmentType?,
        status: EquipmentStatus?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<Equipment>

    fun save(equipment: Equipment)
}
