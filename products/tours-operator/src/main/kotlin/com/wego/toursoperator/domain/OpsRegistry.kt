package com.wego.toursoperator.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * WEGO-016-OPS2-E registries: suppliers, drivers and vehicles.
 *
 * Nothing is ever deleted: a record that was used becomes inactive so that
 * history (assignments, later costs) stays readable. Every record carries a
 * [revision] that a writer echoes back (optimistic locking). Agreed supplier
 * prices are deliberately absent: they belong to OPS2-F.
 */
object OpsRules {
    /** Egyptian and international work phones: optional +, digits, spaces, hyphens, brackets; 7–24 characters. */
    private val PHONE = Regex("^\\+?[0-9][0-9 ()-]{5,22}[0-9]$")
    val CODE = Regex("^[A-Z][A-Z0-9-]{1,23}$")

    fun requirePhone(
        phone: String?,
        field: String,
    ) {
        require(phone == null || PHONE.matches(phone)) { "$field must be a phone number (digits, optional +)" }
    }

    fun requireText(
        value: String?,
        field: String,
        max: Int,
    ) {
        require(value == null || (value == value.trim() && value.isNotEmpty() && value.length <= max && value.none { it.isISOControl() })) {
            "$field must be 1–$max characters without leading/trailing spaces or control characters"
        }
    }

    /** Blank input is "not provided"; everything else is trimmed. */
    fun clean(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
}

enum class SupplierServiceType { DIVING_SNORKELING, QUAD_BUGGY_SAFARI, BOAT, TRANSPORT, ATTRACTION, OTHER }

enum class ConfirmationChannel { WHATSAPP, PHONE, EMAIL, OTHER }

enum class SupplierPricingBasis { PER_PERSON, PER_UNIT, PER_TRIP, PERCENT_OF_SALE, OTHER }

enum class SettlementCadence { AFTER_EACH_TRIP, WEEKLY, MONTHLY, OTHER }

enum class SupplierPaymentMethod { CASH, INSTAPAY, MOBILE_WALLET, BANK_TRANSFER, OTHER }

enum class DriverEngagement { PER_TRIP, MONTHLY, DAILY, OTHER }

enum class VehicleType { SEDAN, SUV, JEEP, VAN, MINIBUS, BUS, OTHER }

enum class VehicleOwnership { OWNED, HIRED }

/** Fields every registry row shares: optimistic-lock revision and who/when. */
data class RegistryAudit(
    val revision: Int,
    val createdAt: Instant,
    val createdByUserId: UUID?,
    val updatedAt: Instant,
    val updatedByUserId: UUID?,
)

class Supplier(
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
    /** Tours this supplier serves (many-to-many); assignment suggests suppliers by tour. */
    val tourIds: Set<UUID>,
    val audit: RegistryAudit,
) {
    init {
        require(OpsRules.CODE.matches(code)) { "code must be 2–24 characters: capital letters, digits and hyphen, starting with a letter" }
        OpsRules.requireText(name, "name", 120)
        OpsRules.requireText(contactPerson, "contactPerson", 120)
        OpsRules.requirePhone(businessPhone, "businessPhone")
        require(noticeHours == null || noticeHours in 0..720) { "noticeHours must be between 0 and 720" }
        require(cancellationTerms == null || (cancellationTerms.isNotBlank() && cancellationTerms.length <= 1000)) {
            "cancellationTerms must be 1–1000 characters"
        }
    }
}

class Driver(
    val id: UUID,
    val name: String,
    val workPhone: String?,
    val engagement: DriverEngagement,
    val licenceValidUntil: LocalDate,
    val active: Boolean,
    val audit: RegistryAudit,
) {
    init {
        OpsRules.requireText(name, "name", 120)
        OpsRules.requirePhone(workPhone, "workPhone")
    }

    /** The licence covers the day of service (the last valid day counts). */
    fun licenceCovers(day: LocalDate): Boolean = !licenceValidUntil.isBefore(day)
}

class Vehicle(
    val id: UUID,
    val label: String?,
    val plate: String?,
    val type: VehicleType,
    val seats: Int,
    val ownership: VehicleOwnership,
    val hiredFromSupplierId: UUID?,
    val active: Boolean,
    val audit: RegistryAudit,
) {
    init {
        require(label != null || plate != null) { "a vehicle needs a label or a plate" }
        OpsRules.requireText(label, "label", 80)
        OpsRules.requireText(plate, "plate", 24)
        require(seats in 1..80) { "seats must be between 1 and 80" }
        require(hiredFromSupplierId == null || ownership == VehicleOwnership.HIRED) { "only a hired vehicle has a lender" }
    }

    /** What staff see in lists and on documents: label first, plate in brackets when both exist. */
    val display: String get() = listOfNotNull(label, plate?.let { if (label != null) "($it)" else it }).joinToString(" ")
}

/** The driver / vehicle / suppliers of one departure (tour slot). [revision] is the optimistic lock. */
data class SlotAssignment(
    val slotId: TourSlotId,
    val date: LocalDate,
    val timeSlot: TimeSlot,
    val driverId: UUID?,
    val vehicleId: UUID?,
    val supplierIds: Set<UUID>,
    val revision: Int,
    val assignedAt: Instant,
    val assignedByUserId: UUID?,
    val updatedAt: Instant,
    val updatedByUserId: UUID?,
    /**
     * Staff-written note printed on this departure's supplier orders. Customers' own special
     * requests are never forwarded to suppliers; staff copy only what the supplier needs.
     */
    val supplierNote: String? = null,
) {
    init {
        require(
            driverId != null || vehicleId != null || supplierIds.isNotEmpty(),
        ) { "an assignment needs a driver, a vehicle or a supplier" }
        OpsRules.requireText(supplierNote, "supplierNote", SUPPLIER_NOTE_MAX)
    }

    companion object {
        const val SUPPLIER_NOTE_MAX = 500
    }
}

enum class ConflictKind { DRIVER, VEHICLE }

/** The same driver or vehicle is already on another departure whose window overlaps. */
data class AssignmentConflict(
    val kind: ConflictKind,
    val resourceId: UUID,
    val otherSlotId: UUID,
    val otherTourId: UUID,
    val otherTimeSlot: TimeSlot,
)

/** A refusal ([blocking] = true, nothing is saved) or a heads-up that does not stop the save. */
data class AssignmentIssue(
    val code: String,
    val resourceId: UUID? = null,
    val value: Int? = null,
    val limit: Int? = null,
    val blocking: Boolean = false,
)

/**
 * Time-slot overlap rules. Departure clock times are not stored (owner has not
 * supplied them), so a departure is known only by its window.
 *
 *  - **Same window, same day = overlap = blocking.** Two departures in the same
 *    window run at the same time, so the same driver or vehicle cannot be on both.
 *  - **Adjacent windows (SUNRISE→MORNING→AFTERNOON→SUNSET) = back-to-back =
 *    warning only.** Whether a trip really finishes before the next starts depends
 *    on the tour's length, which is not modelled; staff decide.
 *  - Windows further apart never conflict. A different day never conflicts.
 */
object TimeSlotOverlap {
    fun overlaps(
        a: TimeSlot,
        b: TimeSlot,
    ): Boolean = a == b

