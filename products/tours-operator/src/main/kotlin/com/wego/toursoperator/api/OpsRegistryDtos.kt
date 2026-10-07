package com.wego.toursoperator.api

import com.wego.toursoperator.application.AssignmentAuditEntry
import com.wego.toursoperator.application.AssignmentDetail
import com.wego.toursoperator.application.AssignmentOptions
import com.wego.toursoperator.application.AssignmentView
import com.wego.toursoperator.application.DriverInput
import com.wego.toursoperator.application.SupplierInput
import com.wego.toursoperator.application.VehicleInput
import com.wego.toursoperator.domain.AssignmentConflict
import com.wego.toursoperator.domain.AssignmentIssue
import com.wego.toursoperator.domain.ConfirmationChannel
import com.wego.toursoperator.domain.ConflictKind
import com.wego.toursoperator.domain.Driver
import com.wego.toursoperator.domain.DriverEngagement
import com.wego.toursoperator.domain.PaidCurrency
import com.wego.toursoperator.domain.SettlementCadence
import com.wego.toursoperator.domain.Supplier
import com.wego.toursoperator.domain.SupplierPaymentMethod
import com.wego.toursoperator.domain.SupplierPricingBasis
import com.wego.toursoperator.domain.SupplierServiceType
import com.wego.toursoperator.domain.TimeSlot
import com.wego.toursoperator.domain.Vehicle
import com.wego.toursoperator.domain.VehicleOwnership
import com.wego.toursoperator.domain.VehicleType
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

// ── requests ────────────────────────────────────────────────────────────────

data class SupplierRequest(
    @field:NotBlank @field:Size(max = 24) val code: String,
    @field:NotBlank @field:Size(max = 120) val name: String,
    val serviceType: SupplierServiceType,
    @field:Size(max = 120) val contactPerson: String? = null,
    @field:Size(max = 32) val businessPhone: String? = null,
    val confirmationChannel: ConfirmationChannel? = null,
    val noticeHours: Int? = null,
    val pricingBasis: SupplierPricingBasis? = null,
    val currency: PaidCurrency? = null,
    val settlementCadence: SettlementCadence? = null,
    val paymentMethod: SupplierPaymentMethod? = null,
    @field:Size(max = 1000) val cancellationTerms: String? = null,
    val active: Boolean? = null,
    @field:Size(max = 200) val tourIds: Set<UUID>? = null,
    /** Required when updating: the revision the caller read. Ignored on create. */
    @field:Min(1) val expectedRevision: Int? = null,
) {
    fun toInput() =
        SupplierInput(
            code,
            name,
            serviceType,
            contactPerson,
            businessPhone,
            confirmationChannel,
            noticeHours,
            pricingBasis,
            currency,
            settlementCadence,
            paymentMethod,
            cancellationTerms,
            active ?: true,
            tourIds.orEmpty(),
        )
}

data class DriverRequest(
    @field:NotBlank @field:Size(max = 120) val name: String,
    @field:Size(max = 32) val workPhone: String? = null,
    val engagementType: DriverEngagement,
    val licenceValidUntil: LocalDate,
    val active: Boolean? = null,
    @field:Min(1) val expectedRevision: Int? = null,
) {
    fun toInput() = DriverInput(name, workPhone, engagementType, licenceValidUntil, active ?: true)
}

data class VehicleRequest(
    @field:Size(max = 80) val label: String? = null,
    @field:Size(max = 24) val plate: String? = null,
    val vehicleType: VehicleType,
    val seats: Int,
    val ownership: VehicleOwnership,
    val hiredFromSupplierId: UUID? = null,
    val active: Boolean? = null,
    @field:Min(1) val expectedRevision: Int? = null,
) {
    fun toInput() = VehicleInput(label, plate, vehicleType, seats, ownership, hiredFromSupplierId, active ?: true)
}

data class AssignmentRequest(
    val driverId: UUID? = null,
    val vehicleId: UUID? = null,
    @field:Size(max = 20) val supplierIds: Set<UUID>? = null,
    /** Staff-written note printed on the supplier orders. Do not include customer phone numbers or health details. */
    @field:Size(max = 500) val supplierNote: String? = null,
    /** 0 when the departure has no assignment yet; otherwise the revision read. */
    @field:Min(0) val expectedRevision: Int,
)

// ── responses ───────────────────────────────────────────────────────────────

