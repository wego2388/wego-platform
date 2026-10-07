package com.wego.toursoperator.infrastructure

import com.wego.generated.jooq.tables.IdentityUser.IDENTITY_USER
import com.wego.generated.jooq.tables.ToursOperatorAssignmentAudit.TOURS_OPERATOR_ASSIGNMENT_AUDIT
import com.wego.generated.jooq.tables.ToursOperatorDriver.TOURS_OPERATOR_DRIVER
import com.wego.generated.jooq.tables.ToursOperatorSlotAssignment.TOURS_OPERATOR_SLOT_ASSIGNMENT
import com.wego.generated.jooq.tables.ToursOperatorSlotAssignmentSupplier.TOURS_OPERATOR_SLOT_ASSIGNMENT_SUPPLIER
import com.wego.generated.jooq.tables.ToursOperatorSupplier.TOURS_OPERATOR_SUPPLIER
import com.wego.generated.jooq.tables.ToursOperatorSupplierTour.TOURS_OPERATOR_SUPPLIER_TOUR
import com.wego.generated.jooq.tables.ToursOperatorTourSlot.TOURS_OPERATOR_TOUR_SLOT
import com.wego.generated.jooq.tables.ToursOperatorVehicle.TOURS_OPERATOR_VEHICLE
import com.wego.generated.jooq.tables.records.ToursOperatorDriverRecord
import com.wego.generated.jooq.tables.records.ToursOperatorSupplierRecord
import com.wego.generated.jooq.tables.records.ToursOperatorVehicleRecord
import com.wego.toursoperator.application.AssignmentAction
import com.wego.toursoperator.application.AssignmentAuditEntry
import com.wego.toursoperator.application.DriverRepository
import com.wego.toursoperator.application.ResourceUse
import com.wego.toursoperator.application.SlotAssignmentRepository
import com.wego.toursoperator.application.SupplierRepository
import com.wego.toursoperator.application.VehicleRepository
import com.wego.toursoperator.domain.ConfirmationChannel
import com.wego.toursoperator.domain.ConflictKind
import com.wego.toursoperator.domain.Driver
import com.wego.toursoperator.domain.DriverEngagement
import com.wego.toursoperator.domain.PaidCurrency
import com.wego.toursoperator.domain.RegistryAudit
import com.wego.toursoperator.domain.SettlementCadence
import com.wego.toursoperator.domain.SlotAssignment
import com.wego.toursoperator.domain.Supplier
import com.wego.toursoperator.domain.SupplierPaymentMethod
import com.wego.toursoperator.domain.SupplierPricingBasis
import com.wego.toursoperator.domain.SupplierServiceType
import com.wego.toursoperator.domain.TimeSlot
import com.wego.toursoperator.domain.TourSlotId
import com.wego.toursoperator.domain.Vehicle
import com.wego.toursoperator.domain.VehicleOwnership
import com.wego.toursoperator.domain.VehicleType
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

private fun Instant.utc(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)

