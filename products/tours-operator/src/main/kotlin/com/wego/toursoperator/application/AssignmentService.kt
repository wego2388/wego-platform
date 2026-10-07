package com.wego.toursoperator.application

import com.wego.toursoperator.domain.AssignmentConflict
import com.wego.toursoperator.domain.AssignmentIssue
import com.wego.toursoperator.domain.AssignmentRules
import com.wego.toursoperator.domain.ConflictKind
import com.wego.toursoperator.domain.Driver
import com.wego.toursoperator.domain.SlotAssignment
import com.wego.toursoperator.domain.Supplier
import com.wego.toursoperator.domain.SupplierServiceType
import com.wego.toursoperator.domain.TimeSlot
import com.wego.toursoperator.domain.TimeSlotOverlap
import com.wego.toursoperator.domain.TourSlot
import com.wego.toursoperator.domain.TourSlotId
import com.wego.toursoperator.domain.Vehicle
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class NamedRef(
    val id: UUID,
    val name: String,
)

data class VehicleRef(
    val id: UUID,
    val display: String,
    val seats: Int,
)

data class SupplierRef(
    val id: UUID,
    val code: String,
    val name: String,
)

/** Names only: phones stay with the supplier and fleet registries. */
data class AssignmentDetail(
    val revision: Int,
    val driver: NamedRef?,
    val vehicle: VehicleRef?,
    val suppliers: List<SupplierRef>,
    val assignedByEmail: String?,
    val assignedAt: Instant,
    val updatedByEmail: String?,
    val updatedAt: Instant,
)

data class AssignmentView(
    val slotId: UUID,
    val tourId: UUID,
    val tourNameEn: String,
    val date: LocalDate,
    val timeSlot: TimeSlot,
    val guests: Int,
    val slotBlocked: Boolean,
    val assignment: AssignmentDetail?,
    val issues: List<AssignmentIssue>,
    /** Active suppliers that serve this tour, to suggest when assigning. */
    val suggestedSuppliers: List<SupplierRef>,
)

data class AssignCommand(
    val slotId: TourSlotId,
    val driverId: UUID?,
    val vehicleId: UUID?,
    val supplierIds: Set<UUID>,
    val expectedRevision: Int,
    val actorUserId: UUID,
)

sealed class AssignResult {
    data class Saved(
        val view: AssignmentView,
    ) : AssignResult()

    data object NotFound : AssignResult()

    data class RevisionConflict(
        val currentRevision: Int,
    ) : AssignResult()

    /** A blocking rule failed (inactive driver, expired licence, unknown id, …); nothing was saved. */
    data class Refused(
        val code: String,
        val issues: List<AssignmentIssue>,
    ) : AssignResult()

    data class Conflict(
        val conflicts: List<AssignmentConflict>,
    ) : AssignResult()
}

data class DriverOption(
    val id: UUID,
    val name: String,
    val licenceValidUntil: LocalDate,
    val licenceCoversDate: Boolean,
)

data class SupplierOption(
    val id: UUID,
    val code: String,
    val name: String,
    val serviceType: SupplierServiceType,
    val tourIds: Set<UUID>,
)

data class AssignmentOptions(
    val drivers: List<DriverOption>,
    val vehicles: List<VehicleRef>,
    val suppliers: List<SupplierOption>,
)

/**
 * Daily assignment of a driver, optional vehicle and suppliers to a departure
 * (tour slot), WEGO-016-OPS2-E.
 *
 * Rules (see [com.wego.toursoperator.domain.TimeSlotOverlap]): the same driver
 * or vehicle on two departures of the same day and window is refused; adjacent
 * windows are only a warning. An inactive or licence-expired driver, or an
 * inactive vehicle or supplier, is refused. Seats below the booked guests is a
 * warning (guest counts keep changing). Each save echoes the assignment's
 * revision; a stale one is refused, and every change is audited with its actor.
 * Concurrent assignments are serialised by the slot row lock plus an advisory
 * lock per driver / vehicle (a partial unique index is the last backstop).
 */
