package com.wego.divers.application

import com.wego.divers.domain.EquipmentId
import com.wego.divers.domain.EquipmentServiceRecord

interface EquipmentServiceRecordRepository {
    fun findByEquipmentId(
        equipmentId: EquipmentId,
        limit: Int,
        offset: Int,
    ): List<EquipmentServiceRecord>

    fun save(record: EquipmentServiceRecord)
}