@Repository("stoSupplierRepositoryImpl")
class JooqSupplierRepository(
    private val dsl: DSLContext,
) : SupplierRepository {
    private val t = TOURS_OPERATOR_SUPPLIER
    private val link = TOURS_OPERATOR_SUPPLIER_TOUR

    @Transactional(readOnly = true)
    override fun findById(id: UUID): Supplier? = findByIds(listOf(id)).firstOrNull()

    @Transactional(readOnly = true)
    override fun findByIds(ids: Collection<UUID>): List<Supplier> = if (ids.isEmpty()) emptyList() else load(t.ID.`in`(ids))

    @Transactional(readOnly = true)
    override fun findAll(active: Boolean?): List<Supplier> =
        load(
            active?.let { t.ACTIVE.eq(it) } ?: org.jooq.impl.DSL
                .noCondition(),
        )

    private fun load(condition: org.jooq.Condition): List<Supplier> {
        val records =
            dsl
                .selectFrom(t)
                .where(condition)
                .orderBy(t.CODE.asc())
                .fetch()
        if (records.isEmpty()) return emptyList()
        val tours =
            dsl
                .select(link.SUPPLIER_ID, link.TOUR_ID)
                .from(link)
                .where(link.SUPPLIER_ID.`in`(records.map { it.id }))
                .fetch()
                .groupBy({ it[link.SUPPLIER_ID]!! }, { it[link.TOUR_ID]!! })
        return records.map { toDomain(it, tours[it.id].orEmpty().toSet()) }
    }

    @Transactional(readOnly = true)
    override fun codeTaken(
        code: String,
        exceptId: UUID?,
    ): Boolean =
        dsl.fetchExists(
            dsl.selectFrom(t).where(t.CODE.eq(code)).and(
                exceptId?.let { t.ID.ne(it) } ?: org.jooq.impl.DSL
                    .noCondition(),
            ),
        )

    @Transactional
    override fun insert(supplier: Supplier) {
        dsl
            .insertInto(t)
            .set(t.ID, supplier.id)
            .set(t.CODE, supplier.code)
            .set(t.NAME, supplier.name)
            .set(t.SERVICE_TYPE, supplier.serviceType.name)
            .set(t.CONTACT_PERSON, supplier.contactPerson)
            .set(t.BUSINESS_PHONE, supplier.businessPhone)
            .set(t.CONFIRMATION_CHANNEL, supplier.confirmationChannel?.name)
            .set(t.NOTICE_HOURS, supplier.noticeHours)
            .set(t.PRICING_BASIS, supplier.pricingBasis?.name)
            .set(t.CURRENCY, supplier.currency?.name)
            .set(t.SETTLEMENT_CADENCE, supplier.settlementCadence?.name)
            .set(t.PAYMENT_METHOD, supplier.paymentMethod?.name)
            .set(t.CANCELLATION_TERMS, supplier.cancellationTerms)
            .set(t.ACTIVE, supplier.active)
            .set(t.REVISION, supplier.audit.revision)
            .set(t.CREATED_AT, supplier.audit.createdAt.utc())
            .set(t.CREATED_BY_USER_ID, supplier.audit.createdByUserId)
            .set(t.UPDATED_AT, supplier.audit.updatedAt.utc())
            .set(t.UPDATED_BY_USER_ID, supplier.audit.updatedByUserId)
            .execute()
        replaceTours(supplier)
    }

    @Transactional
    override fun update(
        supplier: Supplier,
        expectedRevision: Int,
    ): Boolean {
        val rows =
            dsl
                .update(t)
                .set(t.CODE, supplier.code)
                .set(t.NAME, supplier.name)
                .set(t.SERVICE_TYPE, supplier.serviceType.name)
                .set(t.CONTACT_PERSON, supplier.contactPerson)
                .set(t.BUSINESS_PHONE, supplier.businessPhone)
                .set(t.CONFIRMATION_CHANNEL, supplier.confirmationChannel?.name)
                .set(t.NOTICE_HOURS, supplier.noticeHours)
                .set(t.PRICING_BASIS, supplier.pricingBasis?.name)
                .set(t.CURRENCY, supplier.currency?.name)
                .set(t.SETTLEMENT_CADENCE, supplier.settlementCadence?.name)
                .set(t.PAYMENT_METHOD, supplier.paymentMethod?.name)
                .set(t.CANCELLATION_TERMS, supplier.cancellationTerms)
                .set(t.ACTIVE, supplier.active)
                .set(t.REVISION, supplier.audit.revision)
                .set(t.UPDATED_AT, supplier.audit.updatedAt.utc())
                .set(t.UPDATED_BY_USER_ID, supplier.audit.updatedByUserId)
                .where(t.ID.eq(supplier.id))
                .and(t.REVISION.eq(expectedRevision))
                .execute()
        if (rows == 0) return false
        replaceTours(supplier)
        return true
    }

    private fun replaceTours(supplier: Supplier) {
        dsl.deleteFrom(link).where(link.SUPPLIER_ID.eq(supplier.id)).execute()
        for (tourId in supplier.tourIds) {
            dsl
                .insertInto(link)
                .set(link.SUPPLIER_ID, supplier.id)
                .set(link.TOUR_ID, tourId)
                .execute()
        }
    }

    private fun toDomain(
        r: ToursOperatorSupplierRecord,
        tourIds: Set<UUID>,
    ) = Supplier(
        id = r.id,
        code = r.code,
        name = r.name,
        serviceType = SupplierServiceType.valueOf(r.serviceType),
        contactPerson = r.contactPerson,
        businessPhone = r.businessPhone,
        confirmationChannel = r.confirmationChannel?.let(ConfirmationChannel::valueOf),
        noticeHours = r.noticeHours,
        pricingBasis = r.pricingBasis?.let(SupplierPricingBasis::valueOf),
        currency = r.currency?.let(PaidCurrency::valueOf),
        settlementCadence = r.settlementCadence?.let(SettlementCadence::valueOf),
        paymentMethod = r.paymentMethod?.let(SupplierPaymentMethod::valueOf),
        cancellationTerms = r.cancellationTerms,
        active = r.active,
        tourIds = tourIds,
        audit = RegistryAudit(r.revision, r.createdAt.toInstant(), r.createdByUserId, r.updatedAt.toInstant(), r.updatedByUserId),
    )
}

