package com.wego.toursoperator.application

import com.wego.toursoperator.domain.ConfirmationChannel
import com.wego.toursoperator.domain.Driver
import com.wego.toursoperator.domain.DriverEngagement
import com.wego.toursoperator.domain.OpsRules
import com.wego.toursoperator.domain.PaidCurrency
import com.wego.toursoperator.domain.RegistryAudit
import com.wego.toursoperator.domain.SettlementCadence
import com.wego.toursoperator.domain.Supplier
import com.wego.toursoperator.domain.SupplierPaymentMethod
import com.wego.toursoperator.domain.SupplierPricingBasis
import com.wego.toursoperator.domain.SupplierServiceType
import com.wego.toursoperator.domain.TourId
import com.wego.toursoperator.domain.Vehicle
import com.wego.toursoperator.domain.VehicleOwnership
import com.wego.toursoperator.domain.VehicleType
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

sealed class RegistryResult<out T> {
    data class Ok<T>(
        val value: T,
    ) : RegistryResult<T>()

    data object NotFound : RegistryResult<Nothing>()

    /** Someone saved a newer revision first; [currentRevision] is what the caller must re-read. */
    data class RevisionConflict(
        val currentRevision: Int,
    ) : RegistryResult<Nothing>()

    data class Refused(
        val code: String,
    ) : RegistryResult<Nothing>()
}

data class SupplierInput(
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
    val tourIds: Set<UUID>,
)

data class DriverInput(
    val name: String,
    val workPhone: String?,
    val engagement: DriverEngagement,
    val licenceValidUntil: LocalDate,
    val active: Boolean,
)

data class VehicleInput(
    val label: String?,
    val plate: String?,
    val type: VehicleType,
    val seats: Int,
    val ownership: VehicleOwnership,
    val hiredFromSupplierId: UUID?,
    val active: Boolean,
)

/**
 * Supplier, driver and vehicle registries (WEGO-016-OPS2-E). Records are never
 * deleted — deactivate instead — and every write echoes the revision it read.
 * Domain rule violations surface as IllegalArgumentException (HTTP 400).
 */
