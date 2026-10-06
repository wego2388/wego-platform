package com.wego.toursoperator.application

import com.wego.toursoperator.domain.ConflictKind
import com.wego.toursoperator.domain.Driver
import com.wego.toursoperator.domain.SlotAssignment
import com.wego.toursoperator.domain.Supplier
import com.wego.toursoperator.domain.TimeSlot
import com.wego.toursoperator.domain.TourSlotId
import com.wego.toursoperator.domain.Vehicle
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Registries are never deleted; [update] only succeeds when the stored revision still equals [expectedRevision]. */
interface SupplierRepository {
    fun findById(id: UUID): Supplier?

    fun findByIds(ids: Collection<UUID>): List<Supplier>

    fun findAll(active: Boolean?): List<Supplier>

    fun codeTaken(
        code: String,
        exceptId: UUID?,
    ): Boolean

    fun insert(supplier: Supplier)

    /** Writes the supplier (revision becomes expectedRevision + 1) and replaces its tour links. False when the revision moved. */
    fun update(
        supplier: Supplier,
        expectedRevision: Int,
    ): Boolean
}

interface DriverRepository {
    fun findById(id: UUID): Driver?

    fun findAll(active: Boolean?): List<Driver>

    fun insert(driver: Driver)

    fun update(
        driver: Driver,
        expectedRevision: Int,
    ): Boolean
}

interface VehicleRepository {
    fun findById(id: UUID): Vehicle?

    fun findAll(active: Boolean?): List<Vehicle>

    fun plateTaken(
        plate: String,
        exceptId: UUID?,
    ): Boolean

    fun insert(vehicle: Vehicle)

    fun update(
        vehicle: Vehicle,
        expectedRevision: Int,
    ): Boolean
}

/** A departure on which a driver or vehicle is already used. */
data class ResourceUse(
    val slotId: UUID,
    val tourId: UUID,
    val timeSlot: TimeSlot,
)

enum class AssignmentAction { ASSIGNED, CHANGED, CLEARED }

data class AssignmentAuditEntry(
    val id: UUID,
    val slotId: UUID,
    val date: LocalDate,
    val timeSlot: TimeSlot,
    val action: AssignmentAction,
    val revision: Int,
    val driverId: UUID?,
    val vehicleId: UUID?,
    val supplierIds: Set<UUID>,
    val actorUserId: UUID?,
    val actorEmail: String?,
    val occurredAt: Instant,
)

interface SlotAssignmentRepository {
    /** Serialises concurrent assignments of one driver / vehicle for the rest of the transaction (taken in a fixed order). */
    fun lockResources(
        driverId: UUID?,
        vehicleId: UUID?,
    )

    fun findBySlot(slotId: TourSlotId): SlotAssignment?

    fun findByDate(date: LocalDate): List<SlotAssignment>

    fun insert(assignment: SlotAssignment)

    fun update(
        assignment: SlotAssignment,
        expectedRevision: Int,
    ): Boolean

    fun delete(slotId: TourSlotId)

    /** Other departures of [date] where [resourceId] is already assigned, any window. */
    fun usage(
        kind: ConflictKind,
        resourceId: UUID,
        date: LocalDate,
        excludeSlotId: UUID,
    ): List<ResourceUse>

    fun appendAudit(entry: AssignmentAuditEntry)

    fun history(slotId: TourSlotId): List<AssignmentAuditEntry>

    fun emailOf(userId: UUID): String?
}