@Repository("stoDriverRepositoryImpl")
class JooqDriverRepository(
    private val dsl: DSLContext,
) : DriverRepository {
    private val t = TOURS_OPERATOR_DRIVER

    @Transactional(readOnly = true)
    override fun findById(id: UUID): Driver? =
        dsl
            .selectFrom(t)
            .where(t.ID.eq(id))
            .fetchOne()
            ?.let(::toDomain)

    @Transactional(readOnly = true)
    override fun findAll(active: Boolean?): List<Driver> =
        dsl
            .selectFrom(t)
            .where(
                active?.let { t.ACTIVE.eq(it) } ?: org.jooq.impl.DSL
                    .noCondition(),
            ).orderBy(t.NAME.asc(), t.ID.asc())
            .fetch()
            .map(::toDomain)

    @Transactional
    override fun insert(driver: Driver) {
        dsl
            .insertInto(t)
            .set(t.ID, driver.id)
            .set(t.NAME, driver.name)
            .set(t.WORK_PHONE, driver.workPhone)
            .set(t.ENGAGEMENT_TYPE, driver.engagement.name)
            .set(t.LICENCE_VALID_UNTIL, driver.licenceValidUntil)
            .set(t.ACTIVE, driver.active)
            .set(t.REVISION, driver.audit.revision)
            .set(t.CREATED_AT, driver.audit.createdAt.utc())
            .set(t.CREATED_BY_USER_ID, driver.audit.createdByUserId)
            .set(t.UPDATED_AT, driver.audit.updatedAt.utc())
            .set(t.UPDATED_BY_USER_ID, driver.audit.updatedByUserId)
            .execute()
    }

    @Transactional
    override fun update(
        driver: Driver,
        expectedRevision: Int,
    ): Boolean =
        dsl
            .update(t)
            .set(t.NAME, driver.name)
            .set(t.WORK_PHONE, driver.workPhone)
            .set(t.ENGAGEMENT_TYPE, driver.engagement.name)
            .set(t.LICENCE_VALID_UNTIL, driver.licenceValidUntil)
            .set(t.ACTIVE, driver.active)
            .set(t.REVISION, driver.audit.revision)
            .set(t.UPDATED_AT, driver.audit.updatedAt.utc())
            .set(t.UPDATED_BY_USER_ID, driver.audit.updatedByUserId)
            .where(t.ID.eq(driver.id))
            .and(t.REVISION.eq(expectedRevision))
            .execute() == 1

    private fun toDomain(r: ToursOperatorDriverRecord) =
        Driver(
            id = r.id,
            name = r.name,
            workPhone = r.workPhone,
            engagement = DriverEngagement.valueOf(r.engagementType),
            licenceValidUntil = r.licenceValidUntil,
            active = r.active,
            audit = RegistryAudit(r.revision, r.createdAt.toInstant(), r.createdByUserId, r.updatedAt.toInstant(), r.updatedByUserId),
        )
}