class OpsRegistryService(
    private val suppliers: SupplierRepository,
    private val drivers: DriverRepository,
    private val vehicles: VehicleRepository,
    private val tours: TourRepository,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
) {
    // ── suppliers ────────────────────────────────────────────────────────────

    fun listSuppliers(active: Boolean?): List<Supplier> = suppliers.findAll(active)

    fun supplier(id: UUID): Supplier? = suppliers.findById(id)

    fun createSupplier(
        input: SupplierInput,
        actor: UUID,
    ): RegistryResult<Supplier> =
        transactionRunner.runInTransaction {
            val code = input.code.trim().uppercase()
            if (suppliers.codeTaken(code, null)) return@runInTransaction RegistryResult.Refused("supplier_code_taken")
            unknownTour(input.tourIds)?.let { return@runInTransaction RegistryResult.Refused(it) }
            val now = Instant.now(clock)
            val supplier = buildSupplier(UUID.randomUUID(), code, input, RegistryAudit(1, now, actor, now, actor))
            suppliers.insert(supplier)
            RegistryResult.Ok(supplier)
        }

    fun updateSupplier(
        id: UUID,
        input: SupplierInput,
        expectedRevision: Int,
        actor: UUID,
    ): RegistryResult<Supplier> =
        transactionRunner.runInTransaction {
            val current = suppliers.findById(id) ?: return@runInTransaction RegistryResult.NotFound
            if (current.audit.revision != expectedRevision) return@runInTransaction RegistryResult.RevisionConflict(current.audit.revision)
            val code = input.code.trim().uppercase()
            if (suppliers.codeTaken(code, id)) return@runInTransaction RegistryResult.Refused("supplier_code_taken")
            unknownTour(input.tourIds)?.let { return@runInTransaction RegistryResult.Refused(it) }
            val audit = current.audit.copy(revision = expectedRevision + 1, updatedAt = Instant.now(clock), updatedByUserId = actor)
            val next = buildSupplier(id, code, input, audit)
            if (!suppliers.update(next, expectedRevision)) {
                return@runInTransaction RegistryResult.RevisionConflict(suppliers.findById(id)?.audit?.revision ?: expectedRevision)
            }
            RegistryResult.Ok(next)
        }

    private fun buildSupplier(
        id: UUID,
        code: String,
        i: SupplierInput,
        audit: RegistryAudit,
    ) = Supplier(
        id = id,
        code = code,
        name = i.name.trim(),
        serviceType = i.serviceType,
        contactPerson = OpsRules.clean(i.contactPerson),
        businessPhone = OpsRules.clean(i.businessPhone),
        confirmationChannel = i.confirmationChannel,
        noticeHours = i.noticeHours,
        pricingBasis = i.pricingBasis,
        currency = i.currency,
        settlementCadence = i.settlementCadence,
        paymentMethod = i.paymentMethod,
        cancellationTerms = OpsRules.clean(i.cancellationTerms),
        active = i.active,
        tourIds = i.tourIds,
        audit = audit,
    )

    private fun unknownTour(ids: Set<UUID>): String? = if (ids.any { tours.findById(TourId(it)) == null }) "tour_not_found" else null

    // ── drivers ──────────────────────────────────────────────────────────────

    fun listDrivers(active: Boolean?): List<Driver> = drivers.findAll(active)

    fun driver(id: UUID): Driver? = drivers.findById(id)

    fun createDriver(
        input: DriverInput,
        actor: UUID,
    ): RegistryResult<Driver> =
        transactionRunner.runInTransaction {
            val now = Instant.now(clock)
            val driver = buildDriver(UUID.randomUUID(), input, RegistryAudit(1, now, actor, now, actor))
            drivers.insert(driver)
            RegistryResult.Ok(driver)
        }

    fun updateDriver(
        id: UUID,
        input: DriverInput,
        expectedRevision: Int,
        actor: UUID,
    ): RegistryResult<Driver> =
        transactionRunner.runInTransaction {
            val current = drivers.findById(id) ?: return@runInTransaction RegistryResult.NotFound
            if (current.audit.revision != expectedRevision) return@runInTransaction RegistryResult.RevisionConflict(current.audit.revision)
            val next =
                buildDriver(
                    id,
                    input,
                    current.audit.copy(revision = expectedRevision + 1, updatedAt = Instant.now(clock), updatedByUserId = actor),
                )
            if (!drivers.update(next, expectedRevision)) {
                return@runInTransaction RegistryResult.RevisionConflict(drivers.findById(id)?.audit?.revision ?: expectedRevision)
            }
            RegistryResult.Ok(next)
        }

    private fun buildDriver(
        id: UUID,
        i: DriverInput,
        audit: RegistryAudit,
    ) = Driver(id, i.name.trim(), OpsRules.clean(i.workPhone), i.engagement, i.licenceValidUntil, i.active, audit)

    // ── vehicles ─────────────────────────────────────────────────────────────

    fun listVehicles(active: Boolean?): List<Vehicle> = vehicles.findAll(active)

    fun vehicle(id: UUID): Vehicle? = vehicles.findById(id)

    fun createVehicle(
        input: VehicleInput,
        actor: UUID,
    ): RegistryResult<Vehicle> =
        transactionRunner.runInTransaction {
            val now = Instant.now(clock)
            val vehicle = buildVehicle(UUID.randomUUID(), input, RegistryAudit(1, now, actor, now, actor))
            checkVehicle(vehicle, null)?.let { return@runInTransaction RegistryResult.Refused(it) }
            vehicles.insert(vehicle)
            RegistryResult.Ok(vehicle)
        }

    fun updateVehicle(
        id: UUID,
        input: VehicleInput,
        expectedRevision: Int,
        actor: UUID,
    ): RegistryResult<Vehicle> =
        transactionRunner.runInTransaction {
            val current = vehicles.findById(id) ?: return@runInTransaction RegistryResult.NotFound
            if (current.audit.revision != expectedRevision) return@runInTransaction RegistryResult.RevisionConflict(current.audit.revision)
            val next =
                buildVehicle(
                    id,
                    input,
                    current.audit.copy(revision = expectedRevision + 1, updatedAt = Instant.now(clock), updatedByUserId = actor),
                )
            checkVehicle(next, id)?.let { return@runInTransaction RegistryResult.Refused(it) }
            if (!vehicles.update(next, expectedRevision)) {
                return@runInTransaction RegistryResult.RevisionConflict(vehicles.findById(id)?.audit?.revision ?: expectedRevision)
            }
            RegistryResult.Ok(next)
        }

    private fun buildVehicle(
        id: UUID,
        i: VehicleInput,
        audit: RegistryAudit,
    ) = Vehicle(
        id,
        OpsRules.clean(i.label),
        OpsRules.clean(i.plate),
        i.type,
        i.seats,
        i.ownership,
        i.hiredFromSupplierId,
        i.active,
        audit,
    )

    private fun checkVehicle(
        vehicle: Vehicle,
        exceptId: UUID?,
    ): String? {
        if (vehicle.plate != null && vehicles.plateTaken(vehicle.plate, exceptId)) return "vehicle_plate_taken"
        val lender = vehicle.hiredFromSupplierId
        if (lender != null && suppliers.findById(lender) == null) return "supplier_not_found"
        return null
    }
}
