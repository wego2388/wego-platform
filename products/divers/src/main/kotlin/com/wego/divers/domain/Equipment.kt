package com.wego.divers.domain

import java.time.Instant
import java.util.UUID

class Equipment(
    val id: EquipmentId,
    val equipmentType: EquipmentType,
    val label: String,
    val qrCode: String,
    val itemSize: String?,
    val serialNumber: String?,
    status: EquipmentStatus,
    val createdByUserId: UUID?,
    val createdAt: Instant,
    retiredAt: Instant?,
) {
    var status: EquipmentStatus = status
        private set

    var retiredAt: Instant? = retiredAt
        private set

    init {
        require(label.isNotBlank()) { "Equipment label must not be blank" }
        require(qrCode.isNotBlank()) { "Equipment QR code must not be blank" }
        require((status == EquipmentStatus.RETIRED) == (retiredAt != null)) {
            "retiredAt must be set if and only if the equipment is retired"
        }
    }

    val isRetired: Boolean get() = status == EquipmentStatus.RETIRED

    fun startMaintenance() {
        require(status == EquipmentStatus.ACTIVE) { "Only active equipment can start maintenance" }
        status = EquipmentStatus.IN_MAINTENANCE
    }

    fun completeMaintenance() {
        require(status == EquipmentStatus.IN_MAINTENANCE) { "Only equipment in maintenance can complete it" }
        status = EquipmentStatus.ACTIVE
    }

    /** Terminal: already-retired equipment cannot be retired again. */
    fun retire(now: Instant) {
        require(status != EquipmentStatus.RETIRED) { "Equipment is already retired" }
        status = EquipmentStatus.RETIRED
        retiredAt = now
    }

    /** Identity, QR code, and lifecycle state stay put — only the descriptive fields are editable. */
    fun withUpdatedDetails(
        label: String,
        itemSize: String?,
        serialNumber: String?,
    ): Equipment =
        Equipment(
            id = id,
            equipmentType = equipmentType,
            label = label,
            qrCode = qrCode,
            itemSize = itemSize,
            serialNumber = serialNumber,
            status = status,
            createdByUserId = createdByUserId,
            createdAt = createdAt,
            retiredAt = retiredAt,
        )

    companion object {
        fun create(
            id: EquipmentId,
            equipmentType: EquipmentType,
            label: String,
            qrCode: String,
            itemSize: String?,
            serialNumber: String?,
            createdByUserId: UUID?,
            now: Instant,
        ): Equipment =
            Equipment(
                id = id,
                equipmentType = equipmentType,
                label = label,
                qrCode = qrCode,
                itemSize = itemSize,
                serialNumber = serialNumber,
                status = EquipmentStatus.ACTIVE,
                createdByUserId = createdByUserId,
                createdAt = now,
                retiredAt = null,
            )
    }
}