@Repository("stoVehicleRepositoryImpl")
class JooqVehicleRepository(
    private val dsl: DSLContext,
) : VehicleRepository {
    private val t = TOURS_OPERATOR_VEHICLE

    @Transactional(readOnly = true)
    override fun findById(id: UUID): Vehicle? =
        dsl
            .selectFrom(t)
            .where(t.ID.eq(id))
            .fetchOne()
            ?.let(::toDomain)

    @Transactional(readOnly = true)
    override fun findAll(active: Boolean?): List<Vehicle> =
        dsl
            .selectFrom(t)
            .where(
                active?.let { t.ACTIVE.eq(it) } ?: org.jooq.impl.DSL
                    .noCondition(),
            ).orderBy(t.LABEL.asc().nullsLast(), t.PLATE.asc().nullsLast(), t.ID.asc())
            .fetch()
            .map(::toDomain)

    @Transactional(readOnly = true)
    override fun plateTaken(
        plate: String,
        exceptId: UUID?,
    ): Boolean =
        dsl.fetchExists(
            dsl
                .selectFrom(t)
                .where(
                    org.jooq.impl.DSL
                        .upper(t.PLATE)
                        .eq(plate.uppercase()),
                ).and(
                    exceptId?.let { t.ID.ne(it) } ?: org.jooq.impl.DSL
                        .noCondition(),
                ),
        )

    @Transactional
    override fun insert(vehicle: Vehicle) {
        dsl
            .insertInto(t)
            .set(t.ID, vehicle.id)
            .set(t.LABEL, vehicle.label)
            .set(t.PLATE, vehicle.plate)
            .set(t.VEHICLE_TYPE, vehicle.type.name)
            .set(t.SEATS, vehicle.seats)
            .set(t.OWNERSHIP, vehicle.ownership.name)
            .set(t.HIRED_FROM_SUPPLIER_ID, vehicle.hiredFromSupplierId)
            .set(t.ACTIVE, vehicle.active)
            .set(t.REVISION, vehicle.audit.revision)
            .set(t.CREATED_AT, vehicle.audit.createdAt.utc())
            .set(t.CREATED_BY_USER_ID, vehicle.audit.createdByUserId)
            .set(t.UPDATED_AT, vehicle.audit.updatedAt.utc())
            .set(t.UPDATED_BY_USER_ID, vehicle.audit.updatedByUserId)
            .execute()
    }

    @Transactional
    override fun update(
        vehicle: Vehicle,
        expectedRevision: Int,
    ): Boolean =
        dsl
            .update(t)
            .set(t.LABEL, vehicle.label)
            .set(t.PLATE, vehicle.plate)
            .set(t.VEHICLE_TYPE, vehicle.type.name)
            .set(t.SEATS, vehicle.seats)
            .set(t.OWNERSHIP, vehicle.ownership.name)
            .set(t.HIRED_FROM_SUPPLIER_ID, vehicle.hiredFromSupplierId)
            .set(t.ACTIVE, vehicle.active)
            .set(t.REVISION, vehicle.audit.revision)
            .set(t.UPDATED_AT, vehicle.audit.updatedAt.utc())
            .set(t.UPDATED_BY_USER_ID, vehicle.audit.updatedByUserId)
            .where(t.ID.eq(vehicle.id))
            .and(t.REVISION.eq(expectedRevision))
            .execute() == 1

    private fun toDomain(r: ToursOperatorVehicleRecord) =
        Vehicle(
            id = r.id,
            label = r.label,
            plate = r.plate,
            type = VehicleType.valueOf(r.vehicleType),
            seats = r.seats,
            ownership = VehicleOwnership.valueOf(r.ownership),
            hiredFromSupplierId = r.hiredFromSupplierId,
            active = r.active,
            audit = RegistryAudit(r.revision, r.createdAt.toInstant(), r.createdByUserId, r.updatedAt.toInstant(), r.updatedByUserId),
        )
}