    fun backToBack(
        a: TimeSlot,
        b: TimeSlot,
    ): Boolean = kotlin.math.abs(a.ordinal - b.ordinal) == 1
}

object AssignmentRules {
    /** A licence ending within this many days of the service day is flagged (warning). */
    const val LICENCE_SOON_DAYS = 30L

    /**
     * Everything a chosen driver / vehicle / supplier set means for a departure.
     * Blocking issues refuse the save; non-blocking ones are shown beside it.
     * [guests] is the slot's booked guest count (adults + children).
     */
    fun evaluate(
        date: LocalDate,
        guests: Int,
        tourId: UUID,
        driver: Driver?,
        vehicle: Vehicle?,
        suppliers: List<Supplier>,
    ): List<AssignmentIssue> {
        val issues = mutableListOf<AssignmentIssue>()
        if (driver != null) {
            if (!driver.active) issues += AssignmentIssue("driver_inactive", driver.id, blocking = true)
            if (!driver.licenceCovers(date)) {
                issues += AssignmentIssue("driver_licence_expired", driver.id, blocking = true)
            } else if (driver.licenceValidUntil.isBefore(date.plusDays(LICENCE_SOON_DAYS))) {
                issues += AssignmentIssue("driver_licence_expires_soon", driver.id)
            }
        }
        if (vehicle != null) {
            if (!vehicle.active) issues += AssignmentIssue("vehicle_inactive", vehicle.id, blocking = true)
            // Warning, not a block: guest counts still move after assignment, and a second vehicle may be arranged.
            if (vehicle.seats < guests) issues += AssignmentIssue("vehicle_seats_below_guests", vehicle.id, guests, vehicle.seats)
        }
        for (supplier in suppliers) {
            if (!supplier.active) issues += AssignmentIssue("supplier_inactive", supplier.id, blocking = true)
            if (tourId !in supplier.tourIds) issues += AssignmentIssue("supplier_not_linked_to_tour", supplier.id)
        }
        return issues
    }
}
