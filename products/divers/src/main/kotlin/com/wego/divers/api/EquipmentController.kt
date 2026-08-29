package com.wego.divers.api

import com.wego.divers.application.AddServiceRecordCommand
import com.wego.divers.application.AddServiceRecordResult
import com.wego.divers.application.AddServiceRecordService
import com.wego.divers.application.CompleteMaintenanceResult
import com.wego.divers.application.CompleteMaintenanceService
import com.wego.divers.application.CreateEquipmentCommand
import com.wego.divers.application.CreateEquipmentResult
import com.wego.divers.application.CreateEquipmentService
import com.wego.divers.application.EquipmentQueryService
import com.wego.divers.application.RecordRentalCommand
import com.wego.divers.application.RecordRentalResult
import com.wego.divers.application.RecordRentalReturnResult
import com.wego.divers.application.RecordRentalReturnService
import com.wego.divers.application.RecordRentalService
import com.wego.divers.application.RetireEquipmentResult
import com.wego.divers.application.RetireEquipmentService
import com.wego.divers.application.StartMaintenanceResult
import com.wego.divers.application.StartMaintenanceService
import com.wego.divers.application.UpdateEquipmentCommand
import com.wego.divers.application.UpdateEquipmentResult
import com.wego.divers.application.UpdateEquipmentService
import com.wego.divers.domain.Equipment
import com.wego.divers.domain.EquipmentId
import com.wego.divers.domain.EquipmentRentalRecord
import com.wego.divers.domain.EquipmentServiceRecord
import com.wego.divers.domain.EquipmentStatus
import com.wego.divers.domain.EquipmentType
import com.wego.events.CorrelationContext
import com.wego.identity.AuthenticatedUser
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Validated
@RestController
@RequestMapping("/api/v1/divers/equipment")
class EquipmentController(
    private val createEquipmentService: CreateEquipmentService,
    private val updateEquipmentService: UpdateEquipmentService,
    private val startMaintenanceService: StartMaintenanceService,
    private val completeMaintenanceService: CompleteMaintenanceService,
    private val retireEquipmentService: RetireEquipmentService,
    private val addServiceRecordService: AddServiceRecordService,
    private val recordRentalService: RecordRentalService,
    private val recordRentalReturnService: RecordRentalReturnService,
    private val equipmentQueryService: EquipmentQueryService,
) {
    @PostMapping
    @PreAuthorize("hasAuthority('equipment:manage')")
    fun create(
        @Valid @RequestBody request: CreateEquipmentRequest,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result =
                createEquipmentService.create(
                    CreateEquipmentCommand(
                        equipmentType = request.equipmentType,
                        label = request.label,
                        qrCode = request.qrCode,
                        itemSize = request.itemSize,
                        serialNumber = request.serialNumber,
                        createdByUserId = actorUserId,
                        correlationId = CorrelationContext.currentCorrelationId(),
                    ),
                )
        ) {
            is CreateEquipmentResult.Created -> ResponseEntity.status(HttpStatus.CREATED).body(result.equipment.toResponse())
            CreateEquipmentResult.DuplicateQrCode ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(EquipmentErrorResponse("duplicate_qr_code"))
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('equipment:view')")
    fun list(
        @RequestParam(required = false) type: EquipmentType?,
        @RequestParam(required = false) status: EquipmentStatus?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) qrCode: String?,
        @RequestParam(required = false, defaultValue = "0") @Min(0) page: Int,
        @RequestParam(required = false, defaultValue = "50") @Min(1) @Max(200) size: Int,
    ): List<EquipmentResponse> {
        // qrCode is an exact, unique-by-database-constraint match — a
        // scanned code resolves to at most one item, so this bypasses
        // pagination/other filters entirely rather than treating a QR scan
        // as a fuzzy multi-result search.
        if (qrCode != null) {
            return listOfNotNull(equipmentQueryService.findByQrCode(qrCode)).map { it.toResponse() }
        }
        return equipmentQueryService.list(type, status, search, page, size).map { it.toResponse() }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('equipment:view')")
    fun findById(
        @PathVariable id: UUID,
    ): ResponseEntity<EquipmentResponse> {
        val equipment = equipmentQueryService.findById(EquipmentId(id)) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(equipment.toResponse())
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('equipment:manage')")
    fun update(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateEquipmentRequest,
    ): ResponseEntity<Any> =
        when (
            val result =
                updateEquipmentService.update(
                    UpdateEquipmentCommand(EquipmentId(id), request.label, request.itemSize, request.serialNumber),
                )
        ) {
            is UpdateEquipmentResult.Updated -> ResponseEntity.ok(result.equipment.toResponse())
            UpdateEquipmentResult.NotFound -> ResponseEntity.notFound().build()
        }

    @PostMapping("/{id}/start-maintenance")
    @PreAuthorize("hasAuthority('equipment:manage')")
    fun startMaintenance(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (val result = startMaintenanceService.start(EquipmentId(id), actorUserId, CorrelationContext.currentCorrelationId())) {
            is StartMaintenanceResult.Started -> ResponseEntity.ok(result.equipment.toResponse())
            StartMaintenanceResult.NotFound -> ResponseEntity.notFound().build()
            StartMaintenanceResult.NotActive -> ResponseEntity.status(HttpStatus.CONFLICT).body(EquipmentErrorResponse("not_active"))
        }
    }

    @PostMapping("/{id}/complete-maintenance")
    @PreAuthorize("hasAuthority('equipment:manage')")
    fun completeMaintenance(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (
            val result = completeMaintenanceService.complete(EquipmentId(id), actorUserId, CorrelationContext.currentCorrelationId())
        ) {
            is CompleteMaintenanceResult.Completed -> ResponseEntity.ok(result.equipment.toResponse())
            CompleteMaintenanceResult.NotFound -> ResponseEntity.notFound().build()
            CompleteMaintenanceResult.NotInMaintenance ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(EquipmentErrorResponse("not_in_maintenance"))
        }
    }

    @PostMapping("/{id}/retire")
    @PreAuthorize("hasAuthority('equipment:manage')")
    fun retire(
        @PathVariable id: UUID,
        authentication: Authentication,
    ): ResponseEntity<Any> {
        val actorUserId = (authentication.principal as AuthenticatedUser).userId
        return when (val result = retireEquipmentService.retire(EquipmentId(id), actorUserId, CorrelationContext.currentCorrelationId())) {
            is RetireEquipmentResult.Retired -> ResponseEntity.ok(result.equipment.toResponse())
            RetireEquipmentResult.NotFound -> ResponseEntity.notFound().build()
            RetireEquipmentResult.AlreadyRetired ->
                ResponseEntity
                    .status(
                        HttpStatus.CONFLICT,
                    ).body(EquipmentErrorResponse("already_retired"))
            RetireEquipmentResult.HasOpenRental ->
                ResponseEntity
                    .status(
                        HttpStatus.CONFLICT,
                    ).body(EquipmentErrorResponse("has_open_rental"))
        }
    }

    @GetMapping("/{id}/service-records")
    @PreAuthorize("hasAuthority('equipment:view')")
    fun listServiceRecords(
        @PathVariable id: UUID,
        @RequestParam(required = false, defaultValue = "0") @Min(0) page: Int,
        @RequestParam(required = false, defaultValue = "50") @Min(1) @Max(200) size: Int,
    ): List<ServiceRecordResponse> = equipmentQueryService.listServiceRecords(EquipmentId(id), page, size).map { it.toResponse() }

    @PostMapping("/{id}/service-records")
    @PreAuthorize("hasAuthority('equipment:manage')")
    fun addServiceRecord(
        @PathVariable id: UUID,
        @Valid @RequestBody request: AddServiceRecordRequest,
    ): ResponseEntity<Any> =
        when (
            val result =
                addServiceRecordService.add(
                    AddServiceRecordCommand(EquipmentId(id), request.servicedOn, request.description, request.performedBy),
                )
        ) {
            is AddServiceRecordResult.Added -> ResponseEntity.status(HttpStatus.CREATED).body(result.record.toResponse())
            AddServiceRecordResult.EquipmentNotFound -> ResponseEntity.notFound().build()
        }

    @GetMapping("/{id}/rentals")
    @PreAuthorize("hasAuthority('equipment:view')")
    fun listRentals(
        @PathVariable id: UUID,
        @RequestParam(required = false, defaultValue = "0") @Min(0) page: Int,
        @RequestParam(required = false, defaultValue = "50") @Min(1) @Max(200) size: Int,
    ): List<RentalRecordResponse> = equipmentQueryService.listRentalRecords(EquipmentId(id), page, size).map { it.toResponse() }

    @PostMapping("/{id}/rentals")
    @PreAuthorize("hasAuthority('equipment:manage')")
    fun recordRental(
        @PathVariable id: UUID,
        @Valid @RequestBody request: RecordRentalRequest,
    ): ResponseEntity<Any> =
        when (
            val result =
                recordRentalService.record(RecordRentalCommand(EquipmentId(id), request.customerName, request.rentedOn, request.notes))
        ) {
            is RecordRentalResult.Recorded -> ResponseEntity.status(HttpStatus.CREATED).body(result.record.toResponse())
            RecordRentalResult.EquipmentNotFound -> ResponseEntity.notFound().build()
            RecordRentalResult.EquipmentNotAvailable ->
                ResponseEntity.status(HttpStatus.CONFLICT).body(EquipmentErrorResponse("equipment_not_available"))
            RecordRentalResult.AlreadyOut -> ResponseEntity.status(HttpStatus.CONFLICT).body(EquipmentErrorResponse("already_out"))
        }

    @PostMapping("/{id}/rentals/return")
    @PreAuthorize("hasAuthority('equipment:manage')")
    fun recordRentalReturn(
        @PathVariable id: UUID,
        @Valid @RequestBody request: RecordRentalReturnRequest,
    ): ResponseEntity<Any> =
        when (val result = recordRentalReturnService.returnItem(EquipmentId(id), request.returnedOn)) {
            is RecordRentalReturnResult.Returned -> ResponseEntity.ok(result.record.toResponse())
            RecordRentalReturnResult.NoOpenRental ->
                ResponseEntity
                    .status(
                        HttpStatus.CONFLICT,
                    ).body(EquipmentErrorResponse("no_open_rental"))
        }
}

private fun Equipment.toResponse() =
    EquipmentResponse(
        id = id.value,
        equipmentType = equipmentType,
        label = label,
        qrCode = qrCode,
        itemSize = itemSize,
        serialNumber = serialNumber,
        status = status,
        createdAt = createdAt,
        retiredAt = retiredAt,
    )

private fun EquipmentServiceRecord.toResponse() =
    ServiceRecordResponse(
        id = id,
        equipmentId = equipmentId.value,
        servicedOn = servicedOn,
        description = description,
        performedBy = performedBy,
        createdAt = createdAt,
    )

private fun EquipmentRentalRecord.toResponse() =
    RentalRecordResponse(
        id = id,
        equipmentId = equipmentId.value,
        customerName = customerName,
        rentedOn = rentedOn,
        returnedOn = returnedOn,
        notes = notes,
        createdAt = createdAt,
    )