@Repository("stoSlotAssignmentRepositoryImpl")
class JooqSlotAssignmentRepository(
    private val dsl: DSLContext,
) : SlotAssignmentRepository {
    private val t = TOURS_OPERATOR_SLOT_ASSIGNMENT
    private val sup = TOURS_OPERATOR_SLOT_ASSIGNMENT_SUPPLIER
    private val audit = TOURS_OPERATOR_ASSIGNMENT_AUDIT

    @Transactional
    override fun lockResources(
        driverId: UUID?,
        vehicleId: UUID?,
    ) {
        // Fixed order (sorted keys) so two requests can never wait on each other.
        val keys =
            listOfNotNull(
                driverId?.let { "tours-operator-assign:driver:$it" },
                vehicleId?.let { "tours-operator-assign:vehicle:$it" },
            ).sorted()
        for (key in keys) dsl.execute("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", key)
    }

    @Transactional(readOnly = true)
    override fun findBySlot(slotId: TourSlotId): SlotAssignment? = load(t.SLOT_ID.eq(slotId.value)).firstOrNull()

    @Transactional(readOnly = true)
    override fun findByDate(date: LocalDate): List<SlotAssignment> = load(t.SERVICE_DATE.eq(date))

    private fun load(condition: org.jooq.Condition): List<SlotAssignment> {
        val rows = dsl.selectFrom(t).where(condition).fetch()
        if (rows.isEmpty()) return emptyList()
        val suppliers =
            dsl
                .select(sup.SLOT_ID, sup.SUPPLIER_ID)
                .from(sup)
                .where(sup.SLOT_ID.`in`(rows.map { it.slotId }))
                .fetch()
                .groupBy({ it[sup.SLOT_ID]!! }, { it[sup.SUPPLIER_ID]!! })
        return rows.map {
            SlotAssignment(
                slotId = TourSlotId(it.slotId),
                date = it.serviceDate,
                timeSlot = TimeSlot.valueOf(it.timeSlot),
                driverId = it.driverId,
                vehicleId = it.vehicleId,
                supplierIds = suppliers[it.slotId].orEmpty().toSet(),
                revision = it.revision,
                assignedAt = it.assignedAt.toInstant(),
                assignedByUserId = it.assignedByUserId,
                updatedAt = it.updatedAt.toInstant(),
                updatedByUserId = it.updatedByUserId,
            )
        }
    }

    @Transactional
    override fun insert(assignment: SlotAssignment) {
        dsl
            .insertInto(t)
            .set(t.SLOT_ID, assignment.slotId.value)
            .set(t.SERVICE_DATE, assignment.date)
            .set(t.TIME_SLOT, assignment.timeSlot.name)
            .set(t.DRIVER_ID, assignment.driverId)
            .set(t.VEHICLE_ID, assignment.vehicleId)
            .set(t.REVISION, assignment.revision)
            .set(t.ASSIGNED_AT, assignment.assignedAt.utc())
            .set(t.ASSIGNED_BY_USER_ID, assignment.assignedByUserId)
            .set(t.UPDATED_AT, assignment.updatedAt.utc())
            .set(t.UPDATED_BY_USER_ID, assignment.updatedByUserId)
            .execute()
        replaceSuppliers(assignment)
    }

    @Transactional
    override fun update(
        assignment: SlotAssignment,
        expectedRevision: Int,
    ): Boolean {
        val rows =
            dsl
                .update(t)
                .set(t.DRIVER_ID, assignment.driverId)
                .set(t.VEHICLE_ID, assignment.vehicleId)
                .set(t.REVISION, assignment.revision)
                .set(t.UPDATED_AT, assignment.updatedAt.utc())
                .set(t.UPDATED_BY_USER_ID, assignment.updatedByUserId)
                .where(t.SLOT_ID.eq(assignment.slotId.value))
                .and(t.REVISION.eq(expectedRevision))
                .execute()
        if (rows == 0) return false
        replaceSuppliers(assignment)
        return true
    }

    private fun replaceSuppliers(assignment: SlotAssignment) {
        dsl.deleteFrom(sup).where(sup.SLOT_ID.eq(assignment.slotId.value)).execute()
        for (id in assignment.supplierIds) {
            dsl
                .insertInto(sup)
                .set(sup.SLOT_ID, assignment.slotId.value)
                .set(sup.SUPPLIER_ID, id)
                .execute()
        }
    }

    @Transactional
    override fun delete(slotId: TourSlotId) {
        dsl.deleteFrom(t).where(t.SLOT_ID.eq(slotId.value)).execute()
    }

    @Transactional(readOnly = true)
    override fun usage(
        kind: ConflictKind,
        resourceId: UUID,
        date: LocalDate,
        excludeSlotId: UUID,
    ): List<ResourceUse> {
        val column = if (kind == ConflictKind.DRIVER) t.DRIVER_ID else t.VEHICLE_ID
        return dsl
            .select(t.SLOT_ID, TOURS_OPERATOR_TOUR_SLOT.TOUR_ID, t.TIME_SLOT)
            .from(t)
            .join(TOURS_OPERATOR_TOUR_SLOT)
            .on(TOURS_OPERATOR_TOUR_SLOT.ID.eq(t.SLOT_ID))
            .where(column.eq(resourceId))
            .and(t.SERVICE_DATE.eq(date))
            .and(t.SLOT_ID.ne(excludeSlotId))
            .fetch { ResourceUse(it[t.SLOT_ID]!!, it[TOURS_OPERATOR_TOUR_SLOT.TOUR_ID]!!, TimeSlot.valueOf(it[t.TIME_SLOT]!!)) }
    }

    @Transactional
    override fun appendAudit(entry: AssignmentAuditEntry) {
        dsl
            .insertInto(audit)
            .set(audit.ID, entry.id)
            .set(audit.SLOT_ID, entry.slotId)
            .set(audit.SERVICE_DATE, entry.date)
            .set(audit.TIME_SLOT, entry.timeSlot.name)
            .set(audit.ACTION, entry.action.name)
            .set(audit.REVISION, entry.revision)
            .set(audit.DRIVER_ID, entry.driverId)
            .set(audit.VEHICLE_ID, entry.vehicleId)
            .set(
                audit.SUPPLIER_IDS,
                entry.supplierIds
                    .map { it.toString() }
                    .sorted()
                    .joinToString(","),
            ).set(audit.ACTOR_USER_ID, entry.actorUserId)
            .set(audit.OCCURRED_AT, entry.occurredAt.utc())
            .execute()
    }

    @Transactional(readOnly = true)
    override fun history(slotId: TourSlotId): List<AssignmentAuditEntry> =
        dsl
            .select(audit.asterisk(), IDENTITY_USER.EMAIL)
            .from(audit)
            .leftJoin(IDENTITY_USER)
            .on(IDENTITY_USER.ID.eq(audit.ACTOR_USER_ID))
            .where(audit.SLOT_ID.eq(slotId.value))
            .orderBy(audit.OCCURRED_AT.asc(), audit.ID.asc())
            .fetch { r ->
                AssignmentAuditEntry(
                    id = r[audit.ID]!!,
                    slotId = r[audit.SLOT_ID]!!,
                    date = r[audit.SERVICE_DATE]!!,
                    timeSlot = TimeSlot.valueOf(r[audit.TIME_SLOT]!!),
                    action = AssignmentAction.valueOf(r[audit.ACTION]!!),
                    revision = r[audit.REVISION]!!,
                    driverId = r[audit.DRIVER_ID],
                    vehicleId = r[audit.VEHICLE_ID],
                    supplierIds =
                        r[audit.SUPPLIER_IDS]!!
                            .split(',')
                            .filter { it.isNotEmpty() }
                            .map(UUID::fromString)
                            .toSet(),
                    actorUserId = r[audit.ACTOR_USER_ID],
                    actorEmail = r[IDENTITY_USER.EMAIL],
                    occurredAt = r[audit.OCCURRED_AT]!!.toInstant(),
                )
            }

    @Transactional(readOnly = true)
    override fun emailOf(userId: UUID): String? =
        dsl
            .select(IDENTITY_USER.EMAIL)
            .from(IDENTITY_USER)
            .where(IDENTITY_USER.ID.eq(userId))
            .fetchOne(0, String::class.java)
}
