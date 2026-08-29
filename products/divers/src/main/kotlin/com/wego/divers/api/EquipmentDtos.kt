package com.wego.divers.api

import com.wego.divers.domain.EquipmentStatus
import com.wego.divers.domain.EquipmentType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private const val MAX_LABEL_LENGTH = 200
private const val MAX_QR_CODE_LENGTH = 128
private const val MAX_SIZE_LENGTH = 16
private const val MAX_SERIAL_LENGTH = 64
private const val MAX_NAME_LENGTH = 200
private const val MAX_TEXT_LENGTH = 1000

data class CreateEquipmentRequest(
    val equipmentType: EquipmentType,
    @field:NotBlank
    @field:Size(max = MAX_LABEL_LENGTH)
    val label: String,
    @field:NotBlank
    @field:Size(max = MAX_QR_CODE_LENGTH)
    val qrCode: String,
    @field:Size(max = MAX_SIZE_LENGTH)
    val itemSize: String?,
    @field:Size(max = MAX_SERIAL_LENGTH)
    val serialNumber: String?,
)

data class UpdateEquipmentRequest(
    @field:NotBlank
    @field:Size(max = MAX_LABEL_LENGTH)
    val label: String,
    @field:Size(max = MAX_SIZE_LENGTH)
    val itemSize: String?,
    @field:Size(max = MAX_SERIAL_LENGTH)
    val serialNumber: String?,
)

data class EquipmentResponse(
    val id: UUID,
    val equipmentType: EquipmentType,
    val label: String,
    val qrCode: String,
    val itemSize: String?,
    val serialNumber: String?,
    val status: EquipmentStatus,
    val createdAt: Instant,
    val retiredAt: Instant?,
)

data class AddServiceRecordRequest(
    val servicedOn: LocalDate,
    @field:NotBlank
    @field:Size(max = MAX_TEXT_LENGTH)
    val description: String,
    @field:Size(max = MAX_NAME_LENGTH)
    val performedBy: String?,
)

data class ServiceRecordResponse(
    val id: UUID,
    val equipmentId: UUID,
    val servicedOn: LocalDate,
    val description: String,
    val performedBy: String?,
    val createdAt: Instant,
)

data class RecordRentalRequest(
    @field:NotBlank
    @field:Size(max = MAX_NAME_LENGTH)
    val customerName: String,
    val rentedOn: LocalDate,
    @field:Size(max = MAX_TEXT_LENGTH)
    val notes: String?,
)

data class RecordRentalReturnRequest(
    val returnedOn: LocalDate,
)

data class RentalRecordResponse(
    val id: UUID,
    val equipmentId: UUID,
    val customerName: String,
    val rentedOn: LocalDate,
    val returnedOn: LocalDate?,
    val notes: String?,
    val createdAt: Instant,
)

data class EquipmentErrorResponse(
    val error: String,
)