class AssignmentService(
    private val slots: TourSlotRepository,
    private val tours: TourRepository,
    private val drivers: DriverRepository,
    private val vehicles: VehicleRepository,
    private val suppliers: SupplierRepository,
    private val assignments: SlotAssignmentRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    // The transaction runner cannot carry a null result, so single reads that may be absent run on the repositories' own read transactions.
    fun forSlot(slotId: TourSlotId): AssignmentView? {
        val slot = slots.findById(slotId) ?: return null
        return viewOf(slot, assignments.findBySlot(slotId))
    }

    fun forDay(date: LocalDate): List<AssignmentView> =
        transactionRunner.runInTransaction {
            val byslot = assignments.findByDate(date).associateBy { it.slotId }
            val activeSuppliers = suppliers.findAll(true)
            slots
                .findByDate(date)
                // Departures that matter: guests booked or something already assigned.
                .filter { it.bookedCount > 0 || it.id in byslot }
                .sortedWith(compareBy({ it.timeSlot.ordinal }, { it.tourId.value }))
                .map { viewOf(it, byslot[it.id], activeSuppliers = activeSuppliers) }
        }

    fun options(date: LocalDate): AssignmentOptions =
        AssignmentOptions(
            drivers = drivers.findAll(true).map { DriverOption(it.id, it.name, it.licenceValidUntil, it.licenceCovers(date)) },
            vehicles = vehicles.findAll(true).map { VehicleRef(it.id, it.display, it.seats) },
            suppliers = suppliers.findAll(true).map { SupplierOption(it.id, it.code, it.name, it.serviceType, it.tourIds) },
        )

    fun history(slotId: TourSlotId): List<AssignmentAuditEntry>? = if (slots.findById(slotId) == null) null else assignments.history(slotId)

    fun assign(command: AssignCommand): AssignResult =
        transactionRunner.runInTransaction {
            val slot = slots.findByIdForUpdate(command.slotId) ?: return@runInTransaction AssignResult.NotFound
            if (slot.date.isBefore(FxRateService.todayInSharm(clock))) {
                return@runInTransaction AssignResult.Refused("slot_in_past", emptyList())
            }
            val existing = assignments.findBySlot(slot.id)
            val currentRevision = existing?.revision ?: 0
            if (command.expectedRevision != currentRevision) return@runInTransaction AssignResult.RevisionConflict(currentRevision)
            require(command.driverId != null || command.vehicleId != null || command.supplierIds.isNotEmpty()) {
                "choose a driver, a vehicle or a supplier (use clear to remove an assignment)"
            }
            val driver = command.driverId?.let { drivers.findById(it) ?: return@runInTransaction notFound("driver_not_found") }
            val vehicle = command.vehicleId?.let { vehicles.findById(it) ?: return@runInTransaction notFound("vehicle_not_found") }
            val chosen = suppliers.findByIds(command.supplierIds)
            if (chosen.size != command.supplierIds.size) return@runInTransaction notFound("supplier_not_found")

            val issues = AssignmentRules.evaluate(slot.date, slot.bookedCount, slot.tourId.value, driver, vehicle, chosen)
            val blocking = issues.filter { it.blocking }
            if (blocking.isNotEmpty()) return@runInTransaction AssignResult.Refused(blocking.first().code, issues)

            assignments.lockResources(command.driverId, command.vehicleId)
            val conflicts = mutableListOf<AssignmentConflict>()
            val backToBack = mutableListOf<AssignmentIssue>()
            collect(ConflictKind.DRIVER, command.driverId, slot, conflicts, backToBack)
            collect(ConflictKind.VEHICLE, command.vehicleId, slot, conflicts, backToBack)
            if (conflicts.isNotEmpty()) return@runInTransaction AssignResult.Conflict(conflicts)

            val now = Instant.now(clock)
            val next =
                SlotAssignment(
                    slotId = slot.id,
                    date = slot.date,
                    timeSlot = slot.timeSlot,
                    driverId = command.driverId,
                    vehicleId = command.vehicleId,
                    supplierIds = command.supplierIds,
                    revision = currentRevision + 1,
                    assignedAt = existing?.assignedAt ?: now,
                    assignedByUserId = existing?.assignedByUserId ?: command.actorUserId,
                    updatedAt = now,
                    updatedByUserId = command.actorUserId,
                )
            if (existing == null) {
                assignments.insert(next)
            } else if (!assignments.update(next, currentRevision)) {
                return@runInTransaction AssignResult.RevisionConflict(assignments.findBySlot(slot.id)?.revision ?: currentRevision)
            }
            audit(next, if (existing == null) AssignmentAction.ASSIGNED else AssignmentAction.CHANGED, command.actorUserId, now)
            AssignResult.Saved(viewOf(slot, next, extra = backToBack))
        }

    fun clear(
        slotId: TourSlotId,
        expectedRevision: Int,
        actorUserId: UUID,
    ): AssignResult =
        transactionRunner.runInTransaction {
            val slot = slots.findByIdForUpdate(slotId) ?: return@runInTransaction AssignResult.NotFound
            if (slot.date.isBefore(
                    FxRateService.todayInSharm(clock),
                )
            ) {
                return@runInTransaction AssignResult.Refused("slot_in_past", emptyList())
            }
            val existing = assignments.findBySlot(slotId) ?: return@runInTransaction AssignResult.NotFound
            if (existing.revision != expectedRevision) return@runInTransaction AssignResult.RevisionConflict(existing.revision)
            assignments.delete(slotId)
            audit(existing.copy(revision = existing.revision + 1), AssignmentAction.CLEARED, actorUserId, Instant.now(clock))
            AssignResult.Saved(viewOf(slot, null))
        }

    /** Used by the documents: the assignment with its names, or null when the departure has none. */
    fun resolved(slotId: TourSlotId): ResolvedAssignment? {
        val assignment = assignments.findBySlot(slotId) ?: return null
        return ResolvedAssignment(
            assignment,
            assignment.driverId?.let { drivers.findById(it) },
            assignment.vehicleId?.let { vehicles.findById(it) },
            suppliers.findByIds(assignment.supplierIds).sortedBy { it.name.lowercase() },
        )
    }

    private fun notFound(code: String) = AssignResult.Refused(code, emptyList())

    private fun collect(
        kind: ConflictKind,
        resourceId: UUID?,
        slot: TourSlot,
        conflicts: MutableList<AssignmentConflict>,
        backToBack: MutableList<AssignmentIssue>,
    ) {
        if (resourceId == null) return
        for (use in assignments.usage(kind, resourceId, slot.date, slot.id.value)) {
            when {
                TimeSlotOverlap.overlaps(slot.timeSlot, use.timeSlot) ->
                    conflicts += AssignmentConflict(kind, resourceId, use.slotId, use.tourId, use.timeSlot)
                TimeSlotOverlap.backToBack(slot.timeSlot, use.timeSlot) ->
                    backToBack +=
                        AssignmentIssue(if (kind == ConflictKind.DRIVER) "driver_back_to_back" else "vehicle_back_to_back", resourceId)
            }
        }
    }

    private fun audit(
        assignment: SlotAssignment,
        action: AssignmentAction,
        actor: UUID,
        at: Instant,
    ) = assignments.appendAudit(
        AssignmentAuditEntry(
            id = UUID.randomUUID(),
            slotId = assignment.slotId.value,
            date = assignment.date,
            timeSlot = assignment.timeSlot,
            action = action,
            revision = assignment.revision,
            driverId = assignment.driverId,
            vehicleId = assignment.vehicleId,
            supplierIds = assignment.supplierIds,
            actorUserId = actor,
            actorEmail = null,
            occurredAt = at,
        ),
    )

    private fun viewOf(
        slot: TourSlot,
        assignment: SlotAssignment?,
        extra: List<AssignmentIssue> = emptyList(),
        activeSuppliers: List<Supplier> = suppliers.findAll(true),
    ): AssignmentView {
        val tour = tours.findById(slot.tourId)
        val driver = assignment?.driverId?.let { drivers.findById(it) }
        val vehicle = assignment?.vehicleId?.let { vehicles.findById(it) }
        val chosen = assignment?.let { suppliers.findByIds(it.supplierIds) }.orEmpty().sortedBy { it.name.lowercase() }
        val issues =
            if (assignment == null) {
                emptyList()
            } else {
                AssignmentRules.evaluate(slot.date, slot.bookedCount, slot.tourId.value, driver, vehicle, chosen)
            }
        val suggested =
            activeSuppliers
                .filter { slot.tourId.value in it.tourIds }
                .map { SupplierRef(it.id, it.code, it.name) }
        return AssignmentView(
            slotId = slot.id.value,
            tourId = slot.tourId.value,
            tourNameEn = tour?.nameEn ?: tour?.slug ?: slot.tourId.value.toString(),
            date = slot.date,
            timeSlot = slot.timeSlot,
            guests = slot.bookedCount,
            slotBlocked = slot.isBlocked,
            assignment =
                assignment?.let {
                    AssignmentDetail(
                        revision = it.revision,
                        driver = driver?.let { d -> NamedRef(d.id, d.name) },
                        vehicle = vehicle?.let { v -> VehicleRef(v.id, v.display, v.seats) },
                        suppliers = chosen.map { s -> SupplierRef(s.id, s.code, s.name) },
                        assignedByEmail = it.assignedByUserId?.let(assignments::emailOf),
                        assignedAt = it.assignedAt,
                        updatedByEmail = it.updatedByUserId?.let(assignments::emailOf),
                        updatedAt = it.updatedAt,
                    )
                },
            issues = issues + extra,
            suggestedSuppliers = suggested,
        )
    }
}

class ResolvedAssignment(
    val assignment: SlotAssignment,
    val driver: Driver?,
    val vehicle: Vehicle?,
    val suppliers: List<Supplier>,
)
