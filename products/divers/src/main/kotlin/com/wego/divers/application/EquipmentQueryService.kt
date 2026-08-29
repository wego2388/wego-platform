package com.wego.divers.application

import com.wego.divers.domain.Equipment
import com.wego.divers.domain.EquipmentId
import com.wego.divers.domain.EquipmentRentalRecord
import com.wego.divers.domain.EquipmentServiceRecord
import com.wego.divers.domain.EquipmentStatus
import com.wego.divers.domain.EquipmentType

class EquipmentQueryService(
    private val equipmentRepository: EquipmentRepository,
    private val serviceRecordRepository: EquipmentServiceRecordRepository,
    private val rentalRecordRepository: EquipmentRentalRecordRepository,
) {
    fun findById(id: EquipmentId): Equipment? = equipmentRepository.findById(id)

    fun findByQrCode(qrCode: String): Equipment? = equipmentRepository.findByQrCode(qrCode)

    fun list(
        equipmentType: EquipmentType?,
        status: EquipmentStatus?,
        search: String?,
        page: Int = 0,
        size: Int = Pagination.DEFAULT_PAGE_SIZE,
    ): List<Equipment> =
        equipmentRepository.findAll(
            equipmentType,
            status,
            search,
            limit = Pagination.boundedSize(size),
            offset = Pagination.offsetFor(page, size),
        )

    fun listServiceRecords(
        equipmentId: EquipmentId,
        page: Int = 0,
        size: Int = Pagination.DEFAULT_PAGE_SIZE,
    ): List<EquipmentServiceRecord> =
        serviceRecordRepository.findByEquipmentId(
            equipmentId,
            limit = Pagination.boundedSize(size),
            offset = Pagination.offsetFor(page, size),
        )

    fun listRentalRecords(
        equipmentId: EquipmentId,
        page: Int = 0,
        size: Int = Pagination.DEFAULT_PAGE_SIZE,
    ): List<EquipmentRentalRecord> =
        rentalRecordRepository.findByEquipmentId(
            equipmentId,
            limit = Pagination.boundedSize(size),
            offset = Pagination.offsetFor(page, size),
        )
}