data class SupplierResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val serviceType: SupplierServiceType,
    val contactPerson: String?,
    val businessPhone: String?,
    val confirmationChannel: ConfirmationChannel?,
    val noticeHours: Int?,
    val pricingBasis: SupplierPricingBasis?,
    val currency: PaidCurrency?,
    val settlementCadence: SettlementCadence?,
    val paymentMethod: SupplierPaymentMethod?,
    val cancellationTerms: String?,
    val active: Boolean,
    val tourIds: List<UUID>,
    val revision: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class DriverResponse(
    val id: UUID,
    val name: String,
    val workPhone: String?,
    val engagementType: DriverEngagement,
    val licenceValidUntil: LocalDate,
    val active: Boolean,
    val revision: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class VehicleResponse(
    val id: UUID,
    val label: String?,
    val plate: String?,
    val display: String,
    val vehicleType: VehicleType,
    val seats: Int,
    val ownership: VehicleOwnership,
    val hiredFromSupplierId: UUID?,
    val active: Boolean,
    val revision: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

fun Supplier.toResponse() =
    SupplierResponse(
        id,
        code,
        name,
        serviceType,
        contactPerson,
        businessPhone,
        confirmationChannel,
        noticeHours,
        pricingBasis,
        currency,
        settlementCadence,
        paymentMethod,
        cancellationTerms,
        active,
        tourIds.sorted(),
        audit.revision,
        audit.createdAt,
        audit.updatedAt,
    )

fun Driver.toResponse() =
    DriverResponse(id, name, workPhone, engagement, licenceValidUntil, active, audit.revision, audit.createdAt, audit.updatedAt)

fun Vehicle.toResponse() =
    VehicleResponse(
        id,
        label,
        plate,
        display,
        type,
        seats,
        ownership,
        hiredFromSupplierId,
        active,
        audit.revision,
        audit.createdAt,
        audit.updatedAt,
    )

data class AssignmentIssueResponse(
    val code: String,
    val resourceId: UUID?,
    val value: Int?,
    val limit: Int?,
    val blocking: Boolean,
)

data class AssignmentConflictResponse(
    val kind: ConflictKind,
    val resourceId: UUID,
    val otherSlotId: UUID,
    val otherTourId: UUID,
    val otherTimeSlot: TimeSlot,
)

data class AssignmentConflictBody(
    val error: String,
    val conflicts: List<AssignmentConflictResponse>,
)

data class AssignmentRefusalBody(
    val error: String,
    val issues: List<AssignmentIssueResponse>,
)

data class RevisionConflictBody(
    val error: String = "revision_conflict",
    val currentRevision: Int,
)

data class NamedRefResponse(
    val id: UUID,
    val name: String,
)

data class VehicleRefResponse(
    val id: UUID,
    val display: String,
    val seats: Int,
)

data class SupplierRefResponse(
    val id: UUID,
    val code: String,
    val name: String,
)

data class AssignmentDetailResponse(
    val revision: Int,
    val driver: NamedRefResponse?,
    val vehicle: VehicleRefResponse?,
    val suppliers: List<SupplierRefResponse>,
    val supplierNote: String?,
    val assignedByEmail: String?,
    val assignedAt: Instant,
    val updatedByEmail: String?,
    val updatedAt: Instant,
)

data class AssignmentViewResponse(
    val slotId: UUID,
    val tourId: UUID,
    val tourNameEn: String,
    val date: LocalDate,
    val timeSlot: TimeSlot,
    val guests: Int,
    val slotBlocked: Boolean,
    val assignment: AssignmentDetailResponse?,
    val issues: List<AssignmentIssueResponse>,
    val suggestedSuppliers: List<SupplierRefResponse>,
)

data class DriverOptionResponse(
    val id: UUID,
    val name: String,
    val licenceValidUntil: LocalDate,
    val licenceCoversDate: Boolean,
)

data class SupplierOptionResponse(
    val id: UUID,
    val code: String,
    val name: String,
    val serviceType: SupplierServiceType,
    val tourIds: List<UUID>,
)

data class AssignmentOptionsResponse(
    val drivers: List<DriverOptionResponse>,
    val vehicles: List<VehicleRefResponse>,
    val suppliers: List<SupplierOptionResponse>,
)

data class AssignmentAuditResponse(
    val action: String,
    val revision: Int,
    val driverId: UUID?,
    val vehicleId: UUID?,
    val supplierIds: List<UUID>,
    val supplierNote: String?,
    val actorEmail: String?,
    val occurredAt: Instant,
)

fun AssignmentIssue.toResponse() = AssignmentIssueResponse(code, resourceId, value, limit, blocking)

fun AssignmentConflict.toResponse() = AssignmentConflictResponse(kind, resourceId, otherSlotId, otherTourId, otherTimeSlot)

private fun AssignmentDetail.toResponse() =
    AssignmentDetailResponse(
        revision,
        driver?.let { NamedRefResponse(it.id, it.name) },
        vehicle?.let { VehicleRefResponse(it.id, it.display, it.seats) },
        suppliers.map { SupplierRefResponse(it.id, it.code, it.name) },
        supplierNote,
        assignedByEmail,
        assignedAt,
        updatedByEmail,
        updatedAt,
    )

fun AssignmentView.toResponse() =
    AssignmentViewResponse(
        slotId,
        tourId,
        tourNameEn,
        date,
        timeSlot,
        guests,
        slotBlocked,
        assignment?.toResponse(),
        issues.map { it.toResponse() },
        suggestedSuppliers.map { SupplierRefResponse(it.id, it.code, it.name) },
    )

fun AssignmentOptions.toResponse() =
    AssignmentOptionsResponse(
        drivers.map { DriverOptionResponse(it.id, it.name, it.licenceValidUntil, it.licenceCoversDate) },
        vehicles.map { VehicleRefResponse(it.id, it.display, it.seats) },
        suppliers.map { SupplierOptionResponse(it.id, it.code, it.name, it.serviceType, it.tourIds.sorted()) },
    )

fun AssignmentAuditEntry.toResponse() =
    AssignmentAuditResponse(action.name, revision, driverId, vehicleId, supplierIds.sorted(), supplierNote, actorEmail, occurredAt)
